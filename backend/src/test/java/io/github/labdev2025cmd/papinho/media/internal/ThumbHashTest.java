package io.github.labdev2025cmd.papinho.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O ThumbHash em Java bate byte a byte com a implementação de referência em JavaScript ({@code thumbhash} 0.1.1). */
class ThumbHashTest {

    @Test
    @DisplayName("imagem opaca e imagem com alfa geram o mesmo hash da referência")
    void shouldMatchReferenceImplementation() {
        assertThat(Base64.getEncoder().encodeToString(ThumbHash.rgbaToThumbHash(8, 6, image(8, 6, false))))
                .isEqualTo("HPgJTZAhWnhgeIeCd6h4dlR3j+Bm");
        assertThat(Base64.getEncoder().encodeToString(ThumbHash.rgbaToThumbHash(5, 7, image(5, 7, true))))
                .isEqualTo("nwiGLAYnoQicF2t6aUj4oX8GhkeWZ2loBg==");
    }

    /** Mesmo gerador usado para obter os valores de referência no Node. */
    private static int[] image(int w, int h, boolean alpha) {
        int[] rgba = new int[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                rgba[i] = (x * 37 + y * 11) % 256;
                rgba[i + 1] = (x * 13 + y * 29) % 256;
                rgba[i + 2] = (x * 7 + y * 53) % 256;
                rgba[i + 3] = alpha ? (x * 41 + y * 17) % 256 : 255;
            }
        }
        return rgba;
    }
}
