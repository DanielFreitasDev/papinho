package io.github.labdev2025cmd.papinho.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;

/** "Visto por último" gravado quando a conexão cai, inclusive depois de a conta ser excluída. */
@IntegrationTest
class PresenceIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private PresenceService presence;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("ao sair grava o visto por último; conta excluída antes de a conexão cair não gera erro")
    void shouldRecordLastSeenOnlyForExistingAccounts() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        ApiClient bruno = clients.create();
        UUID brunoId = UUID.fromString(Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(admin))));

        presence.heartbeat(brunoId, "c1", true);
        presence.disconnected(brunoId, "c1");
        assertThat(count(brunoId)).isOne();

        presence.heartbeat(brunoId, "c2", true);
        assertThat(bruno.delete("/api/v1/account", Map.of("password", Accounts.PASSWORD))).hasStatus(204);
        assertThatNoException().isThrownBy(() -> presence.disconnected(brunoId, "c2"));
        assertThat(count(brunoId)).isZero();
    }

    private Integer count(UUID userId) {
        return jdbc.queryForObject("select count(*) from user_presence where user_id = ?", Integer.class, userId);
    }
}
