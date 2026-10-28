package io.github.labdev2025cmd.papinho.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;

import io.github.labdev2025cmd.papinho.TestcontainersConfiguration;

/**
 * Teste de integração com o contexto completo, MockMvc e PostgreSQL do Testcontainers. O banco é zerado antes
 * de cada teste por {@link DatabaseCleaner}; todos os testes que usam esta anotação compartilham o contexto. Os jobs
 * da duração dos áudios e das páginas dos PDFs antigos não rodam sozinhos durante a suíte (os testes deles os chamam
 * na hora certa).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"app.media.duration-backfill-delay=1d", "app.media.pages-backfill-delay=1d"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
@TestExecutionListeners(listeners = DatabaseCleaner.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public @interface IntegrationTest {
}
