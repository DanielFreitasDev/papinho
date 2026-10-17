package io.github.labdev2025cmd.papinho.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Gerador de UUID versão 7 (RFC 9562): 48 bits do instante em milissegundos e 74 bits aleatórios. É o formato
 * de todos os IDs internos e chaves de arquivo (ID-01, ADR 0006), o mesmo que o Hibernate e o {@code uuidv7()}
 * do PostgreSQL geram; a ordem pelo tempo mantém os índices compactos.
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        long millis = System.currentTimeMillis();
        long high = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long low = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }
}
