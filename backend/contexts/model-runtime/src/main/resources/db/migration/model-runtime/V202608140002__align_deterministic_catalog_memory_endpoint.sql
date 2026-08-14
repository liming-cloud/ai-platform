-- 所属上下文：model-runtime。目的：使受控模型目录的端点约束与确定性本地供应链保持一致。
-- 数据影响：仅 `DETERMINISTIC` 可持久化不含敏感 URI 组成部分的 `memory://` 端点；其余端点仍须使用 HTTPS。
-- 回滚限制：恢复旧约束前必须先处理已经建立的确定性内存目录条目。

ALTER TABLE model_runtime.controlled_model_catalog_entry
    DROP CONSTRAINT ck_controlled_model_catalog_endpoint;

ALTER TABLE model_runtime.controlled_model_catalog_entry
    ADD CONSTRAINT ck_controlled_model_catalog_endpoint
        CHECK (
            (
                provider_kind = 'DETERMINISTIC'
                AND endpoint LIKE 'memory://%'
                AND position('@' IN endpoint) = 0
                AND position('?' IN endpoint) = 0
                AND position('#' IN endpoint) = 0
            )
            OR (
                endpoint LIKE 'https://%'
                AND position('@' IN endpoint) = 0
                AND position('?' IN endpoint) = 0
                AND position('#' IN endpoint) = 0
            )
        );
