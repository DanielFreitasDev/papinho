package io.github.labdev2025cmd.papinho.admin;

import java.time.Duration;

/**
 * Configurações atuais da instância (AD-02), com os padrões seguros definidos na migration.
 *
 * @param registrationMode modo de cadastro
 * @param usernameChangeCooldown intervalo mínimo entre trocas de {@code @usuario} (ID-03)
 * @param messageEditWindow janela para editar mensagens (MS-05)
 * @param messageDeleteWindow janela para apagar para todos (MS-06)
 * @param userQuotaBytes cota de armazenamento por usuário (MD-01)
 * @param breachedPasswordCheck consulta senhas vazadas (Have I Been Pwned, k-anonimato) no cadastro (ID-04)
 */
public record InstanceSettings(
        RegistrationMode registrationMode,
        Duration usernameChangeCooldown,
        Duration messageEditWindow,
        Duration messageDeleteWindow,
        long userQuotaBytes,
        boolean breachedPasswordCheck) {
}
