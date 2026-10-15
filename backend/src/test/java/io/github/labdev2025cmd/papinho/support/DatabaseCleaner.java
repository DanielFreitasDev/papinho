package io.github.labdev2025cmd.papinho.support;

import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Zera os dados de negócio e as sessões antes de cada teste, mantendo o esquema e as configurações padrão. */
public class DatabaseCleaner extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        JdbcTemplate jdbc = testContext.getApplicationContext().getBean(JdbcTemplate.class);
        jdbc.execute("TRUNCATE TABLE user_accounts, spring_session, event_publication CASCADE");
        jdbc.update("""
                UPDATE instance_settings SET registration_mode = 'INVITE_ONLY', username_change_cooldown_days = 30,
                       message_edit_window_minutes = 15, message_delete_window_hours = 48,
                       user_quota_bytes = 5368709120, breached_password_check = FALSE
                """);
        testContext.getApplicationContext().getBean(RateLimiter.class).clear();
    }
}
