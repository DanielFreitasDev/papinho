package io.github.labdev2025cmd.papinho.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.Images;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;

/** Nome de exibição, recado e foto sem EXIF em dois tamanhos (ID-02). */
@IntegrationTest
class ProfileIT {

    @Autowired
    private ApiClients clients;

    @Test
    @DisplayName("atualiza nome e recado; nome vazio é recusado")
    void shouldUpdateProfile() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);

        var updated = ana.put("/api/v1/profile", Map.of("displayName", "  Ana Souza  ", "about", "Bora conversar"));
        assertThat(updated).hasStatusOk();
        assertThat(ana.json(updated).get("displayName").asString()).isEqualTo("Ana Souza");
        assertThat(ana.json(updated).get("about").asString()).isEqualTo("Bora conversar");
        assertThat(ana.put("/api/v1/profile", Map.of("displayName", "   "))).hasStatus(400);
    }

    @Test
    @DisplayName("foto é recortada em quadrado, recodificada sem EXIF e servida em 512 e 96 px")
    void shouldStripExifAndResizePhoto() throws Exception {
        ApiClient ana = clients.create();
        String anaId = Accounts.userId(Accounts.register(ana, "ana", null));
        byte[] original = Images.jpegWithExif(1200, 900);
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("Exif").contains("GPS-SECRETO");

        var result = ana.multipart(HttpMethod.PUT, "/api/v1/profile/photo",
                new MockMultipartFile("photo", "foto.jpg", "image/jpeg", original));
        assertThat(result).hasStatusOk();
        assertThat(ana.json(result).get("hasPhoto").asBoolean()).isTrue();

        byte[] large = ana.get("/api/v1/users/" + anaId + "/photo").getResponse().getContentAsByteArray();
        byte[] thumb = ana.get("/api/v1/users/" + anaId + "/photo?thumbnail=true").getResponse()
                .getContentAsByteArray();
        assertThat(new String(large, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS-SECRETO");
        var largeImage = ImageIO.read(new ByteArrayInputStream(large));
        var thumbImage = ImageIO.read(new ByteArrayInputStream(thumb));
        assertThat(largeImage.getWidth()).isEqualTo(512);
        assertThat(largeImage.getHeight()).isEqualTo(512);
        assertThat(thumbImage.getWidth()).isEqualTo(96);

        assertThat(ana.delete("/api/v1/profile/photo", null)).hasStatusOk();
        assertThat(ana.get("/api/v1/users/" + anaId + "/photo")).hasStatus(404);
    }

    @Test
    @DisplayName("arquivo que não é imagem é recusado com 422")
    void shouldRejectNonImage() {
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);

        var result = ana.multipart(HttpMethod.PUT, "/api/v1/profile/photo",
                new MockMultipartFile("photo", "foto.jpg", "image/jpeg", "não sou imagem".getBytes()));
        assertThat(result).hasStatus(422);
        assertThat(ana.json(result).get("type").asString()).isEqualTo("urn:papinho:problem:image-invalid");
    }

    @Test
    @DisplayName("foto exige sessão")
    void shouldRequireAuthenticationForPhotos() {
        ApiClient anonymous = clients.create().bootstrap();
        assertThat(anonymous.get("/api/v1/users/00000000-0000-7000-8000-000000000000/photo")).hasStatus(401);
    }
}
