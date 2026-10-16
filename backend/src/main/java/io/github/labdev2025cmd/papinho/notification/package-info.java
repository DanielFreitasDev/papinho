/**
 * Notificações (NT-01, NT-03, PV-08): preferências de cada pessoa, assinaturas de Web Push (VAPID) e a decisão de
 * avisar uma mensagem nova, aplicada no servidor. O aviso vai pelo WebSocket (o app aberto mostra pela
 * Notification API) e por push cifrado para os navegadores assinados (app fechado).
 *
 * <p>Ouve {@link io.github.labdev2025cmd.papinho.messaging.MessagePosted} pelo registro de eventos do Spring
 * Modulith e pergunta ao módulo de mensagens quem avisar; o "ocultar conteúdo" vem da privacidade.</p>
 */
@ApplicationModule(displayName = "Notificações")
@NullMarked
package io.github.labdev2025cmd.papinho.notification;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
