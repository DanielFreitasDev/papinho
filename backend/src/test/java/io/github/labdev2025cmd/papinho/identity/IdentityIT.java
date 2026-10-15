package io.github.labdev2025cmd.papinho.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Cadastro, login, convites, TOTP, recuperação e troca de {@code @usuario} pela API real (ID-01 a ID-08). */
@IntegrationTest
class IdentityIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("primeiro cadastro dispensa convite, vira admin e abre sessão __Host-SESSION")
    void shouldMakeFirstUserAdmin() {
        ApiClient client = clients.create().bootstrap();
        assertThat(client.json(client.get("/api/v1/instance")).get("setupRequired").asBoolean()).isTrue();

        JsonNode body = Accounts.register(client, "@Ana.Silva", null);

        JsonNode account = body.get("me").get("account");
        assertThat(account.get("username").asString()).isEqualTo("ana.silva");
        assertThat(account.get("admin").asBoolean()).isTrue();
        assertThat(body.get("recoveryCodes")).hasSize(10);
        assertThat(body.get("recoveryCodes").get(0).asString()).matches("^[a-z2-9]{5}-[a-z2-9]{5}$");
        assertThat(body.get("me").get("profile").get("displayName").asString()).isEqualTo("Pessoa @Ana.Silva");
        assertThat(client.cookie("__Host-SESSION")).isNotBlank();
        assertThat(client.get("/api/v1/me")).hasStatusOk();
        assertThat(jdbc.queryForObject("select id::text from user_accounts", String.class)).satisfies(
                id -> assertThat(java.util.UUID.fromString(id).version()).isEqualTo(7));
    }

    @Test
    @DisplayName("cookie de sessão sai com __Host-, HttpOnly, Secure e SameSite=Lax")
    void shouldIssueHardenedSessionCookie() {
        ApiClient client = clients.create().bootstrap();
        client.post("/api/v1/auth/register", Map.of("username", "ana", "displayName", "Ana",
                "password", Accounts.PASSWORD));
        var result = client.get("/api/v1/me");
        assertThat(result).hasStatusOk();

        var login = clients.create().bootstrap();
        var response = login.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD));
        assertThat(response.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(cookie ->
                assertThat(cookie).startsWith("__Host-SESSION=").contains("Path=/").contains("Secure")
                        .contains("HttpOnly").contains("SameSite=Lax").doesNotContain("Domain"));
    }

    @Test
    @DisplayName("modo somente convite: sem convite é recusado; com convite válido entra como usuário comum")
    void shouldRequireInviteAfterFirstUser() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "admin.ana", null);
        String token = Accounts.invite(admin);

        ApiClient withoutInvite = clients.create().bootstrap();
        var refused = withoutInvite.post("/api/v1/auth/register", Map.of("username", "bruno",
                "displayName", "Bruno", "password", Accounts.PASSWORD));
        assertThat(refused).hasStatus(403);
        assertThat(withoutInvite.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:invite-invalid");

        ApiClient invited = clients.create();
        assertThat(invited.json(invited.get("/api/v1/invites/status?token=" + token)).get("usable").asBoolean())
                .isTrue();
        JsonNode bruno = Accounts.register(invited, "bruno", token);
        assertThat(bruno.get("me").get("account").get("admin").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("convite revogado ou esgotado não serve mais")
    void shouldRejectRevokedOrExhaustedInvite() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        var created = admin.post("/api/v1/invites", Map.of("validHours", 1, "maxUses", 1));
        String url = admin.json(created).get("url").asString();
        String token = url.substring(url.indexOf("invite=") + 7);
        assertThat(admin.json(created).get("qrSvg").asString()).startsWith("<svg");

        Accounts.register(clients.create(), "bruno", token);
        ApiClient third = clients.create().bootstrap();
        assertThat(third.post("/api/v1/auth/register", Map.of("username", "carla", "displayName", "Carla",
                "password", Accounts.PASSWORD, "inviteToken", token))).hasStatus(403);

        String second = Accounts.invite(admin);
        String inviteId = admin.json(admin.get("/api/v1/invites")).get(0).get("id").asString();
        assertThat(admin.delete("/api/v1/invites/" + inviteId, null)).hasStatus(204);
        assertThat(third.post("/api/v1/auth/register", Map.of("username", "carla", "displayName", "Carla",
                "password", Accounts.PASSWORD, "inviteToken", second))).hasStatus(403);
    }

    @Test
    @DisplayName("modo aberto aceita cadastro sem convite; modo fechado recusa")
    void shouldApplyRegistrationModes() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        assertThat(admin.put("/api/v1/admin/settings", settings("OPEN", 30))).hasStatusOk();
        Accounts.register(clients.create(), "bruno", null);

        assertThat(admin.put("/api/v1/admin/settings", settings("CLOSED", 30))).hasStatusOk();
        ApiClient late = clients.create().bootstrap();
        var refused = late.post("/api/v1/auth/register", Map.of("username", "carla", "displayName", "Carla",
                "password", Accounts.PASSWORD));
        assertThat(refused).hasStatus(403);
        assertThat(late.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:registration-closed");
    }

    @Test
    @DisplayName("@usuario é único sem diferenciar maiúsculas e respeita reservados e formato")
    void shouldEnforceUsernameRules() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana.silva", null);
        admin.put("/api/v1/admin/settings", settings("OPEN", 30));

        ApiClient other = clients.create().bootstrap();
        assertThat(register(other, "ANA.SILVA")).hasStatus(409);
        assertThat(register(other, "Ａｎａ.silva")).as("NFKC: letras de largura total").hasStatus(409);
        assertThat(register(other, "admin")).hasStatus(409);
        assertThat(register(other, "a.d.m.i.n")).hasStatus(409);
        assertThat(register(other, "ab")).hasStatus(400);
        assertThat(register(other, "ana..silva")).hasStatus(400);
        assertThat(register(other, "ana-silva")).hasStatus(400);
        assertThat(other.json(other.get("/api/v1/usernames/availability?username=Ana.Silva")).get("available")
                .asBoolean()).isFalse();
        assertThat(other.json(other.get("/api/v1/usernames/availability?username=beto_99")).get("available")
                .asBoolean()).isTrue();
    }

    @Test
    @DisplayName("senha com menos de 12 caracteres é recusada")
    void shouldRejectShortPassword() {
        ApiClient client = clients.create().bootstrap();
        var result = client.post("/api/v1/auth/register", Map.of("username", "ana", "displayName", "Ana",
                "password", "curta123"));
        assertThat(result).hasStatus(400);
        assertThat(client.json(result).get("type").asString()).isEqualTo("urn:papinho:problem:password-weak");
    }

    @Test
    @DisplayName("erro de login é igual para usuário inexistente e senha errada; sucesso abre sessão")
    void shouldUseUniformLoginErrors() {
        Accounts.register(clients.create(), "ana", null);
        ApiClient client = clients.create().bootstrap();

        var wrongPassword = client.post("/api/v1/auth/login", Map.of("username", "ana", "password", "errada errada 1"));
        var unknownUser = client.post("/api/v1/auth/login", Map.of("username", "ninguem", "password", "errada errada 1"));

        assertThat(wrongPassword).hasStatus(401);
        assertThat(unknownUser).hasStatus(401);
        for (String field : new String[] {"type", "title", "detail"}) {
            assertThat(client.json(wrongPassword).get(field)).isEqualTo(client.json(unknownUser).get(field));
        }
        assertThat(client.json(wrongPassword).get("type").asString()).isEqualTo("urn:papinho:problem:bad-credentials");
        assertThat(client.post("/api/v1/auth/login", Map.of("username", "@ANA", "password", Accounts.PASSWORD)))
                .hasStatusOk();
        assertThat(client.get("/api/v1/me")).hasStatusOk();
    }

    @Test
    @DisplayName("tentativas demais de login viram 429 com Retry-After no Problem Details (Bucket4j)")
    void shouldRateLimitLogin() {
        Accounts.register(clients.create(), "ana", null);
        ApiClient attacker = clients.create().bootstrap();
        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            lastStatus = attacker.post("/api/v1/auth/login", Map.of("username", "ana", "password", "chute " + i + " xxxxxx"))
                    .getResponse().getStatus();
        }
        assertThat(lastStatus).isEqualTo(429);
        var limited = attacker.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD));
        assertThat(limited).hasStatus(429);
        assertThat(attacker.json(limited).get("retryAfterSeconds").asLong()).isPositive();
    }

    @Test
    @DisplayName("logout encerra a sessão")
    void shouldLogout() {
        ApiClient client = clients.create();
        Accounts.register(client, "ana", null);
        assertThat(client.post("/api/v1/auth/logout", null)).hasStatus(204);
        assertThat(client.get("/api/v1/me")).hasStatus(401);
    }

    @Test
    @DisplayName("TOTP: depois de confirmado, o login exige o código do app")
    void shouldRequireTotpAfterEnrollment() {
        ApiClient client = clients.create();
        Accounts.register(client, "ana", null);
        JsonNode enrollment = client.json(client.post("/api/v1/account/totp", null));
        String secret = enrollment.get("secret").asString();
        assertThat(enrollment.get("otpauthUri").asString()).startsWith("otpauth://totp/Papinho:%40ana?secret=");
        assertThat(client.post("/api/v1/account/totp/confirm", Map.of("code", "000000"))).hasStatus(400);
        assertThat(client.post("/api/v1/account/totp/confirm", Map.of("code", currentCode(secret)))).hasStatus(204);

        ApiClient other = clients.create().bootstrap();
        var missing = other.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD));
        assertThat(missing).hasStatus(401);
        assertThat(other.json(missing).get("totpRequired").asBoolean()).isTrue();
        assertThat(other.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD,
                "totpCode", currentCode(secret)))).hasStatusOk();
    }

    @Test
    @DisplayName("código de recuperação redefine a senha, desliga o TOTP, encerra sessões e não vale duas vezes")
    void shouldRecoverWithRecoveryCode() {
        ApiClient client = clients.create();
        JsonNode registration = Accounts.register(client, "ana", null);
        String code = registration.get("recoveryCodes").get(0).asString();
        JsonNode enrollment = client.json(client.post("/api/v1/account/totp", null));
        client.post("/api/v1/account/totp/confirm", Map.of("code", currentCode(enrollment.get("secret").asString())));

        ApiClient lost = clients.create().bootstrap();
        assertThat(lost.post("/api/v1/auth/recover", Map.of("username", "ana", "recoveryCode", code.toUpperCase(),
                "newPassword", "outra senha bem longa"))).hasStatus(204);
        assertThat(client.get("/api/v1/me")).as("sessão antiga encerrada").hasStatus(401);
        assertThat(lost.post("/api/v1/auth/login", Map.of("username", "ana", "password", "outra senha bem longa")))
                .hasStatusOk();
        assertThat(lost.json(lost.get("/api/v1/me")).get("account").get("remainingRecoveryCodes").asInt())
                .isEqualTo(9);
        assertThat(lost.json(lost.get("/api/v1/me")).get("account").get("totpEnabled").asBoolean()).isFalse();
        assertThat(clients.create().bootstrap().post("/api/v1/auth/recover", Map.of("username", "ana",
                "recoveryCode", code, "newPassword", "terceira senha longa"))).hasStatus(400);
    }

    @Test
    @DisplayName("troca de @usuario respeita o cooldown e reserva o nome antigo por 30 dias só para o dono")
    void shouldReserveOldUsername() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        ana.put("/api/v1/admin/settings", settings("OPEN", 30));

        assertThat(ana.put("/api/v1/account/username", Map.of("username", "ana.nova"))).hasStatusOk();
        var cooldown = ana.put("/api/v1/account/username", Map.of("username", "ana.outra"));
        assertThat(cooldown).hasStatus(409);
        assertThat(ana.json(cooldown).get("type").asString()).isEqualTo("urn:papinho:problem:username-cooldown");

        ApiClient impostor = clients.create().bootstrap();
        assertThat(register(impostor, "ana")).as("nome antigo reservado").hasStatus(409);

        ana.put("/api/v1/admin/settings", settings("OPEN", 0));
        assertThat(ana.put("/api/v1/account/username", Map.of("username", "ana"))).as("dono retoma").hasStatusOk();

        jdbc.update("update username_reservations set reserved_until = ?", java.sql.Timestamp.from(Instant.now()
                .minusSeconds(60)));
        assertThat(register(impostor, "ana.nova")).as("reserva vencida libera o nome").hasStatus(201);
    }

    @Test
    @DisplayName("sessões: lista com a atual marcada, revoga uma e encerra as outras")
    void shouldManageSessions() {
        ApiClient laptop = clients.create();
        Accounts.register(laptop, "ana", null);
        ApiClient phone = clients.create().bootstrap();
        phone.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD));
        ApiClient tablet = clients.create().bootstrap();
        tablet.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD));

        JsonNode sessions = laptop.json(laptop.get("/api/v1/sessions"));
        assertThat(sessions).hasSize(3);
        assertThat(sessions.valueStream().filter(s -> s.get("current").asBoolean()).count()).isEqualTo(1);
        assertThat(sessions.get(0).get("approximateIp").asString()).endsWith(".0/24");

        String phoneSession = phone.json(phone.get("/api/v1/sessions")).valueStream()
                .filter(s -> s.get("current").asBoolean()).findFirst().orElseThrow().get("id").asString();
        assertThat(laptop.delete("/api/v1/sessions/" + phoneSession, null)).hasStatus(204);
        assertThat(phone.get("/api/v1/me")).hasStatus(401);
        assertThat(laptop.delete("/api/v1/sessions/naoexiste", null)).hasStatus(404);

        assertThat(laptop.json(laptop.delete("/api/v1/sessions", null)).get("revoked").asInt()).isEqualTo(1);
        assertThat(tablet.get("/api/v1/me")).hasStatus(401);
        assertThat(laptop.get("/api/v1/me")).hasStatusOk();
    }

    @Test
    @DisplayName("admin desativa usuário: sessões caem e o login falha com a mensagem uniforme")
    void shouldDisableUser() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        ApiClient bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(admin)));

        assertThat(bruno.get("/api/v1/admin/users")).hasStatus(403);
        assertThat(admin.post("/api/v1/admin/users/" + brunoId + "/disable", null)).hasStatus(204);
        assertThat(bruno.get("/api/v1/me")).hasStatus(401);
        ApiClient again = clients.create().bootstrap();
        assertThat(again.post("/api/v1/auth/login", Map.of("username", "bruno", "password", Accounts.PASSWORD)))
                .hasStatus(401);

        String temporary = admin.json(admin.post("/api/v1/admin/users/" + brunoId + "/reset-password", null))
                .get("temporaryPassword").asString();
        admin.post("/api/v1/admin/users/" + brunoId + "/enable", null);
        assertThat(again.post("/api/v1/auth/login", Map.of("username", "bruno", "password", temporary)))
                .hasStatusOk();
        assertThat(admin.json(admin.get("/api/v1/admin/users?query=bru")).get("items")).hasSize(1);
    }

    private static Map<String, Object> settings(String mode, int cooldownDays) {
        return Map.of("registrationMode", mode, "usernameChangeCooldownDays", cooldownDays,
                "messageEditWindowMinutes", 15, "messageDeleteWindowHours", 48, "userQuotaMegabytes", 5120,
                "breachedPasswordCheck", false);
    }

    private static org.springframework.test.web.servlet.assertj.MvcTestResult register(ApiClient client,
            String username) {
        return client.post("/api/v1/auth/register", Map.of("username", username, "displayName", "X",
                "password", Accounts.PASSWORD));
    }

    private static String currentCode(String secret) {
        return Totp.generate(secret, Instant.now().getEpochSecond() / 30);
    }
}
