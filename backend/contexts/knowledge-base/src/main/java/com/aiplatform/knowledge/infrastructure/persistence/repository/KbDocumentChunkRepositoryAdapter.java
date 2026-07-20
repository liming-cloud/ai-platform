package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.port.retrieval.model.RetrievalSearchQuery;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.knowledge.domain.port.retrieval.KnowledgeRetrievalCandidateRepository;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeDocumentVectorIndexRepository;
import com.aiplatform.knowledge.domain.port.maintenance.KnowledgeIndexMaintenancePort;
import com.aiplatform.knowledge.domain.port.indexing.ActiveKnowledgeIndex;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbDocumentChunkPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbDocumentChunkRetrievalRow;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbKnowledgeIndexActiveRow;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbDocumentChunkMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class KbDocumentChunkRepositoryAdapter implements KnowledgeDocumentChunkRepository,
        KnowledgeRetrievalCandidateRepository,
        KnowledgeDocumentVectorIndexRepository,
        KnowledgeIndexMaintenancePort {

    private final KbDocumentChunkMapper mapper;

    public KbDocumentChunkRepositoryAdapter(KbDocumentChunkMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "切片 Mapper 不能为空");
    }

    @Override
    public String nextChunkId() {
        return requiredGeneratedId(mapper.nextChunkId(), "知识文档切片 ID");
    }

    @Override
    public Optional<ActiveKnowledgeIndex> findActiveIndex(
            TenantId tenantId,
            String knowledgeBaseId
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String requiredKnowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        KbKnowledgeIndexActiveRow active = mapper.findActiveIndex(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(requiredKnowledgeBaseId, "知识库 ID")
        );
        if (active == null) {
            return Optional.empty();
        }
        return Optional.of(new ActiveKnowledgeIndex(active.getEmbeddingModel(), active.getActiveIndexVersion()));
    }

    @Override
    public List<KnowledgeDocumentChunk> saveAll(List<KnowledgeDocumentChunk> chunks) {
        Objects.requireNonNull(chunks, "切片列表不能为空");
        for (KnowledgeDocumentChunk chunk : chunks) {
            KbDocumentChunkPersistenceEntity entity = KbDocumentChunkPersistenceEntity.fromDomain(chunk);
            if (findActiveRow(entity).isEmpty()) {
                requiredInsertedRows(mapper.insert(entity), "知识文档切片");
            } else {
                int updated = mapper.update(entity, activeRowUpdate(entity));
                if (updated != 1) {
                    throw new IllegalStateException("知识文档切片保存失败：记录不存在或已被删除");
                }
            }
        }
        return chunks;
    }

    @Override
    public List<KnowledgeDocumentChunk> findByDocumentId(TenantId tenantId, String documentId) {
        List<KbDocumentChunkPersistenceEntity> rows = mapper.selectList(new LambdaQueryWrapper<KbDocumentChunkPersistenceEntity>()
                .eq(KbDocumentChunkPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbDocumentChunkPersistenceEntity::getDocumentId, parseRequiredLong(documentId, "文档 ID"))
                .isNull(KbDocumentChunkPersistenceEntity::getDeletedAt)
                .orderByAsc(KbDocumentChunkPersistenceEntity::getChunkNo));
        return requiredRows(rows, "文档切片查询结果")
                .stream()
                .map(KbDocumentChunkPersistenceEntity::toDomain)
                .toList();
    }

    @Override
    public void replaceByDocumentId(TenantId tenantId, String knowledgeBaseId, String documentId, List<KnowledgeDocumentChunk> chunks) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        requireText(knowledgeBaseId, "知识库 ID");
        requireText(documentId, "文档 ID");
        Objects.requireNonNull(chunks, "切片列表不能为空");
        Instant replacedAt = Instant.now();
        long tenantValue = parseRequiredLong(tenantId.value(), "租户 ID");
        long knowledgeBaseValue = parseRequiredLong(knowledgeBaseId, "知识库 ID");
        long documentValue = parseRequiredLong(documentId, "文档 ID");
        mapper.update(null, new LambdaUpdateWrapper<KbDocumentChunkPersistenceEntity>()
                .eq(KbDocumentChunkPersistenceEntity::getTenantId, tenantValue)
                .eq(KbDocumentChunkPersistenceEntity::getKnowledgeBaseId, knowledgeBaseValue)
                .eq(KbDocumentChunkPersistenceEntity::getDocumentId, documentValue)
                .isNull(KbDocumentChunkPersistenceEntity::getDeletedAt)
                .set(KbDocumentChunkPersistenceEntity::getDeletedAt, replacedAt)
                .set(KbDocumentChunkPersistenceEntity::getUpdatedAt, replacedAt));
        saveAll(chunks);
    }

    @Override
    public List<RetrievalCandidate> searchVectorCandidates(RetrievalSearchQuery query) {
        Objects.requireNonNull(query, "检索查询不能为空");
        List<KbDocumentChunkRetrievalRow> rows = mapper.searchVectorCandidates(
                parseRequiredLong(query.tenantId().value(), "租户 ID"),
                parseIds(query.knowledgeBaseIds(), "知识库 ID"),
                query.embeddingModel(),
                query.indexVersion(),
                toPgVectorLiteral(query.queryEmbedding()),
                query.aclTokens(),
                query.visibleAt(),
                query.scoreThreshold(),
                query.topK()
        );
        return requiredRows(rows, "向量召回查询结果")
                .stream()
                .map(row -> toCandidate(row, RetrievalSignal.VECTOR))
                .toList();
    }

    @Override
    public List<RetrievalCandidate> searchKeywordCandidates(RetrievalSearchQuery query) {
        Objects.requireNonNull(query, "检索查询不能为空");
        List<KbDocumentChunkRetrievalRow> rows = mapper.searchKeywordCandidates(
                parseRequiredLong(query.tenantId().value(), "租户 ID"),
                parseIds(query.knowledgeBaseIds(), "知识库 ID"),
                query.embeddingModel(),
                query.indexVersion(),
                query.query(),
                query.aclTokens(),
                query.visibleAt(),
                query.topK()
        );
        return requiredRows(rows, "关键词召回查询结果")
                .stream()
                .map(row -> toCandidate(row, RetrievalSignal.KEYWORD))
                .toList();
    }

    @Override
    public void updateChunkEmbedding(
            TenantId tenantId,
            String chunkId,
            String embeddingModel,
            String indexVersion,
            float[] embedding,
            Instant indexedAt,
            String operatorId
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String requiredEmbeddingModel = requireText(embeddingModel, "Embedding 模型");
        String requiredIndexVersion = requireText(indexVersion, "索引版本");
        Objects.requireNonNull(embedding, "文档切片向量不能为空");
        Objects.requireNonNull(indexedAt, "索引时间不能为空");
        if (embedding.length == 0) {
            throw new IllegalArgumentException("文档切片向量不能为空");
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("文档切片向量不能包含非有限数值");
            }
        }
        int updatedRows = mapper.updateChunkEmbedding(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(chunkId, "切片 ID"),
                requiredEmbeddingModel,
                requiredIndexVersion,
                toPgVectorLiteral(embedding),
                indexedAt,
                parseRequiredLong(operatorId, "操作人 ID")
        );
        if (updatedRows != 1) {
            throw new IllegalStateException("知识切片候选向量写入失败，chunkId=" + chunkId);
        }
    }

    @Override
    public int deleteObsoleteEmbeddings(Instant updatedBefore, int batchSize) {
        Objects.requireNonNull(updatedBefore, "旧索引清理截止时间不能为空");
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("旧索引清理批次大小必须在 1 到 500 之间");
        }
        int deleted = mapper.deleteObsoleteEmbeddings(updatedBefore, batchSize);
        if (deleted < 0 || deleted > batchSize) {
            throw new IllegalStateException("旧索引清理返回了非法影响行数");
        }
        return deleted;
    }

    @Override
    public boolean activateIfComplete(
            TenantId tenantId,
            String knowledgeBaseId,
            String embeddingModel,
            String indexVersion,
            Instant activatedAt,
            String operatorId
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String requiredKnowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        String requiredEmbeddingModel = requireText(embeddingModel, "Embedding 模型");
        String requiredIndexVersion = requireText(indexVersion, "索引版本");
        Objects.requireNonNull(activatedAt, "索引激活时间不能为空");
        String activatedVersion = mapper.activateIfComplete(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(requiredKnowledgeBaseId, "知识库 ID"),
                requiredEmbeddingModel,
                requiredIndexVersion,
                activatedAt,
                parseRequiredLong(operatorId, "操作人 ID")
        );
        if (activatedVersion == null) {
            return false;
        }
        if (!requiredIndexVersion.equals(requireText(activatedVersion, "已激活索引版本"))) {
            throw new IllegalStateException("数据库返回了非候选索引版本");
        }
        return true;
    }

    private static RetrievalCandidate toCandidate(KbDocumentChunkRetrievalRow row, RetrievalSignal signal) {
        return new RetrievalCandidate(
                requiredLongText(row.getTenantId(), "租户 ID"),
                requiredLongText(row.getKnowledgeBaseId(), "知识库 ID"),
                requiredLongText(row.getDocumentId(), "文档 ID"),
                requiredLongText(row.getId(), "切片 ID"),
                row.getContent(),
                requiredDouble(row.getScore(), "召回分数"),
                requiredInteger(row.getRank(), "召回排名"),
                signal,
                new KnowledgeDocumentChunkMetadata(
                        row.getSourceUri(),
                        requiredInteger(row.getDocumentVersion(), "文档版本号"),
                        row.getTitlePath(),
                        row.getParagraphNo(),
                        row.getTableCellRange(),
                        row.getClassification(),
                        row.getVisibility(),
                        decodeAclTokens(row.getAclTokens()),
                        requiredBoolean(row.getAuthoritative(), "权威来源标记"),
                        row.getPublishedAt(),
                        row.getExpiresAt(),
                        row.getEmbeddingModel(),
                        row.getChunkStrategyVersion(),
                        row.getIndexVersion()
                )
        );
    }

    private static List<Long> parseIds(List<String> values, String fieldName) {
        return values.stream().map(value -> parseRequiredLong(value, fieldName)).toList();
    }

    private static <T> List<T> requiredRows(List<T> rows, String fieldName) {
        if (rows == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return rows;
    }

    private Optional<KbDocumentChunkPersistenceEntity> findActiveRow(KbDocumentChunkPersistenceEntity entity) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KbDocumentChunkPersistenceEntity>()
                .eq(KbDocumentChunkPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbDocumentChunkPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbDocumentChunkPersistenceEntity::getDocumentId, entity.getDocumentId())
                .eq(KbDocumentChunkPersistenceEntity::getId, entity.getId())
                .isNull(KbDocumentChunkPersistenceEntity::getDeletedAt)
                .last("limit 1")));
    }

    private LambdaUpdateWrapper<KbDocumentChunkPersistenceEntity> activeRowUpdate(KbDocumentChunkPersistenceEntity entity) {
        return new LambdaUpdateWrapper<KbDocumentChunkPersistenceEntity>()
                .eq(KbDocumentChunkPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbDocumentChunkPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbDocumentChunkPersistenceEntity::getDocumentId, entity.getDocumentId())
                .eq(KbDocumentChunkPersistenceEntity::getId, entity.getId())
                .isNull(KbDocumentChunkPersistenceEntity::getDeletedAt);
    }

    private static String toPgVectorLiteral(float[] embedding) {
        return Arrays.toString(embedding);
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    private static Integer requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static double requiredDouble(Double value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static boolean requiredBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static List<String> decodeAclTokens(String tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("权限快照不能为空");
        }
        return Arrays.stream(tokens.split("\\R", -1))
                .map(KbDocumentChunkRepositoryAdapter::requireAclToken)
                .toList();
    }

    private static String requireAclToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("权限快照不能为空白");
        }
        return token.trim();
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return normalized;
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }
}
