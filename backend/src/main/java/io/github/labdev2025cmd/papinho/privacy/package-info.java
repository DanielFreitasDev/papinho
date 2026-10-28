/**
 * Privacidade (Fase 5), aplicada no servidor (SG-04): quem vê visto por último, online, foto e recado (PV-04), com
 * reciprocidade (PV-05); confirmação de leitura global (PV-06; a sobreposição por conversa fica nas mensagens);
 * quem pode me adicionar a grupos (PV-07); notificações sem conteúdo (PV-08); preferências de desfoque (PV-01);
 * aparecer na busca de pessoas por nome; bloqueio de tela com segredo próprio (PV-02) e conversas trancadas com token
 * de desbloqueio curto (PV-03).
 *
 * <p>Depende só de contatos, identidade e perfil; mensagens e presença consultam este módulo. A participação nas
 * conversas chega pela porta {@link io.github.labdev2025cmd.papinho.privacy.ConversationMembership}.</p>
 */
@ApplicationModule(displayName = "Privacidade")
@NullMarked
package io.github.labdev2025cmd.papinho.privacy;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
