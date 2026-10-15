package io.github.labdev2025cmd.papinho.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.labdev2025cmd.papinho.support.Images;

/** Nome de arquivo, tipo de conteúdo, orientação EXIF e limpeza de WebP (MD-01, MD-02). */
class MediaHelpersTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "../../etc/passwd|passwd",
        "C:\\\\Users\\\\ana\\\\foto.jpg|foto.jpg",
        "..|arquivo",
        "  relatório final.pdf  |relatório final.pdf",
        "a<b>c:d\"e?g*h.txt|a_b_c_d_e_g_h.txt",
        ".bashrc|bashrc",
    })
    @DisplayName("nome de arquivo sem diretórios, reservados nem pontos nas pontas")
    void shouldSanitizeFilenames(String raw, String expected) {
        assertThat(FileNames.sanitize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("nome longo é cortado preservando a extensão; controle e bidi viram _")
    void shouldTruncateAndRemoveControlCharacters() {
        String longName = FileNames.sanitize("x".repeat(500) + ".docx");
        assertThat(longName).hasSize(FileNames.MAX_LENGTH).endsWith(".docx");
        assertThat(FileNames.sanitize("fatura\u202Etxt.exe")).isEqualTo("fatura_txt.exe");
        assertThat(FileNames.sanitize("a\u0000b")).isEqualTo("a_b");
    }

    @Test
    @DisplayName("áudio gravado em WebM/Ogg/MP4 vira áudio; SVG e HTML nunca abrem no navegador")
    void shouldResolveTypes() {
        assertThat(ContentTypes.resolve("video/webm", "audio/webm;codecs=opus")).isEqualTo("audio/webm");
        assertThat(ContentTypes.resolve("application/ogg", "audio/ogg")).isEqualTo("audio/ogg");
        assertThat(ContentTypes.resolve("video/mp4", "audio/mp4")).isEqualTo("audio/mp4");
        assertThat(ContentTypes.resolve("video/webm", "video/webm")).isEqualTo("video/webm");
        assertThat(ContentTypes.kind("audio/webm")).isEqualTo(MediaKind.AUDIO);
        assertThat(ContentTypes.kind("image/svg+xml")).isEqualTo(MediaKind.DOCUMENT);
        assertThat(ContentTypes.inline("image/svg+xml")).isFalse();
        assertThat(ContentTypes.inline("text/html")).isFalse();
        assertThat(ContentTypes.inline("image/png")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"1,40,20,RED", "3,40,20,BLUE", "6,20,40,RED", "8,20,40,BLUE"})
    @DisplayName("orientação EXIF é aplicada antes de descartar os metadados")
    void shouldApplyExifOrientation(int orientation, int width, int height, String topLeft) throws IOException {
        BufferedImage image = ImageProcessing.read(
                new ByteArrayInputStream(Images.jpegWithOrientation(40, 20, orientation)));
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
        int rgb = image.getRGB(1, 1);
        boolean red = ((rgb >> 16) & 0xFF) > 200 && (rgb & 0xFF) < 80;
        assertThat(red ? "RED" : "BLUE").isEqualTo(topLeft);
    }

    @Test
    @DisplayName("JPEG tratado perde o EXIF e ganha miniatura e ThumbHash")
    void shouldStripJpegMetadata() throws IOException {
        PhotoPipeline.Result result = PhotoPipeline.process(Images.jpegWithOrientation(800, 400, 6), "image/jpeg");
        assertThat(new String(result.content(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRETO")
                .doesNotContain("Exif");
        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(800);
        assertThat(result.thumbnail()).isNotNull();
        assertThat(result.thumbhash()).isNotBlank();
    }

    @Test
    @DisplayName("WebP perde os blocos EXIF e XMP e as flags do VP8X")
    void shouldStripWebpMetadata() throws IOException {
        byte[] vp8x = chunk("VP8X", new byte[] {(byte) 0x0C, 0, 0, 0, 9, 0, 0, 9, 0, 0});
        byte[] image = chunk("VP8L", new byte[] {0x2f, 1, 2});
        byte[] exif = chunk("EXIF", "GPS-SECRETO".getBytes(StandardCharsets.ISO_8859_1));
        byte[] xmp = chunk("XMP ", "<x:xmpmeta/>".getBytes(StandardCharsets.ISO_8859_1));
        byte[] webp = riff(vp8x, image, exif, xmp);

        byte[] stripped = WebpMetadata.strip(webp);
        assertThat(stripped).isNotNull();
        String text = new String(stripped, StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("GPS-SECRETO").doesNotContain("xmpmeta").contains("VP8L");
        assertThat(stripped[20] & 0x0C).isZero();
        int riffSize = (stripped[4] & 0xFF) | (stripped[5] & 0xFF) << 8;
        assertThat(riffSize).isEqualTo(stripped.length - 8);
        assertThat(WebpMetadata.strip("not a webp file".getBytes(StandardCharsets.US_ASCII))).isNull();
    }

    private static byte[] chunk(String fourcc, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourcc.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[] {(byte) data.length, (byte) (data.length >> 8), 0, 0});
        out.writeBytes(data);
        if (data.length % 2 == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] riff(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        byte[] content = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[] {(byte) content.length, (byte) (content.length >> 8), 0, 0});
        out.writeBytes(content);
        return out.toByteArray();
    }
}
