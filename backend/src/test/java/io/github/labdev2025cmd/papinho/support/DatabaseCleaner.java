package io.github.labdev2025cmd.papinho.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Zera os dados de negócio e as sessões antes de cada teste, mantendo o esquema e as configurações padrão. Os
 * padrões são os que a migration gravou (lidos uma vez por banco, antes do primeiro teste), e não uma cópia aqui:
 * assim um padrão inseguro na migration aparece nos testes (AD-02).
 */
public class DatabaseCleaner extends AbstractTestExecutionListener {

    private static final Map<String, Map<String, Object>> MIGRATED_SETTINGS = new ConcurrentHashMap<>();
    private static final Set<String> NOT_SETTINGS = Set.of("id", "updated_at", "version");

    @Override
    public void beforeTestMethod(TestContext testContext) {
        JdbcTemplate jdbc = testContext.getApplicationContext().getBean(JdbcTemplate.class);
        Map<String, Object> defaults = MIGRATED_SETTINGS.computeIfAbsent(databaseUrl(jdbc),
                url -> jdbc.queryForMap("SELECT * FROM instance_settings WHERE id = 1"));
        truncate(jdbc);
        // DELETE (não TRUNCATE): o TRUNCATE em cascata apagaria a linha única de transcription_settings.
        jdbc.execute("DELETE FROM transcription_providers");
        List<String> columns = defaults.keySet().stream().filter(column -> !NOT_SETTINGS.contains(column)).toList();
        jdbc.update("UPDATE instance_settings SET " + String.join(" = ?, ", columns) + " = ? WHERE id = 1",
                columns.stream().map(defaults::get).toArray());
        testContext.getApplicationContext().getBean(RateLimiter.class).clear();
    }

    private static String databaseUrl(JdbcTemplate jdbc) {
        return jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
    }

    /**
     * Um ouvinte assíncrono do teste anterior (aviso, notificação, prévia) pode ainda estar numa transação: o
     * TRUNCATE então espera o lock ou é escolhido como vítima de deadlock. Espera um pouco e tenta de novo.
     */
    private static void truncate(JdbcTemplate jdbc) {
        for (int attempt = 1; ; attempt++) {
            try {
                jdbc.execute("TRUNCATE TABLE user_accounts, spring_session, event_publication, link_previews CASCADE");
                return;
            } catch (PessimisticLockingFailureException busy) {
                if (attempt == 5) {
                    throw busy;
                }
                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw busy;
                }
            }
        }
    }
}
