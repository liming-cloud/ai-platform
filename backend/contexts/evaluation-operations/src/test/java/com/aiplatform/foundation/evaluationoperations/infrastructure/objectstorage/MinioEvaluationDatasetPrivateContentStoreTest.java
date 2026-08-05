package com.aiplatform.foundation.evaluationoperations.infrastructure.objectstorage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 验证评测私有正文独占对象路径、校验摘要并且不提供匿名读取。
 */
@Testcontainers(disabledWithoutDocker = true)
class MinioEvaluationDatasetPrivateContentStoreTest {

    private static final String IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z"
            + "@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e";
    private static final String ACCESS_KEY = "foundation_test";
    private static final String SECRET_KEY = "foundation-test-secret-key";
    private static final String BUCKET = "foundation-v1-evaluation-test";
    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forStatusCode(200));

    private static MinioClient client;
    private static MinioEvaluationDatasetPrivateContentStore store;

    @BeforeAll
    static void 建立私有评测对象存储() {
        client = MinioClient.builder()
                .endpoint(endpoint())
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        store = new MinioEvaluationDatasetPrivateContentStore(client, BUCKET);
        store.ensureBucket(true);
    }

    @AfterAll
    static void 关闭客户端() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void 应在私有内容寻址路径写入并阻断匿名正文读取() throws Exception {
        var bytes = "{\"expected\":\"REFUSE\"}\n".getBytes(StandardCharsets.UTF_8);
        var reference = store.put(TENANT_ID, 510000000000000001L, content(bytes));

        assertEquals(
                "private://evaluation/foundation-v1/tenants/100000000000000001/"
                        + "evaluation-operations/datasets/510000000000000001/" + sha256(bytes) + ".content",
                reference);
        try (var stored = client.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(reference.substring("private://evaluation/".length()))
                .build())) {
            assertArrayEquals(bytes, stored.readAllBytes());
        }
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(endpoint() + "/" + BUCKET + "/"
                                + reference.substring("private://evaluation/".length())))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(403, response.statusCode());
    }

    @Test
    void 摘要不匹配时应删除已写对象并拒绝登记() throws Exception {
        var bytes = "{\"expected\":\"ANSWER\"}\n".getBytes(StandardCharsets.UTF_8);
        var expectedDigest = "a".repeat(64);

        assertThrows(IllegalArgumentException.class, () -> store.put(
                TENANT_ID,
                510000000000000002L,
                new EvaluationDatasetRegistrationContent(
                        new ByteArrayInputStream(bytes), expectedDigest, 1, "retention-30d")));
        assertThrows(Exception.class, () -> client.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object("foundation-v1/tenants/100000000000000001/evaluation-operations/datasets/"
                        + "510000000000000002/" + expectedDigest + ".content")
                .build()));
    }

    private static EvaluationDatasetRegistrationContent content(byte[] bytes) throws Exception {
        return new EvaluationDatasetRegistrationContent(
                new ByteArrayInputStream(bytes), sha256(bytes), 1, "retention-30d");
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
