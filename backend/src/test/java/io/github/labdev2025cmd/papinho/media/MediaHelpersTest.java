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
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "audio/x-aac|audio/aac|audio/aac",
        "audio/x-flac|audio/flac|audio/flac",
        "audio/x-flac|-|audio/flac",
        "audio/vorbis|-|audio/ogg",
        "audio/vorbis|audio/ogg|audio/ogg",
        "audio/opus|audio/ogg|audio/ogg",
        "audio/vnd.wave|audio/wav|audio/wav",
        "audio/x-wav|-|audio/wav",
        "audio/x-m4a|-|audio/mp4",
    })
    @DisplayName("arquivo de áudio com o nome que o Tika dá vira áudio com o tipo que o navegador toca")
    void shouldNormalizeAudioTypes(String detected, String declared, String expected) {
        assertThat(ContentTypes.resolve(detected, declared)).isEqualTo(expected);
        assertThat(ContentTypes.kind(expected)).isEqualTo(MediaKind.AUDIO);
        assertThat(ContentTypes.inline(expected)).isTrue();
    }

    @Test
    @DisplayName("áudio guardado com o nome antigo continua abrindo no navegador")
    void shouldStillServeOldAudioTypesInline() {
        assertThat(ContentTypes.inline("audio/vnd.wave")).isTrue();
        assertThat(ContentTypes.inline("audio/x-m4a")).isTrue();
        assertThat(ContentTypes.inline("audio/opus")).isTrue();
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
    @DisplayName("JPEG progressivo limpo em fluxo: sai sem EXIF e comentário e decodifica igual (MD-02)")
    void shouldStripProgressiveJpegInStream() throws IOException {
        BufferedImage image = new BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 96; x++) {
            for (int y = 0; y < 64; y++) {
                image.setRGB(x, y, (x * 7 + y * 13) * 0x010203);
            }
        }
        ByteArrayOutputStream progressive = new ByteArrayOutputStream();
        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(javax.imageio.ImageWriteParam.MODE_DEFAULT);
        try (var output = javax.imageio.ImageIO.createImageOutputStream(progressive)) {
            writer.setOutput(output);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        }
        byte[] plain = progressive.toByteArray();
        byte[] exif = "Exif\0\0GPS-SECRETO".getBytes(StandardCharsets.ISO_8859_1);
        byte[] comment = "comentario-secreto".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream tagged = new ByteArrayOutputStream();
        tagged.write(plain, 0, 2);
        tagged.write(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        tagged.write(exif);
        tagged.write(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, (byte) (comment.length + 2)});
        tagged.write(comment);
        tagged.write(plain, 2, plain.length - 2);
        tagged.write("depois-do-fim".getBytes(StandardCharsets.ISO_8859_1));

        ByteArrayOutputStream clean = new ByteArrayOutputStream();
        PhotoMetadata.stripJpeg(new ByteArrayInputStream(tagged.toByteArray()), clean);
        String text = clean.toString(StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("GPS-SECRETO").doesNotContain("comentario-secreto")
                .doesNotContain("depois-do-fim");
        assertThat(clean.toByteArray()).as("só os metadados saem").hasSize(plain.length);
        BufferedImage decoded = javax.imageio.ImageIO.read(new ByteArrayInputStream(clean.toByteArray()));
        BufferedImage original = javax.imageio.ImageIO.read(new ByteArrayInputStream(plain));
        assertThat(decoded.getRGB(50, 30)).isEqualTo(original.getRGB(50, 30));
    }

    @Test
    @DisplayName("PNG limpo em fluxo perde os blocos de texto e continua válido (MD-02)")
    void shouldStripPngTextInStream() throws IOException {
        byte[] tagged = Images.pngWithText(40, 20, "GPS-SECRETO");
        ByteArrayOutputStream clean = new ByteArrayOutputStream();
        PhotoMetadata.stripPng(new ByteArrayInputStream(tagged), clean);
        assertThat(clean.toString(StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRETO");
        assertThat(clean.toByteArray()).isEqualTo(Images.png(40, 20));
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
