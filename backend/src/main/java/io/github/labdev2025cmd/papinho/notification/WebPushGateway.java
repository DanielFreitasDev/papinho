package io.github.labdev2025cmd.papinho.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.labdev2025cmd.papinho.notification.internal.WebPushCrypto;

/**
 * Web Push de verdade: cifra o conteúdo para o navegador (RFC 8291), assina com VAPID (RFC 8292) e faz o POST no
 * serviço de push, sem seguir redirecionamentos. Nos logs vai só o host e o status, nunca o endereço completo
 * (que identifica o navegador) nem o conteúdo.
 */
@Component
class WebPushGateway implements PushGateway {

    private static final Logger LOG = LoggerFactory.getLogger(WebPushGateway.class);

    private final VapidKeys keys;
    private final PushProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    WebPushGateway(VapidKeys keys, PushProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Result send(PushSubscriptions.Subscription subscription, byte[] payload) {
        URI endpoint = URI.create(subscription.endpoint());
        byte[] body = WebPushCrypto.encrypt(payload, Base64.getUrlDecoder().decode(subscription.p256dh()),
                Base64.getUrlDecoder().decode(subscription.auth()));
        String audience = endpoint.getScheme() + "://" + endpoint.getHost();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/octet-stream").header("Content-Encoding", "aes128gcm")
                .header("TTL", Long.toString(properties.ttl().toSeconds())).header("Urgency", "high")
                .header("Authorization", keys.current().authorization(audience, properties.subject(),
                        clock.instant().plus(Duration.ofHours(12))))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            if (status / 100 != 2) {
                LOG.warn("Push service {} answered {}", endpoint.getHost(), status);
                return Result.FAILED;
            }
            return Result.DELIVERED;
        } catch (IOException exception) {
            LOG.warn("Push service {} unreachable: {}", endpoint.getHost(), exception.getClass().getSimpleName());
            return Result.FAILED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }
}
