-- 所属上下文：知识数据。目的：记录受治理来源最后失败的操作，确保恢复严格回到探测或同步的原运行状态。
-- 数据影响：已有来源初始化为空；不可回滚，生产环境应使用前向迁移恢复或替代错误状态。
ALTER TABLE knowledge_data.governed_data_source
    ADD COLUMN failed_operation VARCHAR(32);

ALTER TABLE knowledge_data.governed_data_source
    ADD CONSTRAINT ck_governed_data_source_failed_operation
    CHECK (
        (status = 'FAILED' AND failed_operation IN ('PROBE', 'SYNCHRONIZATION'))
        OR (status <> 'FAILED' AND failed_operation IS NULL)
    );
