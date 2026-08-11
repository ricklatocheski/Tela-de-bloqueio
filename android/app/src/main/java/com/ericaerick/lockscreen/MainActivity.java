package com.ericaerick.lockscreen;

import android.app.Activity;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean biometricInProgress = false;
    private CancellationSignal cancelSignal;
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

        // Ponte: a senha correta (no HTML) chama AndroidLock.unlock()
        web.addJavascriptInterface(new WebAppInterface(), "AndroidLock");

        setContentView(web);
        hideSystemUi();
        web.loadUrl("file:///android_asset/index.html");

        ensureOverlayAndService();
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void unlock() {
            runOnUiThread(new Runnable() {
                @Override public void run() { doUnlock(); }
            });
        }
        @JavascriptInterface
        public void fingerprint() {
            runOnUiThread(new Runnable() {
                @Override public void run() { promptBiometric(); }
            });
        }
    }

    private void promptBiometric() {
        if (Build.VERSION.SDK_INT < 28) {
            // Sem API de digital: cai para a senha reserva
            if (web != null) web.evaluateJavascript("window.showPin && window.showPin();", null);
            return;
        }
        if (biometricInProgress) return;
        biometricInProgress = true;
        cancelSignal = new CancellationSignal();
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Erica e Erick 💕")
                    .setDescription("Toque no sensor para desbloquear")
                    .setNegativeButton("Usar senha", getMainExecutor(),
                            new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) {
                                    biometricInProgress = false;
                                    if (web != null) web.evaluateJavascript("window.showPin && window.showPin();", null);
                                }
                            })
                    .build();
            prompt.authenticate(cancelSignal, getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            biometricInProgress = false;
                            doUnlock();
                        }
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            biometricInProgress = false;
                        }
                        @Override
                        public void onAuthenticationFailed() {
                            // dedo nao reconhecido: continua tentando
                        }
                    });
        } catch (Exception e) {
            biometricInProgress = false;
            if (web != null) web.evaluateJavascript("window.showPin && window.showPin();", null);
        }
    }

    private void cancelBiometric() {
        if (cancelSignal != null) {
            try { cancelSignal.cancel(); } catch (Exception e) { /* ignora */ }
            cancelSignal = null;
        }
        biometricInProgress = false;
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

    private void doUnlock() {
        // Reseta o teclado e manda para segundo plano (mantem na memoria = reabre instantaneo)
        if (web != null) web.evaluateJavascript("window.resetLock && window.resetLock();", null);
        moveTaskToBack(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        hideSystemUi();
        if (web != null) web.evaluateJavascript("window.resetLock && window.resetLock();", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        // Ao aparecer a tela, pede a digital automaticamente (pequeno atraso p/ estabilizar)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { promptBiometric(); }
        }, 350);
    }

    @Override
    public void onBackPressed() {
        // Nao desbloqueia pelo botao voltar: so pela senha.
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
