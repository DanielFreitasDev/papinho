package io.github.labdev2025cmd.papinho.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.labdev2025cmd.papinho.notification.PushGateway;
import io.github.labdev2025cmd.papinho.notification.PushSubscriptions;

/**
 * Serviço de push dos testes: guarda o que seria enviado (o conteúdo ainda sem cifrar) em vez de fazer POST na
 * internet. Assinaturas cujo endereço contém "gone" respondem como o 410 de uma assinatura vencida.
 */
public class RecordingPushGateway implements PushGateway {

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public Result send(PushSubscriptions.Subscription subscription, byte[] payload) {
        sent.add(new Sent(subscription.endpoint(), new String(payload, StandardCharsets.UTF_8)));
        return subscription.endpoint().contains("gone") ? Result.GONE : Result.DELIVERED;
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    public record Sent(String endpoint, String payload) {
    }
}
