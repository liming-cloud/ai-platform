-- 所属上下文：model-runtime；目的：允许受控模型部署进入不可逆停用状态；影响：保留既有部署与验证历史；可回滚：需在确认没有 DISABLED 记录后恢复原约束。
ALTER TABLE model_runtime.model_deployment
    DROP CONSTRAINT ck_model_deployment_status;

ALTER TABLE model_runtime.model_deployment
    ADD CONSTRAINT ck_model_deployment_status
        CHECK (status IN ('DRAFT', 'VERIFIED', 'PUBLISHED', 'DISABLED'));

ALTER TABLE model_runtime.model_deployment
    DROP CONSTRAINT ck_model_deployment_verification;

ALTER TABLE model_runtime.model_deployment
    ADD CONSTRAINT ck_model_deployment_verification
        CHECK ((status = 'DRAFT' AND last_verification_id IS NULL AND verification_expires_at IS NULL)
            OR (status IN ('VERIFIED', 'PUBLISHED')
                AND last_verification_id IS NOT NULL
                AND verification_expires_at IS NOT NULL)
            OR (status = 'DISABLED'
                AND ((last_verification_id IS NULL AND verification_expires_at IS NULL)
                    OR (last_verification_id IS NOT NULL AND verification_expires_at IS NOT NULL))));
