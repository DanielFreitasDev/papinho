package io.github.labdev2025cmd.papinho.contact;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Porta que diz quem escolheu não aparecer na busca de pessoas por nome ("Aparecer na busca por nome").
 * Implementada pelo módulo de privacidade, que depende deste; o bloqueio a busca já aplica aqui mesmo. Quem fica de
 * fora continua sendo achado pelo {@code @usuario} exato e por quem já o tem nos contatos.
 */
public interface NameSearchPolicy {

    /** Quais destes usuários não aparecem na busca por nome. */
    Set<UUID> hiddenFromNameSearch(Collection<UUID> userIds);
}
