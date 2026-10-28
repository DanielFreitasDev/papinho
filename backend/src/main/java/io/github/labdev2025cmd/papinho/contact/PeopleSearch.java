package io.github.labdev2025cmd.papinho.contact;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.identity.UserDirectory.UserSummary;
import io.github.labdev2025cmd.papinho.identity.Username;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.profile.ProfileView;

/**
 * Busca de pessoas da instância para as sugestões ao adicionar contato: pelo nome de exibição (módulo de perfil)
 * ou pelo começo do {@code @usuario} (identidade). Ficam de fora quem busca, contas desativadas, quem tem bloqueio
 * com quem busca (em qualquer direção) e quem desligou "Aparecer na busca por nome" ({@link NameSearchPolicy}), a
 * não ser que a busca seja o {@code @usuario} exato dele ou que ele já esteja nos contatos de quem busca.
 *
 * <p>Ordem: o {@code @usuario} exato, depois os nomes que começam pela busca e então o resto, por nome e
 * {@code @usuario}. Foto e recado saem do perfil já filtrados para quem busca (bloqueio e PV-04).</p>
 */
@Component
class PeopleSearch {

    /** Candidatos buscados por resultado, para ainda completar a lista depois dos filtros. */
    private static final int CANDIDATES_PER_RESULT = 3;
    private static final Locale SORT_LOCALE = Locale.forLanguageTag("pt-BR");

    private final UserDirectory users;
    private final ProfileService profiles;
    private final ContactRepository contacts;
    private final BlockRepository blocks;
    private final List<NameSearchPolicy> policies;

    PeopleSearch(UserDirectory users, ProfileService profiles, ContactRepository contacts, BlockRepository blocks,
            List<NameSearchPolicy> policies) {
        this.users = users;
        this.profiles = profiles;
        this.contacts = contacts;
        this.blocks = blocks;
        this.policies = policies;
    }

    /** Até {@code limit} pessoas para {@code viewer}; a busca já chega sem o {@code @} e sem espaços sobrando. */
    @Transactional(readOnly = true)
    List<ContactService.PersonView> find(UUID viewer, String query, int limit) {
        // Candidatos: começo do @usuario (só contas ativas) e nome (a conta ativa é conferida aqui).
        int candidates = limit * CANDIDATES_PER_RESULT;
        Map<UUID, UserSummary> found = new HashMap<>();
        users.findByUsernamePrefix(query, candidates).forEach(user -> found.put(user.id(), user));
        Set<UUID> startsWithQuery = new HashSet<>();
        List<UUID> foundOnlyByName = new ArrayList<>();
        for (ProfileService.NameMatch match : profiles.searchByName(query, candidates)) {
            if (match.startsWithQuery()) {
                startsWithQuery.add(match.userId());
            }
            if (!found.containsKey(match.userId())) {
                foundOnlyByName.add(match.userId());
            }
        }
        users.findAllById(foundOnlyByName).stream().filter(UserSummary::active)
                .forEach(user -> found.put(user.id(), user));
        // Fora: quem busca, bloqueio nas duas direções e quem saiu da busca por nome (menos o exato e os contatos).
        found.remove(viewer);
        if (found.isEmpty()) {
            return List.of();
        }
        blocks.findBetween(viewer, Set.copyOf(found.keySet())).forEach(block -> found.remove(block.otherThan(viewer)));
        if (found.isEmpty()) {
            return List.of();
        }

        Set<UUID> ids = Set.copyOf(found.keySet());
        Set<UUID> contactIds = contacts.findByOwnerAndContacts(viewer, ids).stream().map(Contact::contactId)
                .collect(Collectors.toSet());
        Set<UUID> hidden = new HashSet<>();
        policies.forEach(policy -> hidden.addAll(policy.hiddenFromNameSearch(ids)));
        String exactUsername = Username.normalize(query);
        found.values().removeIf(user -> hidden.contains(user.id()) && !contactIds.contains(user.id())
                && !user.username().equals(exactUsername));

        // Ordem e limite; foto e recado só de quem vai na resposta, já filtrados para quem busca.
        Map<UUID, String> names = profiles.displayNames(List.copyOf(found.keySet()));
        Collator collator = Collator.getInstance(SORT_LOCALE);
        collator.setStrength(Collator.SECONDARY);
        Comparator<UserSummary> order = Comparator
                .comparingInt((UserSummary user) -> rank(user, exactUsername, startsWithQuery))
                .thenComparing(user -> names.get(user.id()), collator).thenComparing(UserSummary::username);
        List<UserSummary> top = found.values().stream().filter(user -> names.containsKey(user.id())).sorted(order)
                .limit(limit).toList();
        Map<UUID, ProfileView> views = profiles.viewAll(viewer, top.stream().map(UserSummary::id).toList());
        return top.stream().filter(user -> views.containsKey(user.id()))
                .map(user -> ContactService.PersonView.of(user, views.get(user.id()), contactIds.contains(user.id()),
                        false, false))
                .toList();
    }

    /** 0 para o {@code @usuario} exato, 1 para o nome que começa pela busca e 2 para o resto. */
    private static int rank(UserSummary user, String exactUsername, Set<UUID> startsWithQuery) {
        if (user.username().equals(exactUsername)) {
            return 0;
        }
        return startsWithQuery.contains(user.id()) ? 1 : 2;
    }
}
