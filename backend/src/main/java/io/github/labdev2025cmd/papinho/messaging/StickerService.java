package io.github.labdev2025cmd.papinho.messaging;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.media.MediaView;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.InvalidMessageException;
import io.github.labdev2025cmd.papinho.messaging.MessagingExceptions.NotFoundException;
import io.github.labdev2025cmd.papinho.shared.id.Uuids;

/**
 * Figurinhas (MS-10): pacotes enviados pelos usuários da instância, em WebP (estáticas ou animadas), e as
 * favoritas de cada pessoa. A biblioteca é a mesma para todos; só o dono tira figurinhas ou o pacote.
 */
@Service
public class StickerService {

    static final int MAX_PER_PACK = 120;
    static final long MAX_BYTES = 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaService media;
    private final Clock clock;

    StickerService(NamedParameterJdbcTemplate jdbc, MediaService media, Clock clock) {
        this.jdbc = jdbc;
        this.media = media;
        this.clock = clock;
    }

    /** Todos os pacotes da instância, com as figurinhas em ordem. */
    @Transactional(readOnly = true)
    public List<PackView> packs(UUID me) {
        Map<UUID, PackView> packs = new LinkedHashMap<>();
        jdbc.query("select id, name, owner_id from sticker_packs where removed_at is null order by created_at",
                new MapSqlParameterSource(), rs -> {
                    UUID owner = rs.getObject("owner_id", UUID.class);
                    UUID id = rs.getObject("id", UUID.class);
                    packs.put(id, new PackView(id, rs.getString("name"), me.equals(owner), new ArrayList<>()));
                });
        List<UUID> favorites = favoriteIds(me);
        jdbc.query("""
                select s.id, s.pack_id, s.media_id, s.emoji from stickers s
                 join sticker_packs p on p.id = s.pack_id
                 where s.removed_at is null and p.removed_at is null order by s.pack_id, s.position
                """, new MapSqlParameterSource(), rs -> {
            PackView pack = packs.get(rs.getObject("pack_id", UUID.class));
            if (pack != null) {
                UUID id = rs.getObject("id", UUID.class);
                pack.stickers().add(new StickerView(id, rs.getObject("media_id", UUID.class), rs.getString("emoji"),
                        favorites.contains(id)));
            }
        });
        return List.copyOf(packs.values());
    }

