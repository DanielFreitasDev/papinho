package io.github.labdev2025cmd.papinho.messaging;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.media.MediaReferences;

/**
 * Arquivos que as mensagens usam: anexos (inclusive de mensagens escondidas ou encaminhadas), figurinhas (mesmo
 * tiradas da biblioteca, porque as mensagens enviadas continuam mostrando) e fotos de grupo (MD-01).
 */
@Component
class MessageMediaReferences implements MediaReferences {

    private final NamedParameterJdbcTemplate jdbc;

    MessageMediaReferences(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> inUse(Collection<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("""
                select attachment_id from messages where attachment_id in (:ids)
                union select media_id from stickers where media_id in (:ids)
                union select photo_media_id from group_details where photo_media_id in (:ids)
                """, new MapSqlParameterSource("ids", mediaIds), UUID.class));
    }
}
