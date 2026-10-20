package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderModelCandidateRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 当前修订的未过期候选先过滤再分页；数据和计数使用相同的安全范围。 */
public interface PlatformProviderModelCandidateMapper {
    /** 已发布连接的当前完整候选，普通分页由 MyBatis-Plus 生成。 */
    IPage<com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformDeployableModelRow> selectDeployablePage(
            IPage<com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformDeployableModelRow> page, @Param("now") Instant now);

    /** 不读取任何凭据，返回固定排序的有界候选窗口。 */
    IPage<PlatformProviderModelCandidateRow> selectCandidatePage(IPage<PlatformProviderModelCandidateRow> page,
            @Param("connectionId") long connectionId, @Param("revision") long revision,
            @Param("now") Instant now, @Param("q") String q);
    /** 冻结一次发现根，调用方与连接 CAS 共用同一事务。 */
    int insertDiscovery(@Param("id") long id, @Param("connectionId") long connectionId,
            @Param("revision") long revision, @Param("succeeded") boolean succeeded,
            @Param("resultCode") String resultCode, @Param("elapsedMillis") long elapsedMillis,
            @Param("discoveredAt") Instant discoveredAt, @Param("expiresAt") Instant expiresAt);

    /** 名称为空表示来源未提供；不得在存储中使用编码补齐或更新历史名称。 */
    int insertCandidate(@Param("discoveryId") long discoveryId, @Param("modelCode") String modelCode,
            @Param("modelType") String modelType, @Param("capabilities") String capabilities,
            @Param("classification") String classification, @Param("displayName") String displayName);

    /** 仍按编码精确选择当前未过期的已确认候选。 */
    PlatformProviderModelCandidateRow selectCurrentCandidate(@Param("connectionId") long connectionId,
            @Param("revision") long revision, @Param("modelCode") String modelCode,
            @Param("modelType") String modelType, @Param("now") Instant now);

    /** 当前完整发现根的草稿候选，允许待确认分类，但不回退旧发现。 */
    PlatformProviderModelCandidateRow selectDraftCandidate(@Param("connectionId") long connectionId,
            @Param("revision") long revision, @Param("modelCode") String modelCode,
            @Param("modelType") String modelType, @Param("now") Instant now);

    /** 单独保存当前候选的分类事实，原始名称与编码保持不变。 */
    int confirmCandidate(@Param("connectionId") long connectionId, @Param("revision") long revision,
            @Param("modelCode") String modelCode, @Param("modelType") String modelType,
            @Param("capability") String capability, @Param("subjectId") long subjectId,
            @Param("confirmedAt") Instant confirmedAt, @Param("now") Instant now);
}
