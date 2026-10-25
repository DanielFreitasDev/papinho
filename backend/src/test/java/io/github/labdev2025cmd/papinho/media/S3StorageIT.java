package io.github.labdev2025cmd.papinho.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.github.labdev2025cmd.papinho.support.Accounts;
import io.github.labdev2025cmd.papinho.support.ApiClient;
import io.github.labdev2025cmd.papinho.support.IntegrationTest;
import io.github.labdev2025cmd.papinho.support.TestSupportConfiguration.ApiClients;
import io.github.labdev2025cmd.papinho.support.Uploads;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Armazenamento S3-compatível (ADR 0006) de ponta a ponta: o upload vai para o bucket, a outra pessoa baixa com
 * {@code Range} e apagar para todos tira o objeto. O S3 é o S3Mock da Adobe num contêiner.
 */
@IntegrationTest
class S3StorageIT {

    private static final String BUCKET = "papinho-arquivos";
    private static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:4.10.0")
            .withExposedPorts(9090).withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .waitingFor(Wait.forLogMessage(".*Started S3MockApplication.*", 1)
                    .withStartupTimeout(java.time.Duration.ofMinutes(2)));

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry registry) {
        registry.add("app.storage.type", () -> "s3");
        registry.add("app.storage.s3.endpoint", S3StorageIT::endpoint);
        registry.add("app.storage.s3.bucket", () -> BUCKET);
        registry.add("app.storage.s3.access-key", () -> "teste");
        registry.add("app.storage.s3.secret-key", () -> "teste-segredo");
    }

    @Autowired
    private ApiClients clients;

    @Autowired
    private FileStorage storage;

    private static String endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }

    @BeforeAll
    static void bucketExists() {
        try (S3Client client = client()) {
            assertThat(client.listBuckets().buckets()).anyMatch(bucket -> bucket.name().equals(BUCKET));
        }
    }

    @Test
    @DisplayName("upload, download com Range e apagar para todos passam pelo bucket")
    void shouldStoreFilesInBucket() {
        // Desde o Spring Modulith 2.1.2, a implementação interna de um tipo da API do módulo vem num proxy de
        // observabilidade (spring-modulith#1874): a classe que importa é a de trás dele.
        assertThat(AopUtils.getTargetClass(storage).getSimpleName()).isEqualTo("S3FileStorage");
        ApiClient ana = clients.create();
        Accounts.register(ana, "ana", null);
        ApiClient bruno = clients.create();
        Accounts.register(bruno, "bruno", Accounts.invite(ana));
        String conversation = ana.json(ana.post("/api/v1/conversations/direct", Map.of("username", "bruno")))
                .get("id").asString();
        byte[] video = new byte[300_000];
        byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0, 'i', 's', 'o', 'm',
            'i', 's', 'o', '2'};
        System.arraycopy(header, 0, video, 0, header.length);
        for (int i = header.length; i < video.length; i++) {
            video[i] = (byte) i;
        }
        String id = Uploads.upload(ana, video, "video.mp4", "video/mp4");
        try (S3Client client = client()) {
            assertThat(client.listObjectsV2(request -> request.bucket(BUCKET)).contents())
                    .anyMatch(object -> object.key().contains(id));
        }
        Map<String, Object> message = new HashMap<>();
        message.put("clientMessageId", UUID.randomUUID());
        message.put("body", "");
        message.put("attachmentId", id);
        String messageId = ana.json(ana.post("/api/v1/conversations/" + conversation + "/messages", message))
                .get("id").asString();

        var range = bruno.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/media/" + id + "/content").header(HttpHeaders.RANGE, "bytes=1000-1999"));
        assertThat(range).hasStatus(206);
        assertThat(range.getResponse().getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(video, 1000, 2000));

        assertThat(ana.delete("/api/v1/messages/" + messageId, null)).hasStatusOk();
        try (S3Client client = client()) {
            assertThat(client.listObjectsV2(request -> request.bucket(BUCKET)).contents())
                    .as("objeto apagado do bucket").noneMatch(object -> object.key().contains(id));
        }
    }

    private static S3Client client() {
        return S3Client.builder().endpointOverride(URI.create(endpoint())).region(Region.US_EAST_1)
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("teste", "teste-segredo"))).build();
    }
}
