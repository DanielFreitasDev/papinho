package io.github.labdev2025cmd.papinho.privacy;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.contact.ContactQueries;
import io.github.labdev2025cmd.papinho.privacy.PrivacyExceptions.InvalidPrivacyException;
import io.github.labdev2025cmd.papinho.profile.ProfileVisibilityPolicy;

/**
 * Configurações de privacidade de cada pessoa e as regras que os outros módulos aplicam: visibilidade de visto
 * por último, online, foto e recado (PV-04) com reciprocidade na presença (PV-05), confirmação de leitura global
 * (PV-06), quem pode me adicionar a grupos (PV-07), notificações sem conteúdo (PV-08), desfoque (PV-01) e aparecer
 * na busca de pessoas por nome.
 */
@Service
public class PrivacyService implements ProfileVisibilityPolicy {

    /** Maior atraso para revelar ao passar o mouse (PV-01). */
    static final int MAX_REVEAL_DELAY_MS = 10_000;

    private static final Settings DEFAULTS = new Settings(Visibility.EVERYONE, Visibility.EVERYONE,
            Visibility.EVERYONE, Visibility.EVERYONE, Visibility.EVERYONE, true, false, true,
            new Blur(false, true, true, false, false, true, 0));

    private final NamedParameterJdbcTemplate jdbc;
    private final ContactQueries contacts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PrivacyService(NamedParameterJdbcTemplate jdbc, ContactQueries contacts, ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.contacts = contacts;
        this.events = events;
        this.clock = clock;
    }

    /** Configurações de {@code userId}; sem linha no banco, os padrões (tudo visível, como no WhatsApp). */
    @Transactional(readOnly = true)
    public Settings settings(UUID userId) {
        return settingsOf(List.of(userId)).getOrDefault(userId, DEFAULTS);
    }

