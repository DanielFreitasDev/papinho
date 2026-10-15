package io.github.labdev2025cmd.papinho.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário autenticado da requisição atual. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Usuário autenticado, se houver. */
    public static Optional<PapinhoUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PapinhoUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Id do usuário autenticado.
     *
     * @throws IllegalStateException fora de uma requisição autenticada (erro de programação: a rota
     *     deveria exigir autenticação)
     */
    public static UUID id() {
        return get().map(PapinhoUser::id).orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
