/**
 * Presença (RT-03): online a partir dos WebSockets com heartbeat de aba visível; aba em segundo plano não conta.
 * "Visto por último" é gravado quando a pessoa fica offline. Por enquanto só quem tem conversa aceita pelos dois
 * lados, sem bloqueio, vê a presença; a visibilidade configurável e a reciprocidade entram com PV-04 e PV-05.
 */
@ApplicationModule(displayName = "Presença")
@NullMarked
package io.github.labdev2025cmd.papinho.presence;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
