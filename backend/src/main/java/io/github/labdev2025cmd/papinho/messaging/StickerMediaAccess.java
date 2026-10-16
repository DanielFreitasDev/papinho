package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.MediaAccessPolicy;

/**
 * As figurinhas são a biblioteca da instância: qualquer pessoa com sessão abre o arquivo das que estão nela
 * (MS-10). Uma figurinha tirada da biblioteca (ou do pacote tirado) só abre para quem participa de uma conversa
 * em que ela foi enviada, como prevê a migration: as mensagens já enviadas continuam mostrando.
 */
@Component
class StickerMediaAccess implements MediaAccessPolicy {

    private final NamedParameterJdbcTemplate jdbc;

    StickerMediaAccess(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID viewer, UUID mediaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from stickers s join sticker_packs p on p.id = s.pack_id
                                where s.media_id = :id and s.removed_at is null and p.removed_at is null)
                    or exists (select 1 from stickers s
                                 join messages m on m.sticker_id = s.id
                                 join conversation_members cm on cm.conversation_id = m.conversation_id
                                where s.media_id = :id and cm.user_id = :viewer
                                  and cm.state in ('ACTIVE', 'REQUEST'))
                """, new MapSqlParameterSource("id", mediaId).addValue("viewer", viewer), Boolean.class));
    }
}
