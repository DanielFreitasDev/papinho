package io.github.labdev2025cmd.papinho.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.media.FileNames;
import io.github.labdev2025cmd.papinho.media.MediaService;
import io.github.labdev2025cmd.papinho.shared.personal.PersonalDataSection;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exportar todos os dados da conta (BK-02, portabilidade da LGPD): um ZIP com {@code dados.json} (uma seção por
 * módulo, em JSON legível) e, se pedido, os arquivos que a pessoa enviou em {@code arquivos/}.
 */
@Component
class AccountExport {

    private final List<PersonalDataSection> sections;
    private final MediaService media;
    private final JsonMapper json;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    AccountExport(List<PersonalDataSection> sections, MediaService media, JsonMapper json, RateLimiter rateLimiter,
            Clock clock) {
        this.sections = sections.stream().sorted(Comparator.comparing(PersonalDataSection::name)).toList();
        this.media = media;
        this.json = json;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    void checkLimit(UUID userId) {
        rateLimiter.consume(RateLimiter.Policy.EXPORT_BY_USER, userId.toString());
    }

    @Transactional(readOnly = true)
    void write(UUID userId, boolean withFiles, OutputStream target) throws IOException {
        ObjectNode data = json.createObjectNode();
        data.put("exportadoEm", clock.instant().toString());
        sections.forEach(section -> data.set(section.name(), section.collect(userId)));
        try (ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("dados.json"));
            zip.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
            zip.closeEntry();
            if (withFiles) {
                for (MediaService.OwnedFile file : media.ownedBy(userId)) {
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
    }
}
