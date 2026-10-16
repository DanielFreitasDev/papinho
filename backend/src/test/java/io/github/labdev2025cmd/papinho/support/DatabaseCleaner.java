package io.github.labdev2025cmd.papinho.support;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Zera os dados de negócio e as sessões antes de cada teste, mantendo o esquema e as configurações padrão. */
public class DatabaseCleaner extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        JdbcTemplate jdbc = testContext.getApplicationContext().getBean(JdbcTemplate.class);
        truncate(jdbc);
        // DELETE (não TRUNCATE): o TRUNCATE em cascata apagaria a linha única de transcription_settings.
        jdbc.execute("DELETE FROM transcription_providers");
        jdbc.update("""
                UPDATE instance_settings SET registration_mode = 'INVITE_ONLY', username_change_cooldown_days = 30,
                       message_edit_window_minutes = 15, message_delete_window_hours = 48,
                       user_quota_bytes = 5368709120, breached_password_check = FALSE
                """);
        testContext.getApplicationContext().getBean(RateLimiter.class).clear();
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
