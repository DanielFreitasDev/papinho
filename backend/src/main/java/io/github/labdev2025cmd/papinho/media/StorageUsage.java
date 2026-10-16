package io.github.labdev2025cmd.papinho.media;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Uso de disco no painel do admin (AD-01): arquivos enviados, banco e espaço livre do volume. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
class StorageUsage {

    private final JdbcTemplate jdbc;
    private final FileStorage storage;

    StorageUsage(JdbcTemplate jdbc, FileStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @GetMapping("/api/v1/admin/storage")
    @Transactional(readOnly = true)
    Usage usage() {
        Long files = jdbc.queryForObject("select coalesce(sum(size), 0) from media_files", Long.class);
        Long count = jdbc.queryForObject("select count(*) from media_files", Long.class);
        Long database = jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
        List<TopUser> top = jdbc.query("""
                select f.owner_id, u.username, sum(f.size) as bytes, count(*) as files
                  from media_files f left join user_accounts u on u.id = f.owner_id
                 where f.owner_id is not null
                 group by f.owner_id, u.username order by bytes desc limit 10
                """, (rs, index) -> new TopUser(rs.getObject("owner_id", UUID.class), rs.getString("username"),
                rs.getLong("bytes"), rs.getLong("files")));
        FileStorage.Space space = storage.space().orElse(null);
        return new Usage(files == null ? 0 : files, count == null ? 0 : count, database == null ? 0 : database,
                space == null ? null : space.usableBytes(), space == null ? null : space.totalBytes(), top);
    }

    /**
     * @param freeBytes espaço livre no volume dos arquivos; nulo quando o armazenamento não informa
     * @param top quem mais ocupa (só o dono de cada arquivo conta, como na cota)
     */
    record Usage(long filesBytes, long fileCount, long databaseBytes, @Nullable Long freeBytes,
            @Nullable Long totalBytes, List<TopUser> top) {
    }

    record TopUser(UUID userId, @Nullable String username, long bytes, long files) {
    }
}