    @Transactional
    public PackView createPack(UUID me, String name) {
        String clean = name.strip();
        if (clean.isEmpty() || clean.length() > 64) {
            throw new InvalidMessageException("pack name");
        }
        UUID id = Uuids.v7();
        jdbc.update("insert into sticker_packs (id, owner_id, name, created_at) values (:id, :me, :name, :now)",
                new MapSqlParameterSource("id", id).addValue("me", me).addValue("name", clean)
                        .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        return new PackView(id, clean, true, List.of());
    }

    /** Põe no pacote um WebP já enviado por tus pelo dono do pacote (até {@value #MAX_BYTES} bytes). */
    @Transactional
    public StickerView addSticker(UUID me, UUID packId, UUID mediaId, @Nullable String emoji) {
        requireOwnPack(me, packId);
        MediaView file = media.requireOwned(me, mediaId);
        if (!"image/webp".equals(file.contentType()) || file.size() > MAX_BYTES) {
            throw new InvalidMessageException("sticker must be a WebP up to 1 MB");
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from stickers where pack_id = :packId and removed_at is null",
                new MapSqlParameterSource("packId", packId), Integer.class);
        if (count != null && count >= MAX_PER_PACK) {
            throw new InvalidMessageException("pack is full");
        }
        String cleanEmoji = emoji == null || emoji.isBlank() ? null : emoji.strip();
        if (cleanEmoji != null && cleanEmoji.length() > 32) {
            throw new InvalidMessageException("emoji");
        }
        UUID id = Uuids.v7();
        jdbc.update("""
                insert into stickers (id, pack_id, media_id, emoji, position, created_at)
                values (:id, :packId, :mediaId, :emoji,
                        (select coalesce(max(position), -1) + 1 from stickers where pack_id = :packId), :now)
                """, new MapSqlParameterSource("id", id).addValue("packId", packId).addValue("mediaId", mediaId)
                .addValue("emoji", cleanEmoji).addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        return new StickerView(id, mediaId, cleanEmoji, false);
    }

    /** Tira a figurinha da biblioteca (mensagens já enviadas continuam com ela). */
    @Transactional
    public void removeSticker(UUID me, UUID stickerId) {
        UUID pack = jdbc.queryForList("select pack_id from stickers where id = :id and removed_at is null",
                new MapSqlParameterSource("id", stickerId), UUID.class).stream().findFirst()
                .orElseThrow(NotFoundException::new);
        requireOwnPack(me, pack);
        jdbc.update("update stickers set removed_at = :now where id = :id", new MapSqlParameterSource("id", stickerId)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
    }

    @Transactional
    public void removePack(UUID me, UUID packId) {
        requireOwnPack(me, packId);
        jdbc.update("update sticker_packs set removed_at = :now where id = :id", new MapSqlParameterSource("id", packId)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
    }

    @Transactional
    public void favorite(UUID me, UUID stickerId, boolean favorite) {
        requireAvailable(stickerId);
        MapSqlParameterSource params = new MapSqlParameterSource("me", me).addValue("id", stickerId)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC));
        if (favorite) {
            jdbc.update("insert into sticker_favorites (user_id, sticker_id, created_at) values (:me, :id, :now) "
                    + "on conflict do nothing", params);
        } else {
            jdbc.update("delete from sticker_favorites where user_id = :me and sticker_id = :id", params);
        }
    }

    /** Figurinhas favoritas de {@code me}, da mais recente. */
    @Transactional(readOnly = true)
    public List<StickerView> favorites(UUID me) {
        return jdbc.query("""
                select s.id, s.media_id, s.emoji from sticker_favorites f join stickers s on s.id = f.sticker_id
                 where f.user_id = :me and s.removed_at is null order by f.created_at desc
                """, new MapSqlParameterSource("me", me), (rs, index) -> new StickerView(rs.getObject("id", UUID.class),
                rs.getObject("media_id", UUID.class), rs.getString("emoji"), true));
    }

    /** Arquivo de uma figurinha disponível para envio; senão 400. */
    UUID requireAvailable(UUID stickerId) {
        return jdbc.queryForList("""
                select s.media_id from stickers s join sticker_packs p on p.id = s.pack_id
                 where s.id = :id and s.removed_at is null and p.removed_at is null
                """, new MapSqlParameterSource("id", stickerId), UUID.class).stream().findFirst()
                .orElseThrow(() -> new InvalidMessageException("sticker"));
    }

    /** Arquivo de uma figurinha (mesmo tirada da biblioteca), para mostrar nas mensagens. */
    Map<UUID, UUID> mediaOf(java.util.Collection<UUID> stickerIds) {
        Map<UUID, UUID> result = new LinkedHashMap<>();
        if (stickerIds.isEmpty()) {
            return result;
        }
        jdbc.query("select id, media_id from stickers where id in (:ids)", new MapSqlParameterSource("ids", stickerIds),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getObject("media_id", UUID.class));
                });
        return result;
    }

    private void requireOwnPack(UUID me, UUID packId) {
        Optional<UUID> owner = jdbc.query("select owner_id from sticker_packs where id = :id and removed_at is null",
                new MapSqlParameterSource("id", packId), (rs, index) -> Optional.ofNullable(rs.getObject(1, UUID.class)))
                .stream().findFirst().orElseThrow(NotFoundException::new);
        if (owner.isEmpty() || !owner.get().equals(me)) {
            throw new NotFoundException();
        }
    }

    private List<UUID> favoriteIds(UUID me) {
        return jdbc.queryForList("select sticker_id from sticker_favorites where user_id = :me",
                new MapSqlParameterSource("me", me), UUID.class);
    }

    /** @param mine o pacote é meu (posso tirar figurinhas e apagá-lo) */
    public record PackView(UUID id, String name, boolean mine, List<StickerView> stickers) {
    }

    /** Figurinha; a imagem vem de {@code /api/v1/media/{mediaId}/content}. */
    public record StickerView(UUID id, UUID mediaId, @Nullable String emoji, boolean favorite) {
    }
}
