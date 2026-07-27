package com.aiplatform.foundation.vectorstore.pgvector;

import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateRetriever;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 使用 PostgreSQL 原生事务和向量类型实现精确余弦检索。
 */
public final class PgVectorIndexStore
        implements IndexVectorWriter, IndexCandidateRetriever {

    private static final String INSERT = """
            INSERT INTO knowledge_data.index_chunk (
                tenant_id,
                knowledge_space_id,
                index_version_id,
                data_source_id,
                content_version,
                chunk_id,
                ordinal,
                content,
                content_sha256,
                embedding_deployment_id,
                embedding_deployment_revision,
                embedding_dimension,
                embedding
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector)
            )
            """;

    private static final String RETRIEVE = """
            SELECT chunk.data_source_id,
                   chunk.content_version,
                   chunk.chunk_id,
                   chunk.ordinal,
                   chunk.content,
                   1 - (chunk.embedding <=> CAST(? AS vector)) AS score
            FROM knowledge_data.index_chunk chunk
            JOIN knowledge_data.index_version version
              ON version.tenant_id = chunk.tenant_id
             AND version.knowledge_space_id = chunk.knowledge_space_id
             AND version.id = chunk.index_version_id
            WHERE chunk.tenant_id = ?
              AND chunk.knowledge_space_id = ?
              AND (
                    (CAST(? AS bigint) IS NULL AND version.status = 'ACTIVE')
                    OR chunk.index_version_id = CAST(? AS bigint)
              )
              AND chunk.embedding_deployment_id = ?
              AND chunk.embedding_deployment_revision = ?
              AND chunk.embedding_dimension = ?
            ORDER BY chunk.embedding <=> CAST(? AS vector),
                     chunk.chunk_id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public PgVectorIndexStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "数据库访问器不能为空");
    }

    @Override
    public void replace(IndexVectorBatch batch) {
        Objects.requireNonNull(batch, "索引向量批次不能为空");
        jdbc.update(
                """
                DELETE FROM knowledge_data.index_chunk
                WHERE tenant_id = ?
                  AND knowledge_space_id = ?
                  AND index_version_id = ?
                """,
                batch.tenantId().value(),
                batch.knowledgeSpaceId(),
                batch.indexVersionId());
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement statement, int index)
                    throws SQLException {
                var vector = batch.vectors().get(index);
                statement.setLong(1, batch.tenantId().value());
                statement.setLong(2, batch.knowledgeSpaceId());
                statement.setLong(3, batch.indexVersionId());
                statement.setLong(4, vector.dataSourceId());
                statement.setInt(5, vector.contentVersion());
                statement.setString(6, vector.chunkId());
                statement.setInt(7, vector.ordinal());
                statement.setString(8, vector.text());
                statement.setString(9, vector.textDigest());
                statement.setObject(10, batch.embeddingDeploymentId());
                statement.setLong(11, batch.embeddingRevision());
                statement.setInt(12, batch.embeddingDimension());
                statement.setString(13, literal(vector.embedding()));
            }

            @Override
            public int getBatchSize() {
                return batch.vectors().size();
            }
        });
    }

    @Override
    public List<IndexCandidate> retrieve(IndexCandidateQuery query) {
        Objects.requireNonNull(query, "索引候选查询不能为空");
        var embedding = literal(query.embedding());
        return List.copyOf(jdbc.query(
                RETRIEVE,
                (result, rowNumber) -> new IndexCandidate(
                        result.getLong("data_source_id"),
                        result.getInt("content_version"),
                        result.getString("chunk_id"),
                        result.getInt("ordinal"),
                        result.getString("content"),
                        clamp(result.getDouble("score"))),
                embedding,
                query.tenantId().value(),
                query.knowledgeSpaceId(),
                query.indexVersionId(),
                query.indexVersionId(),
                query.embeddingDeploymentId(),
                query.embeddingRevision(),
                query.embeddingDimension(),
                embedding,
                query.limit()));
    }

    private static String literal(float[] values) {
        var parts = new ArrayList<String>(values.length);
        for (var value : values) {
            parts.add(Float.toString(value));
        }
        return "[" + String.join(",", parts) + "]";
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
