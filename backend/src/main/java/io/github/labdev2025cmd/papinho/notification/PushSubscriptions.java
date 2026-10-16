package io.github.labdev2025cmd.papinho.notification;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.notification.internal.WebPushCrypto;
import io.github.labdev2025cmd.papinho.shared.error.ApiException;

/**
 * Assinaturas de Web Push de cada navegador (NT-01). O endereço vem do navegador, então só serviços de push
 * conhecidos (HTTPS, lista em {@link PushProperties#allowedHosts()}) são aceitos: o servidor nunca faz POST para
 * um endereço arbitrário (SSRF).
 */
@Service
public class PushSubscriptions {

    /** Navegadores por pessoa; o mais antigo sai quando passa disso. */
    static final int MAX_PER_USER = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final PushProperties properties;
    private final Clock clock;

    PushSubscriptions(NamedParameterJdbcTemplate jdbc, PushProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Guarda (ou move para esta pessoa, se o navegador trocou de conta) a assinatura, ligada à sessão que a fez
     * ({@code sessionKey}, de {@link PushSessions}); o app confirma a assinatura a cada abertura.
     */
    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String sessionKey) {
        validate(endpoint, p256dh, auth);
        jdbc.update("""
                insert into push_subscriptions (id, user_id, endpoint, p256dh, auth, created_at, session_key)
                values (uuidv7(), :userId, :endpoint, :p256dh, :auth, :now, :sessionKey)
                on conflict (endpoint) do update set user_id = excluded.user_id, p256dh = excluded.p256dh,
                    auth = excluded.auth, created_at = excluded.created_at, session_key = excluded.session_key
                """, new MapSqlParameterSource("userId", userId).addValue("endpoint", endpoint)
                .addValue("p256dh", p256dh).addValue("auth", auth).addValue("sessionKey", sessionKey)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        jdbc.update("""
                delete from push_subscriptions where user_id = :userId and id not in (
                    select id from push_subscriptions where user_id = :userId order by created_at desc limit :max)
                """, new MapSqlParameterSource("userId", userId).addValue("max", MAX_PER_USER));
    }

    /** Tira a assinatura deste navegador; só a própria. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        jdbc.update("delete from push_subscriptions where user_id = :userId and endpoint = :endpoint",
                new MapSqlParameterSource("userId", userId).addValue("endpoint", endpoint));
    }

    /** O serviço de push respondeu 404/410: a assinatura não existe mais. */
    @Transactional
    public void forget(UUID id) {
        jdbc.update("delete from push_subscriptions where id = :id", new MapSqlParameterSource("id", id));
    }

    @Transactional
    public void markUsed(UUID id) {
        jdbc.update("update push_subscriptions set last_used_at = :now where id = :id",
                new MapSqlParameterSource("id", id).addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public List<Subscription> of(UUID userId) {
        return jdbc.query(
                "select id, endpoint, p256dh, auth, session_key from push_subscriptions where user_id = :userId",
                new MapSqlParameterSource("userId", userId), (rs, index) -> new Subscription(
                        rs.getObject("id", UUID.class), rs.getString("endpoint"), rs.getString("p256dh"),
                        rs.getString("auth"), rs.getString("session_key")));
    }

    private void validate(String endpoint, String p256dh, String auth) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidSubscriptionException();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = properties.allowedHosts().stream().map(suffix -> suffix.toLowerCase(Locale.ROOT))
                .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        if (!"https".equals(uri.getScheme()) || !allowed || uri.getUserInfo() != null
                || uri.getPort() != -1 && uri.getPort() != 443 || endpoint.length() > 1024) {
            throw new InvalidSubscriptionException();
        }
        try {
            WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(p256dh));
            if (Base64.getUrlDecoder().decode(auth).length != 16) {
                throw new InvalidSubscriptionException();
            }
        } catch (IllegalArgumentException | GeneralSecurityException invalid) {
            throw new InvalidSubscriptionException();
        }
    }

    /**
     * Assinatura de um navegador.
     *
     * @param sessionKey chave da sessão que a criou; nula nas assinaturas de antes da ligação com a sessão
     */
    public record Subscription(UUID id, String endpoint, String p256dh, String auth, @Nullable String sessionKey) {
    }

    static class InvalidSubscriptionException extends ApiException {
        InvalidSubscriptionException() {
            super(HttpStatus.BAD_REQUEST, "push-subscription-invalid", "Push subscription rejected");
        }
    }
}
