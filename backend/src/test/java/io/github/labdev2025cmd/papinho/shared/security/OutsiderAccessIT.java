package io.github.labdev2025cmd.papinho.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import tools.jackson.databind.JsonNode;

/**
 * IDOR (SG-01): uma terceira pessoa com sessão tenta cada ação sobre recursos alheios pelo id e recebe sempre
 * 404, sem descobrir se o recurso existe e sem mudar nada. Complementa os testes por módulo, que cobrem as
 * leituras principais.
 */
@IntegrationTest
class OutsiderAccessIT {

    @Autowired
    private ApiClients clients;

    @Test
    @DisplayName("quem é de fora recebe 404 em toda ação sobre conversa, mensagem, grupo, lista, upload e conta alheios")
    void shouldRefuseOutsiders() {
        ApiClient ana = clients.create();
        String anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        ApiClient bruno = clients.create();
        String brunoId = Accounts.userId(Accounts.register(bruno, "bruno", Accounts.invite(ana)));
        ApiClient carla = clients.create();
        Accounts.register(carla, "carla", Accounts.invite(ana));
        ana.post("/api/v1/contacts", Map.of("username", "bruno"));
        bruno.post("/api/v1/contacts", Map.of("username", "ana"));

        String direct = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        String conversation = "/api/v1/conversations/" + direct;
        String voice = Uploads.upload(ana, Uploads.ogg(2048), "voz.ogg", "audio/ogg");
        String message = ana.json(ana.post(conversation + "/messages", Map.of("clientMessageId", UUID.randomUUID(),
                "body", "original", "attachmentId", voice))).get("id").asString();
        Map<String, Object> pollBody = new HashMap<>();
        pollBody.put("clientMessageId", UUID.randomUUID());
        pollBody.put("question", "Pizza?");
        pollBody.put("options", List.of("Sim", "Não"));
        pollBody.put("multiple", false);
        pollBody.put("anonymous", false);
        JsonNode poll = ana.json(ana.post(conversation + "/polls", pollBody));
        String option = poll.get("poll").get("options").get(0).get("id").asString();
        String scheduled = ana.json(ana.post(conversation + "/scheduled", Map.of("body", "depois", "sendAt",
                Instant.now().plusSeconds(3600).toString()))).get("id").asString();
        String group = ana.json(ana.post("/api/v1/groups", Map.of("name", "Turma", "memberIds", List.of(brunoId))))
                .get("id").asString();
        String broadcast = ana.json(ana.post("/api/v1/broadcasts", Map.of("name", "Avisos", "recipientIds",
                List.of(brunoId)))).get("id").asString();
        String folder = ana.json(ana.post("/api/v1/folders", Map.of("name", "Minha", "conversationIds",
                List.of(direct)))).get("id").asString();
        MvcTestResult created = ana.perform(MockMvcRequestBuilders.post("/api/v1/uploads")
                .header("Tus-Resumable", "1.0.0").header("Upload-Length", 10)
                .header("Upload-Metadata", "filename YS50eHQ="));
        String upload = created.getResponse().getHeader(HttpHeaders.LOCATION);
        Accounts.invite(ana);
        String invite = ana.json(ana.get("/api/v1/invites")).get(0).get("id").asString();
        String session = ana.json(ana.get("/api/v1/sessions")).get(0).get("id").asString();

        String messagePath = "/api/v1/messages/" + message;
        String groupPath = "/api/v1/groups/" + group;
        Map<String, Function<ApiClient, MvcTestResult>> attempts = new java.util.LinkedHashMap<>();
        attempts.put("enviar", c -> c.post(conversation + "/messages", Map.of("clientMessageId", UUID.randomUUID(),
                "body", "intrusa")));
        attempts.put("aceitar", c -> c.post(conversation + "/accept", null));
        attempts.put("recusar", c -> c.post(conversation + "/decline", null));
        attempts.put("entregue", c -> c.put(conversation + "/delivered", Map.of("seq", 1)));
        attempts.put("lida", c -> c.put(conversation + "/read", Map.of("seq", 1)));
        attempts.put("não lida", c -> c.put(conversation + "/unread", null));
        attempts.put("rascunho", c -> c.put(conversation + "/draft", Map.of("body", "x")));
        attempts.put("temporárias", c -> c.put(conversation + "/disappearing", Map.of("seconds", 86400)));
        attempts.put("agendar", c -> c.post(conversation + "/scheduled", Map.of("body", "x", "sendAt",
                Instant.now().plusSeconds(3600).toString())));
        attempts.put("agendadas", c -> c.get(conversation + "/scheduled"));
        attempts.put("cancelar agendada", c -> c.delete("/api/v1/scheduled/" + scheduled, null));
        attempts.put("enquete", c -> c.post(conversation + "/polls", pollBody));
        attempts.put("votar", c -> c.put("/api/v1/messages/" + poll.get("id").asString() + "/vote",
                Map.of("optionIds", List.of(option))));
        attempts.put("fixadas", c -> c.get(conversation + "/pinned"));
        attempts.put("tirar reação", c -> c.delete(messagePath + "/reaction", null));
        attempts.put("fixar", c -> c.put(messagePath + "/pin", null));
        attempts.put("favoritar", c -> c.put(messagePath + "/star", null));
        attempts.put("editar", c -> c.patch(messagePath, Map.of("body", "trocada")));
        attempts.put("apagar", c -> c.delete(messagePath, null));
        attempts.put("dados da mensagem", c -> c.get(messagePath + "/info"));
        attempts.put("visualização única", c -> c.post(messagePath + "/view-once", null));
        attempts.put("transcrição do navegador", c -> c.put("/api/v1/media/" + voice + "/transcript",
                Map.of("text", "falsa")));
        attempts.put("pasta: incluir", c -> c.put("/api/v1/folders/" + folder + "/conversations/" + direct, null));
        attempts.put("pasta: tirar", c -> c.delete("/api/v1/folders/" + folder + "/conversations/" + direct, null));
        attempts.put("grupo: editar", c -> c.patch(groupPath, Map.of("name", "Invadido")));
        attempts.put("grupo: foto", c -> c.put(groupPath + "/photo", new HashMap<>()));
        attempts.put("grupo: permissões", c -> c.put(groupPath + "/settings", Map.of("onlyAdminsSend", true,
                "onlyAdminsEditInfo", true, "onlyAdminsAddMembers", true, "joinApproval", false)));
        attempts.put("grupo: adicionar", c -> c.post(groupPath + "/members", Map.of("userIds", List.of(anaId))));
        attempts.put("grupo: remover", c -> c.delete(groupPath + "/members/" + brunoId, null));
        attempts.put("grupo: promover", c -> c.put(groupPath + "/admins/" + brunoId, null));
        attempts.put("grupo: rebaixar", c -> c.delete(groupPath + "/admins/" + anaId, null));
        attempts.put("grupo: sair", c -> c.post(groupPath + "/leave", null));
        attempts.put("grupo: pedidos", c -> c.get(groupPath + "/requests"));
        attempts.put("grupo: aprovar", c -> c.post(groupPath + "/requests/" + brunoId + "/approve", null));
        attempts.put("transmissão: editar", c -> c.put("/api/v1/broadcasts/" + broadcast, Map.of("name", "Minha",
                "recipientIds", List.of(brunoId))));
        attempts.put("upload: enviar pedaço", c -> c.perform(MockMvcRequestBuilders.request(HttpMethod.PATCH, upload)
                .header("Tus-Resumable", "1.0.0").header("Upload-Offset", 0)
                .contentType("application/offset+octet-stream").content(new byte[10])));
        attempts.put("upload: cancelar", c -> c.perform(MockMvcRequestBuilders.delete(upload)
                .header("Tus-Resumable", "1.0.0")));
        attempts.put("contato: renomear", c -> c.patch("/api/v1/contacts/" + brunoId, Map.of("nickname", "x")));
        attempts.put("contato: tirar", c -> c.delete("/api/v1/contacts/" + brunoId, null));
        attempts.put("convite: revogar", c -> c.delete("/api/v1/invites/" + invite, null));
        attempts.put("sessão: encerrar", c -> c.delete("/api/v1/sessions/" + session, null));

        List<String> wrong = new java.util.ArrayList<>();
        attempts.forEach((name, attempt) -> {
            MvcTestResult result = attempt.apply(carla);
            if (result.getResponse().getStatus() != 404) {
                wrong.add(name + " → " + result.getResponse().getStatus() + " " + ApiClient.text(result));
            }
        });
        assertThat(wrong).as("ações que não responderam 404 a quem é de fora").isEmpty();

        // Nada mudou para as donas.
        assertThat(ana.get("/api/v1/me")).as("sessão da Ana segue").hasStatusOk();
        JsonNode original = ana.json(ana.get(conversation + "/messages")).get("messages");
        assertThat(original.findValuesAsString("body")).contains("original").doesNotContain("intrusa", "trocada");
        assertThat(ana.json(ana.get(groupPath + "/members"))).hasSize(2);
        assertThat(ana.json(ana.get(conversation + "/scheduled"))).hasSize(1);
        assertThat(ana.json(ana.get("/api/v1/invites")).get(0).get("revoked").asBoolean()).isFalse();
        assertThat(ana.get("/api/v1/media/" + voice + "/transcript").getResponse().getContentAsByteArray())
                .asString().doesNotContain("falsa");
    }
}
