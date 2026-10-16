package io.github.labdev2025cmd.papinho.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;

/** E-mail opcional de recuperação (ID-05) contra um servidor SMTP de verdade em memória (GreenMail). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class EmailRecoveryIT {

    private static final GreenMail SMTP = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
    private static final Pattern LINK = Pattern.compile("https://papinho\\.example(/[a-z-]+)#token=([A-Za-z0-9_-]+)");
    private static final Pattern LOG_LINE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T");
    private static final String NEW_PASSWORD = "outra senha bem segura 456";

    static {
        SMTP.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("app.mail.enabled", () -> "true");
        registry.add("app.mail.from", () -> "Papinho <papinho@papinho.example>");
        registry.add("app.mail.public-url", () -> "https://papinho.example/");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP.getSmtp().getPort());
    }

    @AfterAll
    static void stop() {
        SMTP.stop();
    }

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearMailbox() throws Exception {
        SMTP.purgeEmailFromAllMailboxes();
    }

    @Test
    @DisplayName("instância anuncia o e-mail; endereço só vale depois de confirmado pelo link")
    void shouldVerifyEmailBeforeUsingIt(CapturedOutput output) throws Exception {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        assertThat(ana.json(ana.get("/api/v1/instance")).get("emailRecovery").asBoolean()).isTrue();

        assertThat(ana.put("/api/v1/account/email", Map.of("email", "ana@exemplo.com", "password", "errada errada")))
                .hasStatus(401);
        assertThat(ana.put("/api/v1/account/email", Map.of("email", "não é e-mail", "password", Accounts.PASSWORD)))
                .hasStatus(400);
        var changed = ana.put("/api/v1/account/email", Map.of("email", "ana@exemplo.com", "password",
                Accounts.PASSWORD));
        assertThat(changed).hasStatusOk();
        assertThat(ana.json(changed).get("email").asString()).isEqualTo("ana@exemplo.com");
        assertThat(ana.json(changed).get("emailVerified").asBoolean()).isFalse();

        Message verify = awaitMessage("ana@exemplo.com");
        assertThat(verify.getSubject()).contains("confirme");
        Link link = link(verify);
        assertThat(link.path()).isEqualTo("/verify-email");
        assertThat(jdbc.queryForObject("select count(*) from email_tokens where token_hash = ?", Integer.class,
                link.token())).as("o token em texto puro nunca vai ao banco").isZero();

        // Antes de confirmar, o endereço não recebe link de redefinição.
        ApiClient stranger = clients.create().bootstrap();
        assertThat(stranger.post("/api/v1/auth/password-reset", Map.of("username", "ana"))).hasStatus(202);
        Thread.sleep(300);
        assertThat(SMTP.getReceivedMessagesForDomain("ana@exemplo.com")).hasSize(1);

        assertThat(stranger.post("/api/v1/auth/email/verify", Map.of("token", "inventado"))).hasStatus(400);
        assertThat(stranger.post("/api/v1/auth/email/verify", Map.of("token", link.token()))).hasStatus(204);
        assertThat(stranger.post("/api/v1/auth/email/verify", Map.of("token", link.token())))
                .as("uso único").hasStatus(400);
        assertThat(ana.json(ana.get("/api/v1/me")).get("account").get("emailVerified").asBoolean()).isTrue();

        assertThat(serverLogs(output)).doesNotContain("ana@exemplo.com").doesNotContain(link.token());
    }

    @Test
    @DisplayName("redefinição por e-mail: mesma resposta para qualquer @usuario, link de uso único, sessões caem")
    void shouldResetPasswordByEmail(CapturedOutput output) throws Exception {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        verifiedEmail(ana, "ana@exemplo.com");
        ApiClient other = clients.create().bootstrap();
        assertThat(other.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD)))
                .hasStatusOk();

        ApiClient stranger = clients.create().bootstrap();
        var unknown = stranger.post("/api/v1/auth/password-reset", Map.of("username", "ninguem"));
        var known = stranger.post("/api/v1/auth/password-reset", Map.of("username", "@ANA"));
        assertThat(unknown).hasStatus(202);
        assertThat(known).hasStatus(202);
        assertThat(ApiClient.text(unknown)).isEqualTo(ApiClient.text(known));

        Message reset = awaitMessage("ana@exemplo.com");
        assertThat(reset.getSubject()).contains("redefinir");
        assertThat(reset.getFrom()[0]).isEqualTo(new InternetAddress("Papinho <papinho@papinho.example>"));
        Link link = link(reset);
        assertThat(link.path()).isEqualTo("/reset-password");

        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", link.token(),
                "newPassword", "curta"))).hasStatus(400);
        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", "inventado",
                "newPassword", NEW_PASSWORD))).hasStatus(400);
        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", link.token(),
                "newPassword", NEW_PASSWORD))).hasStatus(204);
        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", link.token(),
                "newPassword", NEW_PASSWORD))).as("uso único").hasStatus(400);

        assertThat(ana.get("/api/v1/me")).as("todas as sessões caem").hasStatus(401);
        assertThat(other.get("/api/v1/me")).hasStatus(401);
        ApiClient again = clients.create().bootstrap();
        assertThat(again.post("/api/v1/auth/login", Map.of("username", "ana", "password", Accounts.PASSWORD)))
                .hasStatus(401);
        assertThat(again.post("/api/v1/auth/login", Map.of("username", "ana", "password", NEW_PASSWORD)))
                .hasStatusOk();

        assertThat(serverLogs(output)).doesNotContain("ana@exemplo.com").doesNotContain(link.token());
    }

    @Test
    @DisplayName("link vencido não vale; trocar o e-mail invalida os links e avisa o endereço antigo")
    void shouldInvalidateLinks() throws Exception {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        verifiedEmail(ana, "ana@exemplo.com");
        ApiClient stranger = clients.create().bootstrap();

        stranger.post("/api/v1/auth/password-reset", Map.of("username", "ana"));
        Link expired = link(awaitMessage("ana@exemplo.com"));
        jdbc.update("update email_tokens set expires_at = now() - interval '1 second'");
        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", expired.token(),
                "newPassword", NEW_PASSWORD))).hasStatus(400);

        SMTP.purgeEmailFromAllMailboxes();
        stranger.post("/api/v1/auth/password-reset", Map.of("username", "ana"));
        Link pending = link(awaitMessage("ana@exemplo.com"));
        assertThat(ana.put("/api/v1/account/email", Map.of("email", "nova@exemplo.com", "password",
                Accounts.PASSWORD))).hasStatusOk();
        await().atMost(Duration.ofSeconds(10)).until(() -> SMTP.getReceivedMessages().length == 3);
        List<String> subjects = Arrays.stream(SMTP.getReceivedMessages()).map(EmailRecoveryIT::subject).toList();
        assertThat(subjects).anyMatch(subject -> subject.contains("mudou"));
        assertThat(stranger.post("/api/v1/auth/password-reset/confirm", Map.of("token", pending.token(),
                "newPassword", NEW_PASSWORD))).as("link do endereço antigo").hasStatus(400);

        var removed = ana.delete("/api/v1/account/email", Map.of("password", Accounts.PASSWORD));
        assertThat(removed).hasStatusOk();
        assertThat(ana.json(removed).get("email").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from email_tokens", Integer.class)).isZero();
    }

    @Test
    @DisplayName("limite de pedidos por conta impede encher a caixa de alguém")
    void shouldRateLimitResetRequests() throws Exception {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        verifiedEmail(ana, "ana@exemplo.com");
        for (int i = 0; i < 3; i++) {
            assertThat(clients.create().bootstrap().post("/api/v1/auth/password-reset", Map.of("username", "ana")))
                    .hasStatus(202);
        }
        var limited = clients.create().bootstrap().post("/api/v1/auth/password-reset", Map.of("username", "ana"));
        assertThat(limited).hasStatus(429);
    }

    private void verifiedEmail(ApiClient client, String email) throws Exception {
        assertThat(client.put("/api/v1/account/email", Map.of("email", email, "password", Accounts.PASSWORD)))
                .hasStatusOk();
        Link link = link(awaitMessage(email));
        assertThat(client.post("/api/v1/auth/email/verify", Map.of("token", link.token()))).hasStatus(204);
        SMTP.purgeEmailFromAllMailboxes();
    }

    /** Linhas de log do servidor, sem o próprio GreenMail nem a impressão das requisições do MockMvc. */
    private static String serverLogs(CapturedOutput output) {
        return output.getAll().lines().filter(line -> LOG_LINE.matcher(line).lookingAt())
                .filter(line -> !line.contains("c.icegreen.greenmail")).collect(Collectors.joining("\n"));
    }

    private static Message awaitMessage(String to) {
        await().atMost(Duration.ofSeconds(10)).until(() -> SMTP.getReceivedMessagesForDomain(to).length > 0);
        Message[] messages = SMTP.getReceivedMessagesForDomain(to);
        return messages[messages.length - 1];
    }

    private static Link link(Message message) throws Exception {
        Matcher matcher = LINK.matcher(message.getContent().toString());
        assertThat(matcher.find()).as("link no corpo").isTrue();
        return new Link(matcher.group(1), matcher.group(2));
    }

    private static String subject(Message message) {
        try {
            return message.getSubject();
        } catch (jakarta.mail.MessagingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Link(String path, String token) {
    }
}
