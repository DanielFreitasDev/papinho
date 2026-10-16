package io.github.labdev2025cmd.papinho.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.FileNames;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportar conversa (BK-01): um ZIP com o texto no formato do WhatsApp ({@code dd/MM/aaaa HH:mm - Nome: texto}, no
 * fuso de negócio) e, se pedido, os arquivos. Só entra o que a pessoa vê: nada do que ela apagou para si, nada de
 * visualização única nem de mensagens já vencidas; conversa trancada exige o token (PV-03).
 */
@Component
class ConversationExport {

    private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale LOCALE = Locale.forLanguageTag("pt-BR");
    private static final int PAGE = 500;

    private final ConversationService conversations;
    private final MediaService media;
    private final NamedParameterJdbcTemplate jdbc;
    private final MessageSource texts;
    private final JsonMapper json;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ConversationExport(ConversationService conversations, MediaService media, NamedParameterJdbcTemplate jdbc,
            MessageSource texts, JsonMapper json, RateLimiter rateLimiter, Clock clock) {
        this.conversations = conversations;
        this.media = media;
        this.jdbc = jdbc;
        this.texts = texts;
        this.json = json;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** Confere o acesso e o limite antes de começar a escrever a resposta; devolve o nome do arquivo. */
    @Transactional(readOnly = true)
    String prepare(UUID me, UUID conversationId) {
        conversations.requireReadable(me, conversationId);
        rateLimiter.consume(RateLimiter.Policy.EXPORT_BY_USER, me.toString());
        String name = label(me, conversationId);
        return FileNames.sanitize(texts.getMessage("export.fileName", new Object[] {name}, LOCALE));
    }

    /** Escreve o ZIP direto na resposta, em páginas, sem carregar a conversa inteira na memória. Só depois de prepare. */
    @Transactional(readOnly = true)
    void write(UUID me, UUID conversationId, boolean withMedia, OutputStream target) throws IOException {
        // O acesso (inclusive o token da conversa trancada) já foi conferido em prepare(), na mesma requisição;
        // aqui a resposta é escrita em outra thread, sem os cabeçalhos dela.
        Map<UUID, String> names = new HashMap<>();
        Set<String> used = new HashSet<>();
        Instant now = clock.instant();
        try (ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(texts.getMessage("export.chatFile", null, LOCALE)));
            List<UUID> attachments = new java.util.ArrayList<>();
            Map<UUID, String> fileNames = new HashMap<>();
            long after = 0;
            while (true) {
                List<Row> page = page(me, conversationId, after, now);
                if (page.isEmpty()) {
                    break;
                }
                Set<UUID> unknown = page.stream().map(Row::senderId).filter(id -> id != null && !names.containsKey(id))
                        .collect(Collectors.toSet());
                names.putAll(conversations.labels(me, unknown));
                StringBuilder lines = new StringBuilder();
                for (Row row : page) {
                    String when = WHEN.format(row.createdAt().atZone(ZONE));
                    if (row.kind() == MessageKind.SYSTEM) {
                        lines.append(when).append(" - ").append(notice(row.body())).append('\n');
                        continue;
                    }
                    String sender = row.senderId() == null ? texts.getMessage("export.deletedAccount", null, LOCALE)
                            : names.getOrDefault(row.senderId(), "");
                    String body;
                    if (row.deleted()) {
                        body = texts.getMessage("export.deleted", null, LOCALE);
                    } else if (row.viewOnce()) {
                        body = texts.getMessage("export.viewOnce", null, LOCALE);
                    } else if (row.attachmentId() != null) {
                        String file = fileName(row.seq(), row.attachmentFilename(), used);
                        fileNames.put(row.attachmentId(), file);
                        attachments.add(row.attachmentId());
                        String key = withMedia ? "export.attached" : "export.omitted";
                        body = texts.getMessage(key, new Object[] {file}, LOCALE)
                                + (row.body().isEmpty() ? "" : "\n" + row.body());
                    } else if (row.stickerId()) {
                        body = texts.getMessage("export.sticker", null, LOCALE);
                    } else if (row.kind() == MessageKind.POLL) {
                        body = texts.getMessage("export.poll", new Object[] {row.body()}, LOCALE);
                    } else {
                        body = row.body();
                    }
                    lines.append(when).append(" - ").append(sender).append(": ").append(body).append('\n');
                }
                zip.write(lines.toString().getBytes(StandardCharsets.UTF_8));
                after = page.getLast().seq();
            }
            zip.closeEntry();
            if (withMedia) {
                for (UUID attachment : attachments) {
                    try (InputStream in = media.content(me, attachment, false).resource().getInputStream()) {
                        zip.putNextEntry(new ZipEntry(fileNames.get(attachment)));
                        in.transferTo(zip);
                        zip.closeEntry();
                    } catch (RuntimeException | IOException unavailable) {
                        // Arquivo já removido (apagado ou vencido) depois de listado: fica só a linha no texto.
                    }
                }
            }
        }
    }

