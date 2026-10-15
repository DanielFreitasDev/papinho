package io.github.labdev2025cmd.papinho.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import tools.jackson.databind.JsonNode;

/** Contatos por {@code @usuario}, link e QR, apelidos e bloqueio (CT-01, CT-03). */
@IntegrationTest
class ContactIT {

    @Autowired
    private ApiClients clients;

    @Test
    @DisplayName("adiciona contato por @usuario com apelido local e lista pelo apelido")
    void shouldAddContactWithNickname() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        ApiClient bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));

        var added = ana.post("/api/v1/contacts", Map.of("username", "@BRUNO", "nickname", "Bruninho"));
        assertThat(added).hasStatus(201);
        JsonNode list = ana.json(ana.get("/api/v1/contacts"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("label").asString()).isEqualTo("Bruninho");
        assertThat(list.get(0).get("displayName").asString()).isEqualTo("Pessoa bruno");

        assertThat(ana.post("/api/v1/contacts", Map.of("username", "ana"))).hasStatus(400);
        assertThat(ana.post("/api/v1/contacts", Map.of("username", "ninguem"))).hasStatus(404);
        assertThat(bruno.json(bruno.get("/api/v1/contacts"))).as("apelido e lista são locais").isEmpty();

        assertThat(ana.json(ana.patch("/api/v1/contacts/" + brunoId, Map.of("nickname", " "))).get("nickname")
                .isNull()).isTrue();
        assertThat(ana.delete("/api/v1/contacts/" + brunoId, null)).hasStatus(204);
        assertThat(ana.json(ana.get("/api/v1/contacts"))).isEmpty();
    }

    @Test
    @DisplayName("link próprio e cartão por @usuario para adicionar via QR Code")
    void shouldExposeOwnLinkAndCard() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        ApiClient bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));

        JsonNode link = bruno.json(bruno.get("/api/v1/contacts/my-link"));
        assertThat(link.get("url").asString()).endsWith("/add/bruno");
        assertThat(link.get("qrSvg").asString()).startsWith("<svg");

        JsonNode card = ana.json(ana.get("/api/v1/people/bruno"));
        assertThat(card.get("username").asString()).isEqualTo("bruno");
        assertThat(card.get("contact").asBoolean()).isFalse();
        assertThat(ana.get("/api/v1/people/ninguem")).hasStatus(404);
    }

    @Test
    @DisplayName("bloqueado não vê foto nem recado de quem bloqueou; desbloquear devolve o acesso")
    void shouldHidePhotoAndAboutFromBlocked() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        ApiClient bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        String anaId = ana.json(ana.get("/api/v1/me")).get("account").get("id").asString();
        bruno.put("/api/v1/profile", Map.of("displayName", "Bruno", "about", "Disponível"));
        bruno.multipart(HttpMethod.PUT, "/api/v1/profile/photo",
                new MockMultipartFile("photo", "foto.jpg", "image/jpeg", Images.jpegWithExif(800, 600)));

        assertThat(ana.get("/api/v1/users/" + brunoId + "/photo")).hasStatusOk();
        assertThat(ana.json(ana.get("/api/v1/people/bruno")).get("about").asString()).isEqualTo("Disponível");

        assertThat(bruno.put("/api/v1/blocks/" + anaId, null)).hasStatus(204);
        assertThat(bruno.put("/api/v1/blocks/" + anaId, null)).as("idempotente").hasStatus(204);
        assertThat(ana.get("/api/v1/users/" + brunoId + "/photo")).hasStatus(404);
        JsonNode card = ana.json(ana.get("/api/v1/people/bruno"));
        assertThat(card.get("about").isNull()).isTrue();
        assertThat(card.get("hasPhoto").asBoolean()).isFalse();
        assertThat(bruno.json(bruno.get("/api/v1/blocks")).get(0).get("username").asString()).isEqualTo("ana");
        assertThat(bruno.json(bruno.get("/api/v1/people/ana")).get("blocked").asBoolean()).isTrue();

        assertThat(bruno.delete("/api/v1/blocks/" + anaId, null)).hasStatus(204);
        assertThat(ana.get("/api/v1/users/" + brunoId + "/photo")).hasStatusOk();
        assertThat(bruno.put("/api/v1/blocks/" + brunoId, null)).hasStatus(400);
    }
}
