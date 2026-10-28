package io.github.labdev2025cmd.papinho.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/**
 * Busca de pessoas ao adicionar contato ({@code GET /api/v1/people?q=}): pelo nome, sem diferença de maiúsculas e
 * acentos, ou pelo começo do {@code @usuario}, com bloqueio, contas desativadas e "Aparecer na busca por nome"
 * aplicados no servidor.
 */
@IntegrationTest
class PeopleSearchIT {

    @Autowired
    private ApiClients clients;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient ana;
    private String anaId;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        assertThat(ana.put("/api/v1/profile", Map.of("displayName", "Ana Admin"))).hasStatusOk();
    }

    @Test
    @DisplayName("cada palavra da busca começa uma palavra do nome, sem diferença de maiúsculas e acentos")
    void shouldMatchWordStartsIgnoringCaseAndAccents() {
        person("daniel.f", "Daniel Freitas");
        person("joao.s", "João Silva");
        person("carla", "Carla Danielle");
        person("jeanluc", "Jean-Luc Picard");

        assertThat(usernames(ana, "dan fre")).as("todas as palavras precisam casar").containsExactly("daniel.f");
        assertThat(usernames(ana, "fre dan")).containsExactly("daniel.f");
        assertThat(usernames(ana, "frei")).as("começo do sobrenome").containsExactly("daniel.f");
        assertThat(usernames(ana, "FREITAS")).containsExactly("daniel.f");
        assertThat(usernames(ana, "joao")).as("sem acento acha com acento").containsExactly("joao.s");
        assertThat(usernames(ana, "JOÃO")).containsExactly("joao.s");
        assertThat(usernames(ana, "silva")).containsExactly("joao.s");
        assertThat(usernames(ana, "niel")).as("meio de palavra não casa").isEmpty();
        assertThat(usernames(ana, "dan")).as("quem começa pela busca vem antes")
                .containsExactly("daniel.f", "carla");
        assertThat(usernames(ana, "jean-luc")).as("pontuação separa palavras").containsExactly("jeanluc");
        assertThat(usernames(ana, "luc")).containsExactly("jeanluc");
    }

    @Test
    @DisplayName("o começo do @usuario acha, com ou sem @ e em maiúsculas; o meio não")
    void shouldMatchUsernamePrefix() {
        person("zeca.dev", "José Carlos");

        assertThat(usernames(ana, "zeca")).containsExactly("zeca.dev");
        assertThat(usernames(ana, "@zeca")).containsExactly("zeca.dev");
        assertThat(usernames(ana, "@ZeCa.D")).containsExactly("zeca.dev");
        assertThat(usernames(ana, " @zeca.dev ")).containsExactly("zeca.dev");
        assertThat(usernames(ana, "eca")).as("meio do @usuario não casa").isEmpty();
        assertThat(usernames(ana, "zeca x")).as("o @usuario é uma palavra só").isEmpty();
    }

    @Test
    @DisplayName("quem busca, conta desativada e bloqueio nas duas direções ficam de fora")
    void shouldLeaveOutSelfDeactivatedAndBlocked() {
        Person clara = person("anaclara", "Ana Clara");
        Person disabled = person("anades", "Ana Desativada");
        Person blocked = person("anablock", "Ana Bloqueada");
        Person blocker = person("anablocker", "Ana Que Bloqueou");
        person("analivre", "Ana Livre");
        assertThat(ana.post("/api/v1/admin/users/" + disabled.id() + "/disable", null)).hasStatus(204);
        assertThat(ana.put("/api/v1/blocks/" + blocked.id(), null)).hasStatus(204);
        assertThat(blocker.client().put("/api/v1/blocks/" + anaId, null)).hasStatus(204);

        assertThat(usernames(ana, "ana")).as("sem a própria Ana, a desativada e os bloqueios")
                .containsExactly("anaclara", "analivre");
        assertThat(usernames(blocked.client(), "ana")).as("quem foi bloqueado não acha quem bloqueou")
                .doesNotContain("ana");
        assertThat(usernames(blocker.client(), "ana admin")).as("quem bloqueou não acha a bloqueada").isEmpty();
        assertThat(usernames(clara.client(), "ana")).as("para os outros, os dois aparecem")
                .containsExactlyInAnyOrder("ana", "anablock", "anablocker", "analivre");

        assertThat(ana.delete("/api/v1/blocks/" + blocked.id(), null)).hasStatus(204);
        assertThat(usernames(ana, "ana")).as("desbloquear devolve").contains("anablock");
    }

    @Test
    @DisplayName("quem desliga a busca por nome some das sugestões, menos pelo @usuario exato e para os contatos")
    void shouldHideOptedOutExceptExactUsernameAndContacts() {
        Person bruno = person("bruno.r", "Bruno Reservado");
        Person carla = person("carla", "Carla Contato");
        assertThat(bruno.client().patch("/api/v1/privacy", Map.of("nameSearch", false))).hasStatusOk();

        assertThat(usernames(ana, "bruno")).as("nem pelo nome nem pelo começo do @usuario").isEmpty();
        assertThat(usernames(ana, "reservado")).isEmpty();
        assertThat(usernames(ana, "bruno.r")).as("o @usuario exato, como no link").containsExactly("bruno.r");
        assertThat(usernames(ana, "@BRUNO.R")).containsExactly("bruno.r");
        JsonNode exact = search(ana, "@bruno.r").get(0);
        assertThat(exact.get("contact").asBoolean()).isFalse();

        assertThat(carla.client().post("/api/v1/contacts", Map.of("username", "bruno.r"))).hasStatus(201);
        JsonNode found = search(carla.client(), "reservado");
        assertThat(found).as("quem já tem nos contatos acha pelo nome").hasSize(1);
        assertThat(found.get(0).get("username").asString()).isEqualTo("bruno.r");
        assertThat(found.get(0).get("contact").asBoolean()).isTrue();
        assertThat(usernames(ana, "bruno")).as("o contato é da Carla, não da Ana").isEmpty();

        assertThat(bruno.client().patch("/api/v1/privacy", Map.of("nameSearch", true))).hasStatusOk();
        assertThat(usernames(ana, "bruno")).containsExactly("bruno.r");
    }

    @Test
    @DisplayName("menos de 2 caracteres devolve vazio; mais de 64 é 400 em Problem Details")
    void shouldIgnoreShortQueriesAndRefuseLongOnes() {
        person("abel", "Abel Antunes");

        assertThat(usernames(ana, "")).isEmpty();
        assertThat(usernames(ana, "a")).isEmpty();
        assertThat(usernames(ana, "@a")).as("o @ não conta").isEmpty();
        assertThat(usernames(ana, "  a  ")).as("nem os espaços").isEmpty();
        assertThat(usernames(ana, " @ ")).isEmpty();
        assertThat(usernames(ana, "ab")).containsExactly("abel");
        assertThat(usernames(ana, "a".repeat(64))).as("64 é o limite").isEmpty();

        MvcTestResult tooLong = get(ana, "a".repeat(65));
        assertThat(tooLong).hasStatus(400).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = ana.json(tooLong);
        assertThat(problem.get("type").asString()).isEqualTo("urn:papinho:problem:search-too-long");
        assertThat(problem.get("title").asString()).isEqualTo("Busca longa demais");
        assertThat(problem.get("detail").asString()).isEqualTo("Busque com até 64 caracteres.");
        assertThat(usernames(ana, "@" + "a".repeat(64))).as("o @ não conta").isEmpty();
    }

    @Test
    @DisplayName("ordem: @usuario exato, nomes que começam pela busca e o resto, por nome e @usuario")
    void shouldRankExactUsernameFirst() {
        person("carol", "Carolina Lia");
        person("lia.m", "Mirela Prado");
        person("liane", "Liane Costa");
        person("bia", "Lia Beatriz");
        person("lia", "Zuleica Souza");

        assertThat(usernames(ana, "lia")).containsExactly("lia", "bia", "liane", "carol", "lia.m");
    }

    @Test
    @DisplayName("%, _ e as formas largas que o unaccent troca por eles não são curingas")
    void shouldTreatLikeWildcardsAsText() {
        person("daniel.f", "Daniel Freitas");
        person("dan_x", "Xavier");

        assertThat(usernames(ana, "%%")).isEmpty();
        assertThat(usernames(ana, "__")).isEmpty();
        assertThat(usernames(ana, "_%")).isEmpty();
        assertThat(usernames(ana, "％％")).as("％ vira % no unaccent").isEmpty();
        assertThat(usernames(ana, "d_n")).as("_ não vale qualquer letra no @usuario").isEmpty();
        assertThat(usernames(ana, "dan%")).as("% não vale qualquer resto no @usuario: só o nome casa")
                .containsExactly("daniel.f");
        assertThat(usernames(ana, "dan_")).as("_ é letra do @usuario").contains("dan_x");
    }

    @Test
    @DisplayName("cada sugestão é o mesmo cartão de /people/{usuario}, com foto e recado filtrados (PV-04)")
    void shouldReturnTheSameCardAsLookup() {
        Person eva = person("eva", "Eva Lima");
        assertThat(eva.client().put("/api/v1/profile", Map.of("displayName", "Eva Lima", "about", "Disponível")))
                .hasStatusOk();
        assertThat(eva.client().multipart(HttpMethod.PUT, "/api/v1/profile/photo",
                new MockMultipartFile("photo", "foto.jpg", "image/jpeg", Images.jpegWithExif(64, 64)))).hasStatusOk();

        JsonNode visible = search(ana, "eva").get(0);
        assertThat(visible).isEqualTo(ana.json(ana.get("/api/v1/people/eva")));
        assertThat(visible.get("about").asString()).isEqualTo("Disponível");
        assertThat(visible.get("hasPhoto").asBoolean()).isTrue();
        assertThat(visible.get("self").asBoolean()).isFalse();
        assertThat(visible.get("blocked").asBoolean()).isFalse();

        assertThat(eva.client().patch("/api/v1/privacy", Map.of("photo", "NOBODY", "about", "NOBODY"))).hasStatusOk();
        JsonNode hidden = search(ana, "eva").get(0);
        assertThat(hidden).isEqualTo(ana.json(ana.get("/api/v1/people/eva")));
        assertThat(hidden.get("about").isNull()).isTrue();
        assertThat(hidden.get("hasPhoto").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("no máximo 20, ainda completos depois de tirar quem não pode aparecer")
    void shouldFillTheLimitAfterFiltering() {
        UUID viewer = UUID.fromString(anaId);
        List<UUID> people = IntStream.rangeClosed(1, 34)
                .mapToObj(n -> insertPerson("pessoa%02d".formatted(n), "Pessoa %02d".formatted(n))).toList();
        people.subList(0, 5).forEach(id -> jdbc.update(
                "insert into privacy_settings (user_id, name_search, updated_at) values (?, false, now())", id));
        people.subList(5, 8).forEach(id -> jdbc.update(
                "insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", viewer, id));
        people.subList(8, 10).forEach(id -> jdbc.update("update user_accounts set enabled = false where id = ?", id));

        List<String> found = usernames(ana, "pessoa");

        assertThat(found).as("1 a 5 sem busca por nome, 6 a 8 bloqueadas, 9 e 10 desativadas").hasSize(20);
        assertThat(found.getFirst()).isEqualTo("pessoa11");
        assertThat(found.getLast()).isEqualTo("pessoa30");
    }

    @Test
    @DisplayName("limite por pessoa: 120 buscas por minuto, depois 429 com Retry-After; busca curta e outros seguem")
    void shouldRateLimitPerUser() {
        Person bruno = person("bruno", "Bruno");
        // O balde reabastece 2 fichas por segundo durante o laço: conta quantas passam até o primeiro 429.
        int accepted = 0;
        MvcTestResult limited = get(ana, "bruno");
        while (limited.getResponse().getStatus() == 200 && accepted < 300) {
            accepted++;
            limited = get(ana, "bruno");
        }
        assertThat(accepted).isBetween(120, 150);
        assertThat(limited).hasStatus(429);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(ana.json(limited).get("type").asString()).isEqualTo("urn:papinho:problem:rate-limited");
        assertThat(get(ana, "b")).as("busca curta volta vazia sem passar pelo limite").hasStatusOk();
        assertThat(usernames(bruno.client(), "ana")).as("o limite é de cada um").containsExactly("ana");
    }

    @Test
    @DisplayName("sem sessão não busca")
    void shouldRequireSession() {
        ApiClient anonymous = clients.create().bootstrap();
        assertThat(get(anonymous, "ana")).hasStatus(401);
    }

    /** Cadastra (por convite da Ana) com o nome de exibição pedido. */
    private Person person(String username, String displayName) {
        ApiClient client = clients.create();
        String id = Accounts.userId(Accounts.register(client, username, Accounts.invite(ana)));
        assertThat(client.put("/api/v1/profile", Map.of("displayName", displayName))).hasStatusOk();
        return new Person(client, id);
    }

    /** Conta e perfil direto no banco, sem o Argon2 do cadastro (para muitas pessoas de uma vez). */
    private UUID insertPerson(String username, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into user_accounts (id, username, password_hash, role, created_at)"
                + " values (?, ?, 'x', 'USER', now())", id, username);
        jdbc.update("insert into profiles (user_id, display_name, updated_at) values (?, ?, now())", id, displayName);
        return id;
    }

    private static List<String> usernames(ApiClient client, String query) {
        return search(client, query).valueStream().map(person -> person.get("username").asString()).toList();
    }

    private static JsonNode search(ApiClient client, String query) {
        MvcTestResult result = get(client, query);
        assertThat(result).as(ApiClient.text(result)).hasStatusOk();
        return client.json(result);
    }

    /** queryParam: o MockMvc codifica a URL de novo, e um "%" ou acento já codificado chegaria como texto. */
    private static MvcTestResult get(ApiClient client, String query) {
        return client.perform(MockMvcRequestBuilders.get("/api/v1/people").queryParam("q", query));
    }

    private record Person(ApiClient client, String id) {
    }
}
