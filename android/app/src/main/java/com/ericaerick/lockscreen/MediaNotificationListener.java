package com.ericaerick.lockscreen;

import android.service.notification.NotificationListenerService;

/**
 * Serviço vazio: existe só para conceder acesso às sessões de mídia
 * (necessário para ler a música que está tocando, ex.: Spotify).
 * O usuário ativa em: Configurações → Acesso a notificações.
 */
public class MediaNotificationListener extends NotificationListenerService {
}
