package io.github.labdev2025cmd.papinho.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública do módulo para os outros: localizar usuários ativos por {@code @usuario} (ou começo dele) ou id. */
@Service
public class UserDirectory {

    private final UserAccountRepository users;

    UserDirectory(UserAccountRepository users) {
        this.users = users;
    }

    /** Usuário ativo com esse {@code @usuario} (aceita {@code @} e maiúsculas). */
    @Transactional(readOnly = true)
    public Optional<UserSummary> findByUsername(String rawUsername) {
        return users.findByUsername(Username.normalize(rawUsername)).filter(UserAccount::enabled)
                .map(UserDirectory::toSummary);
    }

    /**
     * Usuários ativos cujo {@code @usuario} começa por {@code rawPrefix} (aceita {@code @} e maiúsculas), em ordem
     * alfabética. Entrada com caractere que nenhum {@code @usuario} tem (espaço, {@code %}...) não acha ninguém.
     */
    @Transactional(readOnly = true)
    public List<UserSummary> findByUsernamePrefix(String rawPrefix, int limit) {
        String prefix = Username.normalize(rawPrefix);
        if (!Username.isPossiblePrefix(prefix)) {
            return List.of();
        }
        return users.findEnabledByUsernamePrefix(prefix, limit).stream().map(UserDirectory::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(UUID id) {
        return users.findById(id).filter(UserAccount::enabled).map(UserDirectory::toSummary);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAllById(Collection<UUID> ids) {
        return users.findAllById(ids).stream().map(UserDirectory::toSummary).toList();
    }

    private static UserSummary toSummary(UserAccount account) {
        return new UserSummary(account.id(), account.username().value(), account.enabled());
    }

    /** Identificação mínima de um usuário. */
    public record UserSummary(UUID id, String username, boolean active) {
    }
}
