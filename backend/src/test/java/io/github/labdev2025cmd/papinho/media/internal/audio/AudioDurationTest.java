package io.github.labdev2025cmd.papinho.media.internal.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Duração lida do arquivo (AU-02). As gravações {@code chromium.webm}, {@code firefox.webm} e {@code firefox.ogg}
 * saíram do MediaRecorder real dos navegadores (microfone falso do Playwright); o valor esperado é o que o
 * {@code decodeAudioData} do próprio navegador mediu. Os demais foram gerados com ffmpeg e lame (tom de 2,5 s):
 * sem lista de edição nem etiqueta LAME (MP4 fragmentado, AAC cru, MP3 sem Xing), as amostras de preparo e o
 * enchimento do codificador contam, como no navegador.
 */
class AudioDurationTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
        chromium.webm        | audio/webm  | 2280 | 0
        firefox.webm         | audio/webm  | 2274 | 0
        firefox.ogg          | audio/ogg   | 2251 | 0
        opus.webm            | audio/webm  | 2500 | 2
        opus-ao-vivo.webm    | audio/webm  | 2500 | 2
        vorbis.webm          | audio/webm  | 2516 | 0
        vorbis.ogg           | audio/ogg   | 2500 | 0
        flac.oga             | audio/ogg   | 2500 | 0
        aac.m4a              | audio/mp4   | 2500 | 0
        aac-faststart.m4a    | audio/mp4   | 2500 | 0
        aac-fragmentado.m4a  | audio/mp4   | 2564 | 0
        adts.aac             | audio/aac   | 2624 | 0
        lame-cbr.mp3         | audio/mpeg  | 2500 | 0
        lame-vbr.mp3         | audio/mpeg  | 2500 | 0
        cbr-sem-xing.mp3     | audio/mpeg  | 2592 | 0
        pcm.wav              | audio/wav   | 1000 | 0
        flac.flac            | audio/flac  | 2500 | 0
        """)
    @DisplayName("duração dos contêineres aceitos como áudio")
    void shouldReadDuration(String file, String type, long expected, long tolerance) {
        assertThat(AudioDuration.millis(new ByteArrayInputStream(fixture(file)), type))
                .isCloseTo(expected, within(tolerance));
    }

    @Test
    @DisplayName("tipo aceito com outro nome (o que o Tika devolve) e tipo com parâmetros")
    void shouldAcceptTypeAliases() {
        assertThat(AudioDuration.millis(stream("firefox.ogg"), "audio/opus")).isEqualTo(2251);
        assertThat(AudioDuration.millis(stream("pcm.wav"), "audio/vnd.wave")).isEqualTo(1000);
        assertThat(AudioDuration.millis(stream("chromium.webm"), "audio/webm;codecs=opus")).isEqualTo(2280);
        assertThat(AudioDuration.millis(stream("flac.flac"), "audio/x-flac")).isEqualTo(2500);
        assertThat(AudioDuration.millis(stream("adts.aac"), "audio/x-aac")).isEqualTo(2624);
    }

    @Test
    @DisplayName("tipo que não é áudio conhecido não é lido")
    void shouldIgnoreOtherTypes() {
        assertThat(AudioDuration.millis(stream("aac.m4a"), "video/mp4")).isNull();
        assertThat(AudioDuration.millis(stream("chromium.webm"), "application/pdf")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"chromium.webm", "firefox.webm", "firefox.ogg", "adts.aac", "cbr-sem-xing.mp3",
        "aac-fragmentado.m4a"})
    @DisplayName("arquivo cortado no meio dá no máximo a duração do que chegou, nunca erro")
    void shouldReadTruncatedFiles(String file) {
        byte[] full = fixture(file);
        String type = typeOf(file);
        Long whole = AudioDuration.millis(new ByteArrayInputStream(full), type);
        for (int cut = 0; cut < full.length; cut += Math.max(1, full.length / 97)) {
            Long partial = AudioDuration.millis(new ByteArrayInputStream(Arrays.copyOf(full, cut)), type);
            if (partial != null) {
                assertThat(partial).as("%s cortado em %d", file, cut).isLessThanOrEqualTo(whole + 70);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/aac", "audio/wav",
        "audio/flac"})
    @Timeout(5)
    @DisplayName("lixo e arquivo vazio não têm duração")
    void shouldRejectGarbage(String type) {
        byte[] garbage = new byte[256 * 1024];
        new Random(42).nextBytes(garbage);
        assertThat(AudioDuration.millis(new ByteArrayInputStream(garbage), type)).isNull();
        assertThat(AudioDuration.millis(new ByteArrayInputStream(new byte[0]), type)).isNull();
        assertThat(AudioDuration.millis(new ByteArrayInputStream(new byte[50 * 1024 * 1024]), type)).isNull();
    }

    @Test
    @Timeout(5)
    @DisplayName("tamanhos e contagens forjados não alocam nem travam; duração absurda é descartada")
    void shouldSurviveForgedFields() {
        // Bloco WebM que diz ter 2^56 bytes: a leitura só chega ao fim do arquivo.
        byte[] webm = fixture("chromium.webm");
        byte[] forged = Arrays.copyOf(webm, webm.length + 9);
        forged[webm.length] = (byte) 0xA3;
        forged[webm.length + 1] = 0x01;
        Arrays.fill(forged, webm.length + 2, forged.length, (byte) 0xFE);
        assertThat(AudioDuration.millis(new ByteArrayInputStream(forged), "audio/webm")).isEqualTo(2280);

        // Página Ogg com grânulo perto de 2^63: dias de áudio que o arquivo não tem.
        byte[] ogg = fixture("firefox.ogg");
        int lastPage = lastIndexOf(ogg, "OggS");
        ByteBuffer.wrap(ogg, lastPage + 6, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(Long.MAX_VALUE - 1);
        assertThat(AudioDuration.millis(new ByteArrayInputStream(ogg), "audio/ogg")).isNull();

        // trun com 2^32 - 1 amostras numa caixa de 20 bytes.
        byte[] trun = {0, 0, 0, 20, 't', 'r', 'u', 'n', 0, 0, 1, 0, -1, -1, -1, -1, 0, 0, 0, 1};
        assertThat(AudioDuration.millis(new ByteArrayInputStream(trun), "audio/mp4")).isNull();

        // mvhd com escala 1 e 2^32 - 2 unidades: mais de 136 anos.
        byte[] mvhd = new byte[8 + 20];
        ByteBuffer.wrap(mvhd).putInt(28).put("mvhd".getBytes(StandardCharsets.US_ASCII)).putInt(0).putInt(0)
                .putInt(0).putInt(1).putInt(0xFFFFFFFE);
        assertThat(AudioDuration.millis(new ByteArrayInputStream(mvhd), "audio/mp4")).isNull();
    }

    @Test
    @DisplayName("falha de leitura no meio do arquivo vira 'sem duração'")
    void shouldReturnNullOnReadFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disco");
            }
        };
        assertThat(AudioDuration.millis(broken, "audio/ogg")).isNull();
    }

    private static String typeOf(String file) {
        return switch (file.substring(file.lastIndexOf('.') + 1)) {
            case "webm" -> "audio/webm";
            case "ogg", "oga" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "mp3" -> "audio/mpeg";
            case "aac" -> "audio/aac";
            case "wav" -> "audio/wav";
            default -> "audio/flac";
        };
    }

    private static int lastIndexOf(byte[] data, String text) {
        for (int i = data.length - text.length(); i >= 0; i--) {
            if (ByteInput.ascii(data, i, text)) {
                return i;
            }
        }
        throw new IllegalArgumentException(text);
    }

    private static InputStream stream(String file) {
        return new ByteArrayInputStream(fixture(file));
    }

    private static byte[] fixture(String file) {
        try (InputStream in = AudioDurationTest.class.getResourceAsStream("/audio/" + file)) {
            if (in == null) {
                throw new IllegalArgumentException(file);
            }
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
