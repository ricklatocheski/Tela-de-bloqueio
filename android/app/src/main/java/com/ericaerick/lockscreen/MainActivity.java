package com.ericaerick.lockscreen;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.fingerprint.FingerprintManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.json.JSONObject;
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

    // Mídia (Spotify)
    private MediaController mediaController;
    private MediaController.Callback mediaCallback;
    private final Handler mediaHandler = new Handler(Looper.getMainLooper());
    private String lastArtKey = "";
    private Runnable mediaPoll;

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
        @JavascriptInterface
        public void media(final String action) {
            runOnUiThread(new Runnable() {
                @Override public void run() { mediaControl(action); }
            });
        }
        @JavascriptInterface
        public void connectMedia() {
            runOnUiThread(new Runnable() {
                @Override public void run() { openNotificationAccess(); }
            });
        }
    }

    // ===== Música (Spotify / qualquer player) =====

    private boolean hasNotificationAccess() {
        try {
            String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void startMedia() {
        if (mediaPoll == null) {
            mediaPoll = new Runnable() {
                @Override public void run() {
                    refreshMedia();
                    mediaHandler.postDelayed(this, 2500);
                }
            };
        }
        mediaHandler.removeCallbacks(mediaPoll);
        mediaHandler.post(mediaPoll);
    }

    private void stopMedia() {
        if (mediaPoll != null) mediaHandler.removeCallbacks(mediaPoll);
    }

    private void refreshMedia() {
        if (Build.VERSION.SDK_INT < 21) return;
        if (!hasNotificationAccess()) {
            sendNoAccess();
            return;
        }
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName comp = new ComponentName(this, MediaNotificationListener.class);
            List<MediaController> controllers = msm.getActiveSessions(comp);

            MediaController chosen = null;
            // Prioriza o Spotify; senão, o primeiro que estiver tocando
            for (MediaController c : controllers) {
                if ("com.spotify.music".equals(c.getPackageName())) { chosen = c; break; }
            }
            if (chosen == null) {
                for (MediaController c : controllers) {
                    PlaybackState ps = c.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { chosen = c; break; }
                }
            }
            if (chosen == null && !controllers.isEmpty()) chosen = controllers.get(0);

            if (chosen == null) { sendInactive(); attachController(null); return; }

            attachController(chosen);
            pushNowPlaying(chosen);
        } catch (SecurityException se) {
            sendNoAccess();
        } catch (Exception e) {
            sendInactive();
        }
    }

    private void attachController(MediaController c) {
        if (mediaController == c) return;
        if (mediaController != null && mediaCallback != null) {
            try { mediaController.unregisterCallback(mediaCallback); } catch (Exception ignored) {}
        }
        mediaController = c;
        if (c == null) return;
        mediaCallback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) { pushNowPlaying(mediaController); }
            @Override public void onMetadataChanged(MediaMetadata metadata) { pushNowPlaying(mediaController); }
            @Override public void onSessionDestroyed() { sendInactive(); }
        };
        try { c.registerCallback(mediaCallback); } catch (Exception ignored) {}
    }

    private void pushNowPlaying(MediaController c) {
        if (c == null) { sendInactive(); return; }
        try {
            MediaMetadata md = c.getMetadata();
            PlaybackState ps = c.getPlaybackState();
            String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
            String artistTxt = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
            if (artistTxt == null && md != null) artistTxt = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;

            JSONObject o = new JSONObject();
            o.put("active", title != null);
            o.put("hasAccess", true);
            o.put("title", title == null ? "" : title);
            o.put("artist", artistTxt == null ? "" : artistTxt);
            o.put("playing", playing);

            // Capa só quando a faixa muda (economia)
            String key = (title == null ? "" : title) + "|" + (artistTxt == null ? "" : artistTxt);
            if (md != null && !key.equals(lastArtKey)) {
                Bitmap bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (bmp != null) {
                    o.put("art", bitmapToDataUri(bmp));
                }
                lastArtKey = key;
            }
            sendToWeb(o.toString());
        } catch (Exception e) {
            sendInactive();
        }
    }

    private String bitmapToDataUri(Bitmap bmp) {
        try {
            int size = 128;
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, size, size, true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            return "data:image/jpeg;base64," + b64;
        } catch (Exception e) { return null; }
    }

    private void sendInactive() {
        sendToWeb("{\"active\":false,\"hasAccess\":true}");
    }
    private void sendNoAccess() {
        sendToWeb("{\"hasAccess\":false}");
    }
    private void sendToWeb(final String json) {
        if (web == null) return;
        final String js = "window.setNowPlaying && window.setNowPlaying(" + json + ");";
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
            }
        });
    }

    private void mediaControl(String action) {
        if (mediaController == null) return;
        MediaController.TransportControls tc = mediaController.getTransportControls();
        if (tc == null || action == null) return;
        try {
            if ("next".equals(action)) tc.skipToNext();
            else if ("prev".equals(action)) tc.skipToPrevious();
            else if ("toggle".equals(action)) {
                PlaybackState ps = mediaController.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) tc.pause();
                else tc.play();
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void promptBiometric() {
        if (Build.VERSION.SDK_INT < 23) {
            if (web != null) web.evaluateJavascript("window.showPin && window.showPin();", null);
            return;
        }
        FingerprintManager fm;
        try {
            fm = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
        } catch (Exception e) { fm = null; }

        if (fm == null || !fm.isHardwareDetected() || !fm.hasEnrolledFingerprints()) {
            // Sem digital cadastrada: usa a senha reserva
            if (web != null) web.evaluateJavascript("window.showPin && window.showPin();", null);
            return;
        }
        if (biometricInProgress) return;
        biometricInProgress = true;
        cancelSignal = new CancellationSignal();
        try {
            // Leitura SILENCIOSA da digital: sem janela do sistema por cima da tela
            fm.authenticate(null, cancelSignal, 0,
                    new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                            biometricInProgress = false;
                            doUnlock();
                        }
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            // lockout ou cancelado: usuario pode usar a senha
                            biometricInProgress = false;
                        }
                        @Override
                        public void onAuthenticationFailed() {
                            // dedo nao reconhecido: continua escutando
                        }
                    }, null);
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
        // Aparece POR CIMA do bloqueio, mas NAO liga a tela sozinho
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
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
        // Ao aparecer a tela, comeca a escutar a digital (silenciosa)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { promptBiometric(); }
        }, 150);
        // E começa a acompanhar a música tocando
        startMedia();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelBiometric();
        stopMedia();
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
