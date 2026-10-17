package io.github.labdev2025cmd.papinho.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;

/**
 * Teste slice de persistência (QA-01): só o JPA e o Flyway contra o PostgreSQL do Testcontainers, sem o resto da
 * aplicação.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LinkPreviewRepositoryIT {

    @Autowired
    private LinkPreviewRepository previews;

    @Test
    @DisplayName("esquece só as prévias cujo endereço aparece no texto (MS-06)")
    void shouldDeleteOnlyMentionedPreviews() {
        Instant now = Instant.now();
        previews.save(new LinkPreview("a".repeat(64), "https://exemplo.org/secreto", now));
        previews.save(new LinkPreview("b".repeat(64), "https://exemplo.org/publico", now));
        previews.flush();

        int deleted = previews.deleteMentionedIn("olha https://exemplo.org/secreto agora");

        assertThat(deleted).isEqualTo(1);
        assertThat(previews.findAll()).extracting(LinkPreview::urlHash).containsExactly("b".repeat(64));
    }
}
