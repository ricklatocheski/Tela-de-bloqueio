package com.ericaerick.lockscreen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private GestureDetector gesture;
    private static final int FILE_REQUEST = 1001;
    private static final int OVERLAY_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showOverLockScreen();
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF000000);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        hideSystemUi();
        web.loadUrl("file:///android_asset/index.html");

        // Deslizar para cima = desbloquear (sai da nossa tela e vai para o telefone)
        gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && e2 != null && (e1.getY() - e2.getY()) > 180 && Math.abs(vy) > 700) {
                    unlock();
                    return true;
                }
                return false;
            }
        });

        ensureOverlayAndService();
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gesture != null) gesture.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void unlock() {
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(false);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Botao voltar tambem desbloqueia (garantia de saida)
        unlock();
    }

    private void ensureOverlayAndService() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, OVERLAY_REQUEST);
            } catch (Exception e) {
                startLockService();
            }
        } else {
            startLockService();
        }
    }

    private void startLockService() {
        try {
            Intent svc = new Intent(this, LockService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Exception e) { /* ignora */ }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
        } else if (requestCode == OVERLAY_REQUEST) {
            startLockService();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }
}
