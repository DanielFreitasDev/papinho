package io.github.labdev2025cmd.papinho.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.profile.ProfileService;
import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
import io.github.labdev2025cmd.papinho.shared.crypto.SecretCipher;

/**
 * Login e recuperação gastam o mesmo número de hashes exista a conta ou não (ID-04, ID-05): o tempo da resposta
 * não pode dizer se o {@code @usuario} existe.
 */
class IdentityTimingTest {

    private final List<String> matchedHashes = new ArrayList<>();
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hash:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            matchedHashes.add(encoded);
            return encoded.equals(encode(raw));
        }
    };
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
    private final IdentityService service = new IdentityService(users, mock(UsernameReservationRepository.class),
            recoveryCodes, mock(InviteService.class), mock(ProfileService.class), mock(InstanceSettingsService.class),
            mock(PasswordPolicy.class), encoder, mock(SecretCipher.class), mock(RateLimiter.class),
            mock(JdbcTemplate.class), Clock.systemUTC());

    @Test
    @DisplayName("login de @usuario inexistente confere um hash, como o de conta existente com senha errada")
    void shouldMatchOnceForUnknownUser() {
        when(users.findByUsername("ninguem")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("ninguem", "senha qualquer", null, "ip"))
                .isInstanceOf(IdentityExceptions.BadCredentialsException.class);
        assertThat(matchedHashes).hasSize(1);

        matchedHashes.clear();
        when(users.findByUsername("ana")).thenReturn(Optional.of(account("ana")));
        assertThatThrownBy(() -> service.authenticate("ana", "senha errada", null, "ip"))
                .isInstanceOf(IdentityExceptions.BadCredentialsException.class);
        assertThat(matchedHashes).hasSize(1);
    }

    @Test
    @DisplayName("recuperação confere sempre o mesmo número de códigos, exista a conta ou não, com acerto ou não")
    void shouldMatchSameNumberOfCodesOnRecovery() {
        int count = IdentityService.RECOVERY_CODE_COUNT;
        when(users.findByUsername("ninguem")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recover("ninguem", "aaaa-bbbb-cccc", "senha nova bem longa", "ip"))
                .isInstanceOf(IdentityExceptions.InvalidCodeException.class);
        assertThat(matchedHashes).hasSize(count);

        UserAccount ana = account("ana");
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        // Três códigos já usados: sobram sete, e o acerto vem no primeiro.
        List<RecoveryCode> unused = new ArrayList<>();
        for (int i = 0; i < count - 3; i++) {
            unused.add(new RecoveryCode(ana.id(), encoder.encode("codigo" + i)));
        }
        when(recoveryCodes.findUnused(any())).thenReturn(unused);

        matchedHashes.clear();
        assertThatThrownBy(() -> service.recover("ana", "aaaa-bbbb-cccc", "senha nova bem longa", "ip"))
                .isInstanceOf(IdentityExceptions.InvalidCodeException.class);
        assertThat(matchedHashes).hasSize(count);

        matchedHashes.clear();
        service.recover("ana", "codigo0", "senha nova bem longa", "ip");
        assertThat(matchedHashes).hasSize(count);
    }

    private UserAccount account(String username) {
        UserAccount account = new UserAccount(Username.parse(username), encoder.encode("senha certa"), Role.USER,
                Instant.EPOCH);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
