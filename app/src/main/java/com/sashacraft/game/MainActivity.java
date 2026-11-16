package com.sashacraft.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        game = new GameView(this);
        setContentView(game);
    }
    @Override protected void onPause() { super.onPause(); if(game!=null) game.onPause(); }
    @Override protected void onResume() { super.onResume(); if(game!=null) game.onResume(); }
}