    private List<Row> page(UUID me, UUID conversationId, long after, Instant now) {
        return jdbc.query("""
                select m.seq, m.kind, m.sender_id, m.body, m.created_at, m.deleted_at is not null as deleted,
                       m.view_once, m.attachment_id, m.sticker_id is not null as sticker, f.filename
                  from messages m
                  left join media_files f on f.id = m.attachment_id
                 where m.conversation_id = :conversationId and m.seq > :after
                   and (m.expires_at is null or m.expires_at > :now)
                   and not exists (select 1 from message_hidden h where h.message_id = m.id and h.user_id = :userId)
                 order by m.seq limit :limit
                """, new MapSqlParameterSource("conversationId", conversationId).addValue("after", after)
                .addValue("userId", me).addValue("limit", PAGE)
                .addValue("now", now.atOffset(java.time.ZoneOffset.UTC)),
                (rs, index) -> new Row(rs.getLong("seq"), MessageKind.valueOf(rs.getString("kind")),
                        rs.getObject("sender_id", UUID.class), rs.getString("body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getBoolean("deleted"),
                        rs.getBoolean("view_once"), rs.getObject("attachment_id", UUID.class),
                        rs.getString("filename"), rs.getBoolean("sticker")));
    }

    private String label(UUID me, UUID conversationId) {
        ConversationService.ConversationView view = conversations.view(me, conversationId);
        if (view.group() != null) {
            return view.group().name();
        }
        if (view.broadcast() != null) {
            return view.broadcast().name();
        }
        return view.peer() == null ? "" : view.peer().label();
    }

    /** Nome do arquivo dentro do ZIP: seq na frente para não colidir e manter a ordem. */
    private static String fileName(long seq, @Nullable String original, Set<String> used) {
        String clean = FileNames.sanitize(original == null || original.isBlank() ? "arquivo" : original);
        String name = String.format(Locale.ROOT, "%06d-%s", seq, clean);
        int copy = 1;
        while (!used.add(name)) {
            name = String.format(Locale.ROOT, "%06d-%d-%s", seq, copy++, clean);
        }
        return name;
    }

    private String notice(String body) {
        try {
            SystemNotice notice = json.readValue(body, SystemNotice.class);
            String targets = notice.targets().stream().map(SystemNotice.Person::name)
                    .collect(Collectors.joining(", "));
            return texts.getMessage("export.notice." + notice.type(), new Object[] {notice.actor().name(), targets,
                    notice.value() == null ? "" : notice.value()}, notice.actor().name(), LOCALE);
        } catch (RuntimeException unreadable) {
            return texts.getMessage("export.notice", null, LOCALE);
        }
    }

    private record Row(long seq, MessageKind kind, @Nullable UUID senderId, String body, Instant createdAt,
            boolean deleted, boolean viewOnce, @Nullable UUID attachmentId, @Nullable String attachmentFilename,
            boolean stickerId) {
    }
}
