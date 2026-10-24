package io.github.labdev2025cmd.papinho.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.TimeZone;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

/**
 * Travas dos jobs (ShedLock com a hora do banco, como em {@link SchedulingConfig}): a validade gravada vale pela hora
 * real em qualquer fuso da JVM. Num servidor fora de UTC, o driver abria a sessão no fuso da máquina e as travas
 * ficavam horas fora da hora real.
 */
@IntegrationTest
class ShedLockIT {

    private static final String LOCK = "teste-fuso-da-jvm";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("JVM em America/Fortaleza: a trava de 5 min vence em 5 min pela hora real, não 3 h depois")
    void shouldStoreLockValidityInRealTimeWhateverTheJvmTimeZone() throws Exception {
        // Um pool novo com a configuração da aplicação: o driver lê o fuso da JVM ao abrir cada conexão.
        HikariConfig config = new HikariConfig();
        dataSource.unwrap(HikariDataSource.class).copyStateTo(config);
        config.setPoolName("shedlock-fuso-da-jvm");
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Fortaleza"));
        try (HikariDataSource pool = new HikariDataSource(config)) {
            JdbcTemplate session = new JdbcTemplate(pool);
            JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration
                    .builder().withJdbcTemplate(session).usingDbTime().build());
            Optional<SimpleLock> lock = provider
                    .lock(new LockConfiguration(Instant.now(), LOCK, Duration.ofMinutes(5), Duration.ZERO));
            assertThat(lock).isPresent();
            Long seconds = jdbc.queryForObject(
                    "select extract(epoch from lock_until - now())::bigint from shedlock where name = ?", Long.class,
                    LOCK);
            assertThat(seconds).as("validade de 5 min a partir de agora").isBetween(240L, 300L);
            assertThat(session.queryForObject("show timezone", String.class)).isEqualTo("UTC");
            lock.get().unlock();
        } finally {
            TimeZone.setDefault(original);
            jdbc.update("delete from shedlock where name = ?", LOCK);
        }
    }
}
