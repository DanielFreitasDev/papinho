package io.github.labdev2025cmd.papinho.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;

import io.github.labdev2025cmd.papinho.admin.InstanceSettings;
import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;
import io.github.labdev2025cmd.papinho.admin.RegistrationMode;

class PasswordPolicyTest {

    private static final String LEAKED = "senha vazada qualquer";

    private final AtomicInteger checks = new AtomicInteger();
    private final CompromisedPasswordChecker checker = password -> {
        checks.incrementAndGet();
        return new CompromisedPasswordDecision(LEAKED.equals(password));
    };

    private PasswordPolicy policy(boolean breachedCheck) {
        InstanceSettingsService settings = mock(InstanceSettingsService.class);
        when(settings.current()).thenReturn(new InstanceSettings(RegistrationMode.INVITE_ONLY, Duration.ofDays(30),
                Duration.ofMinutes(15), Duration.ofHours(1), 1L << 30, breachedCheck));
        return new PasswordPolicy(settings, checker);
    }

    @Test
    @DisplayName("comprimento entre 12 e 128 caracteres, contados em code points, sem regra de composição")
    void shouldEnforceLength() {
        PasswordPolicy policy = policy(false);
        assertThatThrownBy(() -> policy.validate("curta demai")).isInstanceOf(
                IdentityExceptions.WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate(" ".repeat(20))).isInstanceOf(
                IdentityExceptions.WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("a".repeat(129))).isInstanceOf(
                IdentityExceptions.WeakPasswordException.class);
        assertThatCode(() -> policy.validate("🦜".repeat(12))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("todas minusculas")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checagem de senha vazada só consulta o serviço quando o admin liga")
    void shouldCheckBreachedPasswordsOnlyWhenEnabled() {
        assertThatCode(() -> policy(false).validate(LEAKED)).doesNotThrowAnyException();
        assertThatCode(() -> policy(false).validate("outra senha boa")).doesNotThrowAnyException();
        assertThat(checks).hasValue(0);

        assertThatThrownBy(() -> policy(true).validate(LEAKED)).isInstanceOf(
                IdentityExceptions.CompromisedPasswordException.class);
        assertThatCode(() -> policy(true).validate("outra senha boa")).doesNotThrowAnyException();
        assertThat(checks).hasValue(2);
    }
}
