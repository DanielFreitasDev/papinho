package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaAccessPolicy;

/** As figurinhas são a biblioteca da instância: qualquer pessoa com sessão abre o arquivo delas (MS-10). */
@Component
class StickerMediaAccess implements MediaAccessPolicy {

    private final NamedParameterJdbcTemplate jdbc;

    StickerMediaAccess(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID viewer, UUID mediaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from stickers where media_id = :id)",
                new MapSqlParameterSource("id", mediaId), Boolean.class));
    }
}
