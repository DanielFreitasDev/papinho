package io.github.labdev2025cmd.papinho.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.labdev2025cmd.papinho.shared.security.RateLimiter;
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

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Test
    @DisplayName("primeiro cadastro dispensa convite, vira admin e abre sessão __Host-SESSION")
    void shouldMakeFirstUserAdmin() {
        ApiClient client = clients.create().bootstrap();
        assertThat(client.json(client.get("/api/v1/instance")).get("setupRequired").asBoolean()).isTrue();
        var anonymous = client.get("/api/v1/auth/session");
        assertThat(anonymous).hasStatusOk();
        assertThat(client.json(anonymous).get("me").isNull()).isTrue();

        JsonNode body = Accounts.register(client, "@Ana.Silva", null);

        JsonNode account = body.get("me").get("account");
        assertThat(account.get("username").asString()).isEqualTo("ana.silva");
        assertThat(account.get("admin").asBoolean()).isTrue();
        assertThat(body.get("recoveryCodes")).hasSize(10);
        assertThat(body.get("recoveryCodes").get(0).asString()).matches("^[a-z2-9]{5}-[a-z2-9]{5}$");
        assertThat(body.get("me").get("profile").get("displayName").asString()).isEqualTo("Pessoa @Ana.Silva");
        assertThat(client.cookie("__Host-SESSION")).isNotBlank();
        assertThat(client.get("/api/v1/me")).hasStatusOk();
        assertThat(client.json(client.get("/api/v1/auth/session")).get("me").get("account").get("username")
                .asString()).isEqualTo("ana.silva");
        assertThat(jdbc.queryForObject("select id::text from user_accounts", String.class)).satisfies(
                id -> assertThat(java.util.UUID.fromString(id).version()).isEqualTo(7));
    }

    @Test
    @DisplayName("primeiro cadastro espera a trava do primeiro usuário: dois ao mesmo tempo não viram admin (ID-08)")
    void shouldSerializeFirstRegistration() throws Exception {
        ApiClient client = clients.create().bootstrap();
        try (Connection holder = dataSource.getConnection(); Statement statement = holder.createStatement()) {
            statement.execute("select pg_advisory_lock(" + IdentityService.REGISTRATION_LOCK + ")");
            CompletableFuture<JsonNode> first = CompletableFuture.supplyAsync(() -> Accounts.register(client, "ana", null));
            try {
                assertThatThrownBy(() -> first.get(500, TimeUnit.MILLISECONDS)).as("espera a trava")
                        .isInstanceOf(TimeoutException.class);
            } finally {
                statement.execute("select pg_advisory_unlock(" + IdentityService.REGISTRATION_LOCK + ")");
            }
            assertThat(first.get(10, TimeUnit.SECONDS).get("me").get("account").get("admin").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("com a instância já em uso, cadastros não esperam uns pelos outros (sem a trava do primeiro usuário)")
    void shouldNotSerializeLaterRegistrations() throws Exception {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        String invite = Accounts.invite(ana);
        try (Connection holder = dataSource.getConnection(); Statement statement = holder.createStatement()) {
            statement.execute("select pg_advisory_lock(" + IdentityService.REGISTRATION_LOCK + ")");
            try {
                JsonNode bruno = assertTimeoutPreemptively(Duration.ofSeconds(10),
                        () -> Accounts.register(clients.create(), "bruno", invite));
                assertThat(bruno.get("me").get("account").get("admin").asBoolean()).isFalse();
            } finally {
                statement.execute("select pg_advisory_unlock(" + IdentityService.REGISTRATION_LOCK + ")");
            }
        }
    }

    @Test
    @DisplayName("passkey nova e TOTP pedem a senha confirmada há pouco; com o TOTP ligado, configurar outro é recusado")
    void shouldRequireRecentPasswordToChangeCredentials() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        assertThat(ana.post("/webauthn/register/options", null)).as("o cadastro vale como senha confirmada")
                .hasStatusOk();

        expirePasswordConfirmation(ana);
        assertProblem(ana.post("/api/v1/account/totp", null), 403, "password-confirmation-required");
        assertProblem(ana.post("/webauthn/register/options", null), 403, "password-confirmation-required");
        assertProblem(ana.post("/webauthn/register", Map.of()), 403, "password-confirmation-required");
        assertThat(ana.post("/api/v1/account/password-confirmation", Map.of("password", "outra senha bem longa")))
                .hasStatus(401);
        assertThat(ana.post("/api/v1/account/password-confirmation", Map.of("password", Accounts.PASSWORD)))
                .hasStatus(204);
        assertThat(ana.post("/webauthn/register/options", null)).hasStatusOk();
        JsonNode enrollment = ana.json(ana.post("/api/v1/account/totp", null));
        assertThat(ana.post("/api/v1/account/totp/confirm",
                Map.of("code", currentCode(enrollment.get("secret").asString())))).hasStatus(204);

        // Com o TOTP ligado, configurar outro app desligaria o atual sem a senha: recusado, o atual continua.
        assertProblem(ana.post("/api/v1/account/totp", null), 409, "totp-already-enabled");
        assertThat(ana.json(ana.get("/api/v1/me")).get("account").get("totpEnabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("opções de login por passkey (cada uma cria uma sessão) e consulta de @usuario têm limite por IP")
    void shouldRateLimitAnonymousLookups() {
        ApiClient anonymous = clients.create().bootstrap();
        for (int i = 0; i < RateLimiter.Policy.PASSKEY_OPTIONS_BY_IP.capacity(); i++) {
            assertThat(anonymous.post("/webauthn/authenticate/options", null)).hasStatusOk();
        }
        MvcTestResult options = anonymous.post("/webauthn/authenticate/options", null);
        assertProblem(options, 429, "rate-limited");
        assertThat(options.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();

        for (int i = 0; i < RateLimiter.Policy.USERNAME_CHECK_BY_IP.capacity(); i++) {
            assertThat(anonymous.get("/api/v1/usernames/availability?username=pessoa" + i)).hasStatusOk();
        }
        assertProblem(anonymous.get("/api/v1/usernames/availability?username=outra"), 429, "rate-limited");
        assertThat(clients.create().get("/api/v1/usernames/availability?username=outra")).as("outro IP segue")
                .hasStatusOk();
    }

    @Test
    @DisplayName("dois admins não tiram um ao outro ao mesmo tempo: a segunda ação vê que quem age já não é admin")
    void shouldNotLeaveTheInstanceWithoutAdmins() throws Exception {
        ApiClient ana = clients.create();
        String anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        ApiClient bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        assertThat(ana.put("/api/v1/admin/users/" + brunoId + "/admin", null).getResponse().getStatus())
                .isLessThan(300);
        try (Connection holder = dataSource.getConnection(); Statement statement = holder.createStatement()) {
            statement.execute("select pg_advisory_lock(" + AdminUserService.ADMIN_LOCK + ")");
            CompletableFuture<MvcTestResult> anaRemovesBruno = CompletableFuture.supplyAsync(
                    () -> ana.delete("/api/v1/admin/users/" + brunoId + "/admin", null));
            try {
                assertThatThrownBy(() -> anaRemovesBruno.get(500, TimeUnit.MILLISECONDS)).as("espera a trava")
                        .isInstanceOf(TimeoutException.class);
                // Enquanto isso, Bruno tira a Ana (a ação dele chegou primeiro e já gravou).
                jdbc.update("update user_accounts set role = 'USER' where id = ?", java.util.UUID.fromString(anaId));
            } finally {
                statement.execute("select pg_advisory_unlock(" + AdminUserService.ADMIN_LOCK + ")");
            }
            assertProblem(anaRemovesBruno.get(10, TimeUnit.SECONDS), 403, "forbidden");
        }
        assertThat(jdbc.queryForObject("select role from user_accounts where id = ?", String.class,
                java.util.UUID.fromString(brunoId))).as("ainda há um admin").isEqualTo("ADMIN");
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

        String expired = Accounts.invite(admin);
        jdbc.update("update invites set expires_at = now() - interval '1 minute' where revoked_at is null "
                + "and uses = 0");
        assertThat(third.json(third.get("/api/v1/invites/status?token=" + expired)).get("usable").asBoolean())
                .isFalse();
        assertThat(third.post("/api/v1/auth/register", Map.of("username", "carla", "displayName", "Carla",
                "password", Accounts.PASSWORD, "inviteToken", expired))).hasStatus(403);
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
    @DisplayName("modo fechado: o convite do admin reabre a porta para quem o recebe; o de outra pessoa não (ID-07)")
    void shouldAcceptOnlyAdminInvitesWhenClosed() {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        ApiClient bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(admin));
        String brunoInvite = Accounts.invite(bruno);
        assertThat(admin.put("/api/v1/admin/settings", settings("CLOSED", 30))).hasStatusOk();
        assertThat(bruno.post("/api/v1/invites", Map.of("validHours", 24, "maxUses", 1))).as("só o admin convida")
                .hasStatus(403);
        String adminInvite = Accounts.invite(admin);

        ApiClient carla = clients.create().bootstrap();
        assertThat(carla.json(carla.get("/api/v1/invites/status?token=" + brunoInvite)).get("usable").asBoolean())
                .as("convite de quem não é admin, com a instância fechada").isFalse();
        var refused = carla.post("/api/v1/auth/register", Map.of("username", "carla", "displayName", "Carla",
                "password", Accounts.PASSWORD, "inviteToken", brunoInvite));
        assertThat(refused).hasStatus(403);
        assertThat(carla.json(refused).get("type").asString()).isEqualTo("urn:papinho:problem:registration-closed");

        assertThat(carla.json(carla.get("/api/v1/invites/status?token=" + adminInvite)).get("usable").asBoolean())
                .isTrue();
        Accounts.register(carla, "carla", adminInvite);
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
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    @DisplayName("cadastros simultâneos com o mesmo @usuario: um entra, os outros recebem 409 e o nome não vai ao log")
    void shouldResolveConcurrentRegistrationsWithSameUsername(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        ApiClient admin = clients.create();
        Accounts.register(admin, "ana", null);
        admin.put("/api/v1/admin/settings", settings("OPEN", 30));
        java.util.List<ApiClient> people = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> clients.create().bootstrap()).toList();
        java.util.List<Integer> statuses;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < people.size(); i++) {
                ApiClient person = people.get(i);
                String name = i % 2 == 0 ? "mesmo.nome0" : "MESMO.NOME0";
                results.add(executor.submit(() -> register(person, name).getResponse().getStatus()));
            }
            statuses = new java.util.ArrayList<>();
            for (var result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
        }
        assertThat(statuses).as("status de cada cadastro").containsOnly(201, 409).containsOnlyOnce(201);
        assertThat(output.getAll()).as("log").doesNotContainIgnoringCase("mesmo.nome0");
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
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo(
                Long.toString(attacker.json(limited).get("retryAfterSeconds").asLong()));
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
    @DisplayName("TOTP: depois de confirmado, o login exige o código do app, que não vale duas vezes")
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
        String code = currentCode(secret);
        assertThat(other.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD,
                "totpCode", code))).hasStatusOk();

        // Quem viu o código (por cima do ombro, num site falso) não entra com ele de novo, nem dentro dos 30 s.
        ApiClient replay = clients.create().bootstrap();
        var reused = replay.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD,
                "totpCode", code));
        assertThat(reused).hasStatus(400);
        assertThat(replay.json(reused).get("type").asString()).isEqualTo("urn:papinho:problem:code-invalid");
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

    @Test
    @DisplayName("passkeys: opções de registro exigem sessão; opções de login são públicas; lista começa vazia")
    void shouldExposePasskeyCeremonies() {
        ApiClient anonymous = clients.create().bootstrap();
        // O filtro de opções do Spring roda antes da autorização e recusa o anônimo com 400.
        assertThat(anonymous.post("/webauthn/register/options", null).getResponse().getStatus()).isIn(400, 401);
        var loginOptions = anonymous.post("/webauthn/authenticate/options", null);
        assertThat(loginOptions).hasStatusOk();
        assertThat(anonymous.json(loginOptions).get("rpId").asString()).isEqualTo("localhost");
        assertThat(anonymous.json(loginOptions).get("challenge").asString()).isNotBlank();

        ApiClient ana = clients.create();
        String anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        var options = ana.post("/webauthn/register/options", null);
        assertThat(options).hasStatusOk();
        JsonNode body = ana.json(options);
        assertThat(body.get("rp").get("id").asString()).isEqualTo("localhost");
        assertThat(body.get("rp").get("name").asString()).isEqualTo("Papinho");
        assertThat(body.get("user").get("name").asString()).isEqualTo(anaId);
        assertThat(ana.json(ana.get("/api/v1/account/passkeys"))).isEmpty();
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

    private static void assertProblem(MvcTestResult result, int status, String slug) {
        assertThat(result).as(ApiClient.text(result)).hasStatus(status);
        assertThat(ApiClient.text(result)).contains("urn:papinho:problem:" + slug);
    }

    /** A senha confirmada ficou para trás: o instante guardado na sessão passa da validade. */
    private void expirePasswordConfirmation(ApiClient client) {
        String id = new String(Base64.getDecoder().decode(client.cookie("__Host-SESSION")), StandardCharsets.UTF_8);
        backdate(sessionRepository, id);
    }

    private static <S extends Session> void backdate(SessionRepository<S> repository, String id) {
        S session = repository.findById(id);
        session.setAttribute(RecentPasswordConfirmation.ATTRIBUTE,
                Instant.now().minus(RecentPasswordConfirmation.VALIDITY).minusSeconds(60).getEpochSecond());
        repository.save(session);
    }
}
