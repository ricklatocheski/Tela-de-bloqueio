package com.ericaerick.lockscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class LockService extends Service {

    private BroadcastReceiver screenReceiver;
    private static final String CHANNEL_ID = "lockscreen_channel";
    private static final int NOTIF_ID = 42;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, buildNotification());

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // Mostra a tela SOMENTE quando o usuario acende o celular
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    showLock(context);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private void showLock(final Context context) {
        // Pequeno atraso para dar tempo do alarme/chamada aparecer, e então checar
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                // Se tiver alarme tocando ou chamada, NÃO mostra a tela do casal
                if (MediaNotificationListener.isAlarmOrCallActive()) return;
                try {
                    Intent lock = new Intent(context, MainActivity.class);
                    lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(lock);
                } catch (Exception e) { /* ignora */ }
            }
        }, 200);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Tela do casal", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Erica e Erick 💕")
                .setContentText("Tela do casal ativa")
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception e) { /* ignora */ }
        }
    }
}
