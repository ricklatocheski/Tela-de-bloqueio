package com.ericaerick.lockscreen;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Serviço de acesso às notificações.
 * Serve para: (1) ler a música tocando (Spotify) e
 * (2) detectar alarme/chamada em andamento, para NÃO cobrir essas telas.
 * Ativado em: Configurações → Acesso a notificações.
 */
public class MediaNotificationListener extends NotificationListenerService {

    private static MediaNotificationListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) instance = null;
    }

    /** true se houver um alarme tocando ou uma chamada em tela cheia. */
    public static boolean isAlarmOrCallActive() {
        MediaNotificationListener s = instance;
        if (s == null) return false;
        try {
            StatusBarNotification[] arr = s.getActiveNotifications();
            if (arr == null) return false;
            for (StatusBarNotification sbn : arr) {
                Notification n = sbn.getNotification();
                if (n == null) continue;
                String cat = n.category;
                if (Notification.CATEGORY_ALARM.equals(cat)
                        || Notification.CATEGORY_CALL.equals(cat)
                        || Notification.CATEGORY_EVENT.equals(cat)
                        || Notification.CATEGORY_REMINDER.equals(cat)) {
                    return true;
                }
                // Alarme/chamada normalmente usam "full screen intent"
                if (n.fullScreenIntent != null) return true;
            }
        } catch (Exception e) { /* ignora */ }
        return false;
    }
}
