package com.aiplatform.foundation.objectstorage.minio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class MinioTextContentObjectStorageAdapterTest {

    private static final String IMAGE =
            "minio/minio:RELEASE.2025-09-07T16-13-09Z"
                    + "@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e";
    private static final String ACCESS_KEY = "foundation_test";
    private static final String SECRET_KEY = "foundation-test-secret-key";
    private static final String BUCKET = "foundation-v1-private-test";
    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID =
            new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID =
            new DataSourceId(220000000000000001L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");

    @Container
    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forStatusCode(200));

    private static MinioClient client;
    private static MinioTextContentObjectStorageAdapter adapter;
    private static MinioStructuredDatasetCsvStorageAdapter structuredDatasetAdapter;

    @BeforeAll
    static void 建立真实私有对象存储() {
        client = MinioClient.builder()
                .endpoint(endpoint())
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        adapter = new MinioTextContentObjectStorageAdapter(client, BUCKET);
        structuredDatasetAdapter = new MinioStructuredDatasetCsvStorageAdapter(client, BUCKET);
        adapter.ensureBucket(true);
    }

    @AfterAll
    static void 关闭客户端() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void 内容寻址写入元数据读取和受治理删除形成真实合同() throws Exception {
        var bytes = "[合成数据]\n名称: 青柚气泡水\n".getBytes(StandardCharsets.UTF_8);
        var sha256 = sha256(bytes);
        var request = request(bytes, sha256);

        var first = adapter.put(request);
        var second = adapter.put(request);

        assertEquals(first, second);
        assertEquals(
                "foundation-v1/tenants/" + TENANT_ID.value()
                        + "/knowledge-data/knowledge-spaces/" + SPACE_ID.value()
                        + "/data-sources/" + SOURCE_ID.value()
                        + "/versions/1/" + sha256 + "/content.txt",
                first.objectKey());
        assertEquals(sha256, first.sha256());
        assertEquals(bytes.length, first.byteSize());
        assertEquals(TextContentReference.UTF8_TEXT, first.mediaType());

        var metadata = adapter.metadata(first);
        assertEquals(first.objectKey(), metadata.objectKey());
        assertEquals(first.sha256(), metadata.sha256());
        assertEquals(first.byteSize(), metadata.byteSize());
        assertEquals(first.mediaType(), metadata.mediaType());

        try (var stored = client.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(first.objectKey())
                .build())) {
            assertArrayEquals(bytes, stored.readAllBytes());
        }

        var anonymousResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                                endpoint() + "/" + BUCKET + "/" + first.objectKey()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(403, anonymousResponse.statusCode());

        adapter.delete(first);
        assertThrows(
                ObjectStorageUnavailableException.class,
                () -> adapter.metadata(first));
    }

    @Test
    void 生产模式不允许隐式创建缺失桶() {
        var missingBucket = "foundation-v1-missing-test";
        var strictAdapter =
                new MinioTextContentObjectStorageAdapter(client, missingBucket);

        assertThrows(
                ObjectStorageUnavailableException.class,
                () -> strictAdapter.ensureBucket(false));
    }

    @Test
    void 缺少受治理摘要的对象不能生成可信元数据() {
        var objectKey = "foundation-v1/invalid/content.txt";
        var reference = new TextContentReference(
                1,
                objectKey,
                "a".repeat(64),
                1,
                TextContentReference.UTF8_TEXT,
                CREATED_AT);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(new byte[] {'a'}), 1, -1)
                    .contentType(TextContentReference.UTF8_TEXT)
                    .build());

            assertThrows(
                    TextContentIntegrityException.class,
                    () -> adapter.metadata(reference));
        } catch (TextContentIntegrityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssertionError("准备缺少摘要的测试对象失败", exception);
        } finally {
            adapter.delete(reference);
        }
    }

    @Test
    void 结构化数据集CSV使用私有内容寻址路径写入读取和删除() throws Exception {
        var bytes = "content,title\n测试内容,测试标题\n".getBytes(StandardCharsets.UTF_8);
        var request = structuredDatasetRequest(bytes, sha256(bytes));

        var reference = structuredDatasetAdapter.put(request);

        assertEquals(
                "foundation-v1/tenants/" + TENANT_ID.value()
                        + "/knowledge-data/knowledge-spaces/" + SPACE_ID.value()
                        + "/structured-datasets/" + SOURCE_ID.value()
                        + "/revisions/1/" + request.content().sha256() + "/data.csv",
                reference.objectKey());
        var stat = client.statObject(StatObjectArgs.builder()
                .bucket(BUCKET)
                .object(reference.objectKey())
                .build());
        assertEquals(StructuredDatasetCsvReference.UTF8_CSV, stat.contentType());
        assertEquals(request.content().sha256(), stat.userMetadata().get("sha256"));
        try (var stored = structuredDatasetAdapter.read(reference)) {
            assertArrayEquals(bytes, stored.readAllBytes());
        }

        var anonymousResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                                endpoint() + "/" + BUCKET + "/" + reference.objectKey()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(403, anonymousResponse.statusCode());

        structuredDatasetAdapter.delete(reference);
        assertThrows(Exception.class, () -> client.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(reference.objectKey())
                .build()));
    }

    @Test
    void 平台对象读取只接受冻结成员并在流读取完成时校验摘要() throws Exception {
        var bytes = "对象存储的受治理正文\n".getBytes(StandardCharsets.UTF_8);
        var object = new PlatformObjectReference(
                "obj_ObjectStorage001",
                sha256(bytes),
                bytes.length,
                TextContentReference.UTF8_TEXT,
                CREATED_AT);
        var collection = new ObjectStorageObjectCollection(
                new PlatformObjectRangeId("rng_ObjectStorage001"),
                1,
                java.util.List.of(object));
        var revision = objectStorageRevision(collection);
        var objectKey = "platform-internal/object-storage/content-001.txt";
        var reader = new MinioPlatformObjectContentReaderAdapter(
                client,
                BUCKET,
                java.util.List.of(new PlatformObjectLocation(
                        TENANT_ID.value(),
                        SPACE_ID.value(),
                        collection.rangeId().value(),
                        collection.selectionRevision(),
                        object.objectId(),
                        objectKey)));
        client.putObject(PutObjectArgs.builder()
                .bucket(BUCKET)
                .object(objectKey)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(TextContentReference.UTF8_TEXT)
                .build());

        try (var content = reader.open(revision, object)) {
            assertArrayEquals(bytes, content.readAllBytes());
        }

        var changedObject = new PlatformObjectReference(
                object.objectId(),
                "a".repeat(64),
                object.byteSize(),
                object.mediaType(),
                object.freshAt());
        var changedRevision = objectStorageRevision(new ObjectStorageObjectCollection(
                collection.rangeId(),
                collection.selectionRevision(),
                java.util.List.of(changedObject)));
        try (var content = reader.open(changedRevision, changedObject)) {
            assertThrows(TextContentIntegrityException.class, content::readAllBytes);
        }
    }

    private static TextContentStorageRequest request(byte[] bytes, String sha256) {
        return new TextContentStorageRequest(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                1,
                new StagedTextContent() {
                    @Override
                    public long byteSize() {
                        return bytes.length;
                    }

                    @Override
                    public String sha256() {
                        return sha256;
                    }

                    @Override
                    public ByteArrayInputStream openStream() {
                        return new ByteArrayInputStream(bytes);
                    }

                    @Override
                    public void close() {
                        // 测试内容只存在于内存，不持有外部资源。
                    }
                },
                CREATED_AT);
    }

    private static StructuredDatasetCsvStorageRequest structuredDatasetRequest(byte[] bytes, String sha256) {
        return new StructuredDatasetCsvStorageRequest(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                1,
                stagedContent(bytes, sha256),
                CREATED_AT);
    }

    private static SourceRevision objectStorageRevision(ObjectStorageObjectCollection collection) {
        return SourceRevision.restore(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                1,
                GovernedDataSourceType.OBJECT_STORAGE,
                collection.definitionDigest(),
                null,
                collection,
                null,
                new CreatorId(100000000000000001L),
                CREATED_AT);
    }

    private static StagedTextContent stagedContent(byte[] bytes, String sha256) {
        return new StagedTextContent() {
            @Override
            public long byteSize() {
                return bytes.length;
            }

            @Override
            public String sha256() {
                return sha256;
            }

            @Override
            public ByteArrayInputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void close() {
                // 测试内容只存在于内存，不持有外部资源。
            }
        };
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
