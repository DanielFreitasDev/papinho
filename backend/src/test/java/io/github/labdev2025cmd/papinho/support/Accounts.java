package io.github.labdev2025cmd.papinho.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;

/** Atalhos para criar contas nos testes. */
public final class Accounts {

    public static final String PASSWORD = "senha muito segura 123";

    private Accounts() {
    }

    /** Cadastra e devolve o corpo da resposta; o cliente fica logado. */
    public static JsonNode register(ApiClient client, String username, @Nullable String inviteToken) {
        client.bootstrap();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("displayName", "Pessoa " + username);
        body.put("password", PASSWORD);
        if (inviteToken != null) {
            body.put("inviteToken", inviteToken);
        }
        var result = client.post("/api/v1/auth/register", body);
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        return client.json(result);
    }

    /** Cria um convite com o cliente logado e devolve o token. */
    public static String invite(ApiClient client) {
        var result = client.post("/api/v1/invites", Map.of("validHours", 24, "maxUses", 5));
        assertThat(result).as(ApiClient.text(result)).hasStatus(201);
        String url = client.json(result).get("url").asString();
        return url.substring(url.indexOf("invite=") + "invite=".length());
    }

    public static String userId(JsonNode registration) {
        return registration.get("me").get("account").get("id").asString();
    }
}
