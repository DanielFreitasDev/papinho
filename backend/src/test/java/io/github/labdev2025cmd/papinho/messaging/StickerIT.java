package io.github.labdev2025cmd.papinho.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/** Figurinhas (MS-10): pacotes da instância em WebP, favoritas e mensagem de figurinha. */
@IntegrationTest
class StickerIT {

    /** WebP sem perdas de 1×1 pixel. */
    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

    @Autowired
    private ApiClients clients;

    private ApiClient ana;
    private ApiClient bruno;

    @BeforeEach
    void setUp() {
        ana = clients.create();
        Accounts.register(ana, "ana", null);
        bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
    }

    @Test
    @DisplayName("pacote de quem envia vale para todos; só WebP; favoritas por pessoa; figurinha vira mensagem")
    void shouldShareStickerPacks() {
        String pack = ana.json(ana.post("/api/v1/stickers/packs", Map.of("name", "Gatos"))).get("id").asString();
        String webp = Uploads.upload(ana, WEBP, "gato.webp", "image/webp");
        JsonNode sticker = ana.json(ana.post("/api/v1/stickers/packs/" + pack + "/stickers",
                Map.of("mediaId", webp, "emoji", "🐱")));
        String stickerId = sticker.get("id").asString();
        String png = Uploads.upload(ana, Images.png(16, 16), "nao.png", "image/png");
        assertThat(ana.post("/api/v1/stickers/packs/" + pack + "/stickers", Map.of("mediaId", png)))
                .as("só WebP").hasStatus(400);
        String brunoWebp = Uploads.upload(bruno, WEBP, "dele.webp", "image/webp");
        assertThat(bruno.post("/api/v1/stickers/packs/" + pack + "/stickers", Map.of("mediaId", brunoWebp)))
                .as("só o dono do pacote").hasStatus(404);

        JsonNode packs = bruno.json(bruno.get("/api/v1/stickers/packs"));
        assertThat(packs).hasSize(1);
        assertThat(packs.get(0).get("mine").asBoolean()).isFalse();
        assertThat(packs.get(0).get("stickers").get(0).get("emoji").asString()).isEqualTo("🐱");
        assertThat(bruno.get("/api/v1/media/" + webp + "/content")).as("biblioteca aberta a todos").hasStatusOk();

        assertThat(bruno.put("/api/v1/stickers/" + stickerId + "/favorite", null)).hasStatus(204);
        assertThat(bruno.json(bruno.get("/api/v1/stickers/favorites"))).hasSize(1);
        assertThat(ana.json(ana.get("/api/v1/stickers/favorites"))).isEmpty();

        bruno.post("/api/v1/contacts", Map.of("username", "ana"));
        String conversation = bruno.json(bruno.post("/api/v1/conversations/direct", Map.of("username", "ana")))
                .get("id").asString();
        JsonNode message = bruno.json(bruno.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "", "stickerId", stickerId)));
        assertThat(message.get("sticker").get("mediaId").asString()).isEqualTo(webp);
        assertThat(ana.json(ana.get("/api/v1/conversations/" + conversation)).get("lastMessage").get("sticker")
                .asBoolean()).isTrue();
        assertThat(bruno.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "texto junto", "stickerId", stickerId)))
                .hasStatus(400);

        assertThat(ana.delete("/api/v1/stickers/" + stickerId, null)).hasStatus(204);
        assertThat(bruno.json(bruno.get("/api/v1/stickers/packs")).get(0).get("stickers")).isEmpty();
        JsonNode stillThere = ana.json(ana.get("/api/v1/conversations/" + conversation + "/messages"))
                .get("messages").get(0);
        assertThat(stillThere.get("sticker").get("id").asString()).as("mensagem enviada continua").isEqualTo(stickerId);
        assertThat(bruno.post("/api/v1/conversations/" + conversation + "/messages",
                Map.of("clientMessageId", UUID.randomUUID(), "body", "", "stickerId", stickerId)))
                .as("tirada da biblioteca não sai mais").hasStatus(400);
        assertThat(bruno.delete("/api/v1/stickers/packs/" + pack, null)).hasStatus(404);
        assertThat(ana.delete("/api/v1/stickers/packs/" + pack, null)).hasStatus(204);
        assertThat(ana.json(ana.get("/api/v1/stickers/packs"))).isEmpty();
        assertThat(List.of(pack)).isNotEmpty();
    }

    @Test
    @DisplayName("GIFs ficam desligados por padrão: nada sai do servidor (MS-10)")
    void shouldKeepGifsDisabledByDefault() {
        assertThat(ana.json(ana.get("/api/v1/gifs/status")).get("enabled").asBoolean()).isFalse();
        assertThat(ana.get("/api/v1/gifs?q=gato")).hasStatus(503);
    }
}
