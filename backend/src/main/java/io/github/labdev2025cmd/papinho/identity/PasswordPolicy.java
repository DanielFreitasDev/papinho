package io.github.labdev2025cmd.papinho.identity;

import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.admin.InstanceSettingsService;

/**
 * Política de senha (ID-04, JAVA.md 8.3): comprimento entre 12 e 128 caracteres, sem regras de composição, e
 * consulta opcional a senhas vazadas. A consulta usa k-anonimato (só os 5 primeiros caracteres do SHA-1
 * saem da máquina) e fica desligada por padrão, porque a instância pode não ter internet.
 */
@Component
class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private final InstanceSettingsService settings;
    private final CompromisedPasswordChecker checker;

    PasswordPolicy(InstanceSettingsService settings, CompromisedPasswordChecker checker) {
        this.settings = settings;
        this.checker = checker;
    }

    void validate(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || password.isBlank()) {
            throw new IdentityExceptions.WeakPasswordException();
        }
        if (settings.current().breachedPasswordCheck() && checker.check(password).isCompromised()) {
            throw new IdentityExceptions.CompromisedPasswordException();
        }
    }
}