    /** Altera só os campos informados. */
    @Transactional
    public Settings update(UUID userId, SettingsChange change) {
        Settings current = settings(userId);
        Blur blur = current.blur();
        Blur nextBlur = new Blur(or(change.privateMode(), blur.privateMode()),
                or(change.blurMessages(), blur.messages()), or(change.blurPreviews(), blur.previews()),
                or(change.blurNames(), blur.names()), or(change.blurPhotos(), blur.photos()),
                or(change.blurMedia(), blur.media()), or(change.revealDelayMs(), blur.revealDelayMs()));
        if (nextBlur.revealDelayMs() < 0 || nextBlur.revealDelayMs() > MAX_REVEAL_DELAY_MS) {
            throw new InvalidPrivacyException("reveal delay");
        }
        Settings next = new Settings(or(change.lastSeen(), current.lastSeen()), or(change.online(), current.online()),
                or(change.photo(), current.photo()), or(change.about(), current.about()),
                or(change.groupsAdd(), current.groupsAdd()), or(change.readReceipts(), current.readReceipts()),
                or(change.hideNotificationContent(), current.hideNotificationContent()),
                or(change.nameSearch(), current.nameSearch()), nextBlur);
        jdbc.update("""
                insert into privacy_settings (user_id, last_seen, online, photo, about, groups_add, read_receipts,
                       hide_notification_content, name_search, private_mode, blur_messages, blur_previews, blur_names,
                       blur_photos, blur_media, reveal_delay_ms, updated_at)
                values (:userId, :lastSeen, :online, :photo, :about, :groupsAdd, :readReceipts, :hideContent,
                       :nameSearch, :privateMode, :blurMessages, :blurPreviews, :blurNames, :blurPhotos, :blurMedia,
                       :delay, :now)
                on conflict (user_id) do update set last_seen = excluded.last_seen, online = excluded.online,
                       photo = excluded.photo, about = excluded.about, groups_add = excluded.groups_add,
                       read_receipts = excluded.read_receipts,
                       hide_notification_content = excluded.hide_notification_content,
                       name_search = excluded.name_search,
                       private_mode = excluded.private_mode, blur_messages = excluded.blur_messages,
                       blur_previews = excluded.blur_previews, blur_names = excluded.blur_names,
                       blur_photos = excluded.blur_photos, blur_media = excluded.blur_media,
                       reveal_delay_ms = excluded.reveal_delay_ms, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("userId", userId).addValue("lastSeen", next.lastSeen().name())
                .addValue("online", next.online().name()).addValue("photo", next.photo().name())
                .addValue("about", next.about().name()).addValue("groupsAdd", next.groupsAdd().name())
                .addValue("readReceipts", next.readReceipts())
                .addValue("hideContent", next.hideNotificationContent()).addValue("nameSearch", next.nameSearch())
                .addValue("privateMode", nextBlur.privateMode()).addValue("blurMessages", nextBlur.messages())
                .addValue("blurPreviews", nextBlur.previews()).addValue("blurNames", nextBlur.names())
                .addValue("blurPhotos", nextBlur.photos()).addValue("blurMedia", nextBlur.media())
                .addValue("delay", nextBlur.revealDelayMs())
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        if (next.lastSeen() != current.lastSeen() || next.online() != current.online()) {
            events.publishEvent(new PrivacyEvents.PresenceVisibilityChanged(userId));
        }
        if (next.readReceipts() != current.readReceipts()) {
            events.publishEvent(new PrivacyEvents.ReadReceiptsChanged(userId));
        }
        return next;
    }

    /** Listas "Meus contatos exceto…" de {@code userId}, por item. */
    @Transactional(readOnly = true)
    public Map<PrivacyItem, List<UUID>> exceptions(UUID userId) {
        Map<PrivacyItem, List<UUID>> result = new EnumMap<>(PrivacyItem.class);
        for (PrivacyItem item : PrivacyItem.values()) {
            result.put(item, new java.util.ArrayList<>());
        }
        jdbc.query("select setting, excluded_id from privacy_exceptions where user_id = :userId order by excluded_id",
                new MapSqlParameterSource("userId", userId), row -> {
                    result.get(PrivacyItem.valueOf(row.getString("setting")))
                            .add(row.getObject("excluded_id", UUID.class));
                });
        return result;
    }

    /** Troca a lista "exceto…" de um item; só contatos podem estar nela (os outros já ficam de fora). */
    @Transactional
    public List<UUID> replaceExceptions(UUID userId, PrivacyItem item, Collection<UUID> excluded) {
        Set<UUID> wanted = new LinkedHashSet<>(excluded);
        wanted.remove(userId);
        Set<UUID> allowed = contacts.contactNicknames(userId, wanted).keySet();
        if (!allowed.containsAll(wanted)) {
            throw new InvalidPrivacyException("exceptions must be contacts");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("setting", item.name());
        jdbc.update("delete from privacy_exceptions where user_id = :userId and setting = :setting", params);
        for (UUID id : wanted) {
            jdbc.update("insert into privacy_exceptions (user_id, setting, excluded_id) values (:userId, :setting, :id)",
                    new MapSqlParameterSource(params.getValues()).addValue("id", id));
        }
        if (item == PrivacyItem.LAST_SEEN || item == PrivacyItem.ONLINE) {
            events.publishEvent(new PrivacyEvents.PresenceVisibilityChanged(userId));
        }
        return List.copyOf(wanted);
    }

    /** A regra de {@code owner} para {@code item} deixa {@code viewer} ver (ou, em grupos, adicionar)? */
    @Transactional(readOnly = true)
    public boolean allows(UUID owner, PrivacyItem item, UUID viewer) {
        if (owner.equals(viewer)) {
            return true;
        }
        Visibility visibility = settings(owner).of(item);
        return switch (visibility) {
            case EVERYONE -> true;
            case NOBODY -> false;
            case CONTACTS -> contacts.isContact(owner, viewer);
            case CONTACTS_EXCEPT -> contacts.isContact(owner, viewer) && !isExcepted(owner, item, viewer);
        };
    }

    /**
     * Presença com reciprocidade (PV-05): {@code viewer} vê o visto por último (ou online) de {@code owner} só se a
     * regra de {@code owner} permitir e se {@code viewer} também compartilhar o seu com {@code owner}.
     */
    @Transactional(readOnly = true)
    public boolean canSeePresence(UUID viewer, UUID owner, PrivacyItem item) {
        if (item != PrivacyItem.LAST_SEEN && item != PrivacyItem.ONLINE) {
            throw new IllegalArgumentException("Not a presence item: " + item);
        }
        return viewer.equals(owner) || allows(owner, item, viewer) && allows(viewer, item, owner);
    }

    /** {@code adder} pode adicionar {@code target} a um grupo (PV-07)? */
    @Transactional(readOnly = true)
    public boolean canAddToGroups(UUID adder, UUID target) {
        return allows(target, PrivacyItem.GROUPS_ADD, adder);
    }

    /** Confirmação de leitura global de cada pessoa (PV-06): o padrão das conversas individuais. */
    @Transactional(readOnly = true)
    public Map<UUID, Boolean> readReceipts(Collection<UUID> userIds) {
        Map<UUID, Settings> found = settingsOf(userIds);
        Map<UUID, Boolean> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, found.getOrDefault(id, DEFAULTS).readReceipts()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean readReceipts(UUID userId) {
        return settings(userId).readReceipts();
    }

    /** As notificações de {@code userId} mostram só "Nova mensagem" (PV-08)? */
    @Transactional(readOnly = true)
    public boolean hidesNotificationContent(UUID userId) {
        return settings(userId).hideNotificationContent();
    }

    @Override
    public boolean canSeePhoto(UUID viewer, UUID owner) {
        return allows(owner, PrivacyItem.PHOTO, viewer);
    }

    @Override
    public boolean canSeeAbout(UUID viewer, UUID owner) {
        return allows(owner, PrivacyItem.ABOUT, viewer);
    }

    private boolean isExcepted(UUID owner, PrivacyItem item, UUID viewer) {
        Integer count = jdbc.queryForObject("select count(*) from privacy_exceptions where user_id = :owner "
                + "and setting = :setting and excluded_id = :viewer", new MapSqlParameterSource("owner", owner)
                .addValue("setting", item.name()).addValue("viewer", viewer), Integer.class);
        return count != null && count > 0;
    }

    private Map<UUID, Settings> settingsOf(Collection<UUID> userIds) {
        Map<UUID, Settings> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query("select * from privacy_settings where user_id in (:ids)", new MapSqlParameterSource("ids", userIds),
                row -> {
                    result.put(row.getObject("user_id", UUID.class), new Settings(
                            Visibility.valueOf(row.getString("last_seen")), Visibility.valueOf(row.getString("online")),
                            Visibility.valueOf(row.getString("photo")), Visibility.valueOf(row.getString("about")),
                            Visibility.valueOf(row.getString("groups_add")), row.getBoolean("read_receipts"),
                            row.getBoolean("hide_notification_content"), row.getBoolean("name_search"),
                            new Blur(row.getBoolean("private_mode"), row.getBoolean("blur_messages"),
                                    row.getBoolean("blur_previews"), row.getBoolean("blur_names"),
                                    row.getBoolean("blur_photos"), row.getBoolean("blur_media"),
                                    row.getInt("reveal_delay_ms"))));
                });
        return result;
    }

    private static <T> T or(@Nullable T value, T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Configurações de privacidade de uma pessoa.
     *
     * @param readReceipts confirmação de leitura nas conversas individuais (grupos e conversas podem sobrepor)
     * @param hideNotificationContent notificações mostram só "Nova mensagem" (PV-08)
     * @param nameSearch aparece nas sugestões da busca de pessoas por nome; desligado, só o {@code @usuario} exato e
     *        quem já tem a pessoa nos contatos a acham
     * @param blur desfoque do modo privado (PV-01)
     */
    public record Settings(Visibility lastSeen, Visibility online, Visibility photo, Visibility about,
            Visibility groupsAdd, boolean readReceipts, boolean hideNotificationContent, boolean nameSearch,
            Blur blur) {

        Visibility of(PrivacyItem item) {
            return switch (item) {
                case LAST_SEEN -> lastSeen;
                case ONLINE -> online;
                case PHOTO -> photo;
                case ABOUT -> about;
                case GROUPS_ADD -> groupsAdd;
            };
        }
    }

    /**
     * Desfoque (PV-01): o modo privado liga o desfoque dos itens escolhidos. É uma proteção visual aplicada pelo
     * cliente; o servidor só guarda a escolha para valer em todos os dispositivos.
     *
     * @param revealDelayMs quanto o mouse precisa ficar em cima para revelar (0 = na hora)
     */
    public record Blur(boolean privateMode, boolean messages, boolean previews, boolean names, boolean photos,
            boolean media, int revealDelayMs) {
    }

    /** Alteração parcial: campos nulos ficam como estão. */
    public record SettingsChange(@Nullable Visibility lastSeen, @Nullable Visibility online,
            @Nullable Visibility photo, @Nullable Visibility about, @Nullable Visibility groupsAdd,
            @Nullable Boolean readReceipts, @Nullable Boolean hideNotificationContent, @Nullable Boolean nameSearch,
            @Nullable Boolean privateMode, @Nullable Boolean blurMessages, @Nullable Boolean blurPreviews,
            @Nullable Boolean blurNames, @Nullable Boolean blurPhotos, @Nullable Boolean blurMedia,
            @Nullable Integer revealDelayMs) {
    }
}
