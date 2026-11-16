package com.sashacraft.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.content.SharedPreferences;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

public class GameView extends GLSurfaceView {
    private final WorldRenderer renderer;
    private float downX, downY, lastX, lastY;
    private boolean moving;
    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new WorldRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=lastX=x; downY=lastY=y; moving=x<getWidth()*0.36f && y>getHeight()*0.52f;
            if(!moving && x>getWidth()*0.76f && y>getHeight()*0.70f) renderer.breakBlock();
            else if(!moving && x>getWidth()*0.76f && y>getHeight()*0.48f && y<=getHeight()*0.70f) renderer.placeBlock();
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE) {
            float dx=x-lastX, dy=y-lastY;
            if(moving) renderer.move(dx/(getWidth()*0.20f),dy/(getHeight()*0.20f));
            else if(downX>getWidth()*0.36f || downY<getHeight()*0.52f) renderer.look(dx*0.0045f,dy*0.0045f);
            lastX=x; lastY=y; return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP) { moving=false; return true; }
        return true;
    }

    static class WorldRenderer implements Renderer {
        private final Context context;
        private final int[][][] blocks=new int[32][24][32];
        private FloatBuffer vertices;
        private int vertexCount, program, posHandle, colorHandle, mvpHandle;
        private final float[] projection=new float[16], view=new float[16], mvp=new float[16], model=new float[16];
        private float px=16f, py=12f, pz=29f, yaw=0f, pitch=-0.20f;
        private int selected=1;
        private final SharedPreferences prefs;
        WorldRenderer(Context c) { context=c; prefs=c.getSharedPreferences("world",Context.MODE_PRIVATE); loadOrGenerate(); }
        private void loadOrGenerate() {
            String saved=prefs.getString("blocks","");
            if(saved.length()==blocks.length*blocks[0].length*blocks[0][0].length) {
                int k=0; for(int x=0;x<32;x++) for(int y=0;y<24;y++) for(int z=0;z<32;z++) blocks[x][y][z]=saved.charAt(k++)-'0';
            } else generateWorld();
            rebuild();
        }
        private void generateWorld() {
            Random r=new Random(2360);
            for(int x=0;x<32;x++) for(int z=0;z<32;z++) {
                int h=4+(int)(2*Math.sin(x*.32)+2*Math.cos(z*.27)+r.nextInt(3));
                h=Math.max(2,Math.min(10,h));
                for(int y=0;y<=h;y++) blocks[x][y][z]=y==h?1:(y>h-3?2:3);
            }
            for(int x=3;x<29;x+=5) for(int z=3;z<29;z+=6) {
                int h=top(x,z)+1;
                for(int y=0;y<4;y++) if(h+y<23) blocks[x][h+y][z]=4;
                for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) for(int dy=2;dy<=5;dy++) {
                    int xx=x+dx,zz=z+dz,yy=h+dy;
                    if(xx>=0&&xx<32&&zz>=0&&zz<32&&yy<24&&Math.abs(dx)+Math.abs(dz)+Math.max(0,dy-3)<6&&blocks[xx][yy][zz]==0) blocks[xx][yy][zz]=5;
                }
            }
        }
        private int top(int x,int z) { for(int y=23;y>=0;y--) if(blocks[x][y][z]!=0)return y; return 0; }
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg) {
            GLES20.glClearColor(.48f,.73f,.91f,1); GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            String vs="attribute vec3 aPos; attribute vec3 aColor; uniform mat4 uMVP; varying vec3 vColor; void main(){gl_Position=uMVP*vec4(aPos,1.0);vColor=aColor;}";
            String fs="precision mediump float; varying vec3 vColor; void main(){gl_FragColor=vec4(vColor,1.0);}";
            int v=shader(GLES20.GL_VERTEX_SHADER,vs), f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,v); GLES20.glAttachShader(program,f); GLES20.glLinkProgram(program);
            posHandle=GLES20.glGetAttribLocation(program,"aPos"); colorHandle=GLES20.glGetAttribLocation(program,"aColor"); mvpHandle=GLES20.glGetUniformLocation(program,"uMVP");
            rebuild();
        }
        private int shader(int type,String src) { int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s); return s; }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h) { GLES20.glViewport(0,0,w,h); Matrix.perspectiveM(projection,0,68f,(float)w/Math.max(1,h),.1f,100f); }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float cy=(float)Math.cos(yaw), sy=(float)Math.sin(yaw), cp=(float)Math.cos(pitch), sp=(float)Math.sin(pitch);
            float lx=sy*cp, ly=sp, lz=-cy*cp;
            Matrix.setLookAtM(view,0,px,py,pz,px+lx,py+ly,pz+lz,0,1,0);
            Matrix.multiplyMM(mvp,0,projection,0,view,0);
            GLES20.glUseProgram(program);
            if(vertices!=null) {
                vertices.position(0); GLES20.glEnableVertexAttribArray(posHandle); GLES20.glEnableVertexAttribArray(colorHandle);
                GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,24,vertices);
                vertices.position(3); GLES20.glVertexAttribPointer(colorHandle,3,GLES20.GL_FLOAT,false,24,vertices);
                GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertexCount);
                GLES20.glDisableVertexAttribArray(posHandle); GLES20.glDisableVertexAttribArray(colorHandle);
            }
        }
        void look(float dx,float dy) { yaw+=dx; pitch=Math.max(-1.25f,Math.min(1.25f,pitch+dy)); }
        void move(float x,float y) {
            float speed=.11f, fx=(float)Math.sin(yaw), fz=-(float)Math.cos(yaw);
            float nx=px+x*speed*(float)Math.cos(yaw)-y*speed*fx;
            float nz=pz+x*speed*(float)Math.sin(yaw)-y*speed*fz;
            if(!solid((int)Math.floor(nx),(int)Math.floor(py-1),(int)Math.floor(pz))) px=Math.max(.5f,Math.min(31.5f,nx));
            if(!solid((int)Math.floor(px),(int)Math.floor(py-1),(int)Math.floor(nz))) pz=Math.max(.5f,Math.min(31.5f,nz));
            int ground=top(Math.max(0,Math.min(31,(int)px)),Math.max(0,Math.min(31,(int)pz)));
            py=Math.max(ground+2.0f,Math.min(21.5f,py));
        }
        private boolean solid(int x,int y,int z) { return x>=0&&x<32&&y>=0&&y<24&&z>=0&&z<32&&blocks[x][y][z]!=0; }
        private int[] target(boolean place) {
            float dx=(float)Math.sin(yaw)*(float)Math.cos(pitch), dy=(float)Math.sin(pitch), dz=-(float)Math.cos(yaw)*(float)Math.cos(pitch);
            int[] prev=null;
            for(float d=.5f;d<6;d+=.12f) {
                int x=(int)Math.floor(px+dx*d), y=(int)Math.floor(py+dy*d), z=(int)Math.floor(pz+dz*d);
                if(x<0||x>=32||y<0||y>=24||z<0||z>=32) continue;
                if(blocks[x][y][z]!=0) return place?prev:new int[]{x,y,z};
                prev=new int[]{x,y,z};
            }
            return null;
        }
        void breakBlock() { int[] t=target(false); if(t!=null){blocks[t[0]][t[1]][t[2]]=0; rebuild(); save();} }
        void placeBlock() { int[] t=target(true); if(t!=null&&blocks[t[0]][t[1]][t[2]]==0){blocks[t[0]][t[1]][t[2]]=selected; rebuild(); save();} }
        private void save() { StringBuilder b=new StringBuilder(32*24*32); for(int x=0;x<32;x++)for(int y=0;y<24;y++)for(int z=0;z<32;z++)b.append((char)('0'+blocks[x][y][z])); prefs.edit().putString("blocks",b.toString()).apply(); }
        private void rebuild() {
            java.util.ArrayList<Float> out=new java.util.ArrayList<>();
            for(int x=0;x<32;x++)for(int y=0;y<24;y++)for(int z=0;z<32;z++) {
                int b=blocks[x][y][z]; if(b==0)continue;
                float[][] colors={{0,0,0},{.30f,.68f,.22f},{.48f,.29f,.13f},{.48f,.50f,.52f},{.48f,.30f,.14f},{.15f,.48f,.17f}};
                float[] c=colors[Math.min(b,5)];
                if(!solid(x,y+1,z)) face(out,x,y,z,0,c[0]*1.12f,c[1]*1.12f,c[2]*1.12f);
                if(!solid(x,y-1,z)) face(out,x,y,z,1,c[0]*.55f,c[1]*.55f,c[2]*.55f);
                if(!solid(x+1,y,z)) face(out,x,y,z,2,c[0]*.82f,c[1]*.82f,c[2]*.82f);
                if(!solid(x-1,y,z)) face(out,x,y,z,3,c[0]*.78f,c[1]*.78f,c[2]*.78f);
                if(!solid(x,y,z+1)) face(out,x,y,z,4,c[0]*.92f,c[1]*.92f,c[2]*.92f);
                if(!solid(x,y,z-1)) face(out,x,y,z,5,c[0]*.72f,c[1]*.72f,c[2]*.72f);
            }
            float[] a=new float[out.size()]; for(int i=0;i<a.length;i++)a[i]=out.get(i);
            ByteBuffer bb=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()); vertices=bb.asFloatBuffer(); vertices.put(a).position(0); vertexCount=a.length/6;
        }
        private void face(java.util.ArrayList<Float> o,int x,int y,int z,int side,float r,float g,float b) {
            float[][] p;
            switch(side) {
                case 0:p=new float[][]{{0,1,0},{0,1,1},{1,1,1},{0,1,0},{1,1,1},{1,1,0}};break;
                case 1:p=new float[][]{{0,0,0},{1,0,1},{0,0,1},{0,0,0},{1,0,0},{1,0,1}};break;
                case 2:p=new float[][]{{1,0,0},{1,1,1},{1,0,1},{1,0,0},{1,1,0},{1,1,1}};break;
                case 3:p=new float[][]{{0,0,0},{0,0,1},{0,1,1},{0,0,0},{0,1,1},{0,1,0}};break;
                case 4:p=new float[][]{{0,0,1},{1,0,1},{1,1,1},{0,0,1},{1,1,1},{0,1,1}};break;
                default:p=new float[][]{{0,0,0},{1,1,0},{1,0,0},{0,0,0},{0,1,0},{1,1,0}};
            }
            for(float[] v:p){o.add(x+v[0]);o.add(y+v[1]);o.add(z+v[2]);o.add(r);o.add(g);o.add(b);}
        }
    }
}