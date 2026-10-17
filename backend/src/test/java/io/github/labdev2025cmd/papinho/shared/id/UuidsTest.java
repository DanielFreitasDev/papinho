package io.github.labdev2025cmd.papinho.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidsTest {

    @Test
    @DisplayName("gera UUID versão 7 com a variante da RFC e o instante atual nos 48 bits altos (ID-01)")
    void shouldGenerateVersion7() {
        long before = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UUID id = Uuids.v7();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            assertThat(id.getMostSignificantBits() >>> 16).isBetween(before, System.currentTimeMillis());
            seen.add(id);
        }
        assertThat(seen).as("sem repetição").hasSize(1000);
    }
}
