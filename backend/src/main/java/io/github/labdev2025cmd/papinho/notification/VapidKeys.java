package io.github.labdev2025cmd.papinho.notification;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.notification.internal.Vapid;
import io.github.labdev2025cmd.papinho.shared.crypto.SecretCipher;

/**
 * Par de chaves VAPID da instância, criado na primeira vez e guardado no banco com a privada cifrada. Trocar o par
 * invalida as assinaturas dos navegadores, por isso ele nunca é recriado sozinho.
 */
@Component
class VapidKeys {

    private final NamedParameterJdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final Clock clock;
    private volatile Vapid cached;

    VapidKeys(NamedParameterJdbcTemplate jdbc, SecretCipher cipher, Clock clock) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    Vapid current() {
        Vapid vapid = cached;
        if (vapid != null) {
            return vapid;
        }
        Vapid.Generated generated = Vapid.generate();
        // Duas instâncias podem tentar ao mesmo tempo: só a primeira grava, as duas leem a mesma linha.
        jdbc.update("""
                insert into push_vapid_keys (id, public_key, private_key, created_at)
                values (1, :publicKey, :privateKey, :now) on conflict (id) do nothing
                """, new MapSqlParameterSource("publicKey", generated.publicKey())
                .addValue("privateKey", cipher.encrypt(generated.privateKeyPkcs8()))
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC)));
        List<Vapid> stored = jdbc.query("select public_key, private_key from push_vapid_keys where id = 1",
                new MapSqlParameterSource(), (rs, index) -> Vapid.load(rs.getString("public_key"),
                        cipher.decrypt(rs.getString("private_key"))));
        vapid = stored.getFirst();
        cached = vapid;
        return vapid;
    }
}
