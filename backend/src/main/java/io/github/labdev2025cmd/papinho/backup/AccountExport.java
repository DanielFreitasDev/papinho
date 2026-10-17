package io.github.labdev2025cmd.papinho.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.FileNames;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exportar todos os dados da conta (BK-02, portabilidade da LGPD): um ZIP com {@code dados.json} (uma seção por
 * módulo, em JSON legível) e, se pedido, os arquivos que a pessoa enviou em {@code arquivos/} e a foto de perfil em
 * {@code perfil/foto.jpg}.
 */
@Component
class AccountExport {

    private final List<PersonalDataSection> sections;
    private final MediaService media;
    private final ProfileService profiles;
    private final JsonMapper json;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    AccountExport(List<PersonalDataSection> sections, MediaService media, ProfileService profiles, JsonMapper json,
            RateLimiter rateLimiter, Clock clock) {
        this.sections = sections.stream().sorted(Comparator.comparing(PersonalDataSection::name)).toList();
        this.media = media;
        this.profiles = profiles;
        this.json = json;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    void checkLimit(UUID userId) {
        rateLimiter.consume(RateLimiter.Policy.EXPORT_BY_USER, userId.toString());
    }

    /**
     * Junta os dados na thread da requisição: o token de conversa trancada vem do cabeçalho dela (PV-03), e o
     * corpo em streaming roda depois, noutra thread.
     */
    @Transactional(readOnly = true)
    Prepared prepare(UUID userId, boolean withFiles) {
        ObjectNode data = json.createObjectNode();
        data.put("exportadoEm", clock.instant().toString());
        sections.forEach(section -> data.set(section.name(), section.collect(userId)));
        return new Prepared(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data),
                withFiles ? media.ownedBy(userId) : List.of(), withFiles ? photo(userId) : null);
    }

    /** A foto já recodificada e reduzida pelo perfil: cabe na memória. */
    private byte @Nullable [] photo(UUID userId) {
        return profiles.photo(userId, userId, false).map(in -> {
            try (in) {
                return in.readAllBytes();
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to read profile photo", exception);
            }
        }).orElse(null);
    }

    void write(Prepared prepared, OutputStream target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("dados.json"));
            zip.write(prepared.data());
            zip.closeEntry();
            if (prepared.photo() != null) {
                zip.putNextEntry(new ZipEntry("perfil/foto.jpg"));
                zip.write(prepared.photo());
                zip.closeEntry();
            }
            for (MediaService.OwnedFile file : prepared.files()) {
                try (InputStream in = media.grantedContent(file.id()).resource().getInputStream()) {
                    zip.putNextEntry(new ZipEntry("arquivos/" + file.id() + "-" + FileNames.sanitize(file.filename())));
                    in.transferTo(zip);
                    zip.closeEntry();
                } catch (RuntimeException | IOException gone) {
                    // Arquivo já removido do armazenamento: fica só a linha em dados.json.
                }
            }
        }
    }

    /** Dados já reunidos e os arquivos a incluir. */
    record Prepared(byte[] data, List<MediaService.OwnedFile> files, byte @Nullable [] photo) {
    }
}
