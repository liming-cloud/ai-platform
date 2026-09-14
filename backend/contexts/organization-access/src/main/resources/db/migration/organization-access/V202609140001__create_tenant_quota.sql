-- 所属上下文：organization-access。依据 ADR-0080，为租户原子开户保存独立资源配额。
-- 既有租户显式回填试点默认值；不改变身份、凭据、生命周期或认证切换事实。
-- 新开户必须同时显式写入配额。删除本表会丢失已配置配额，不能作为无损自动回滚。
CREATE TABLE organization_access.tenant_quota (
    tenant_id BIGINT PRIMARY KEY,
    knowledge_bases INTEGER NOT NULL,
    documents_per_knowledge_base INTEGER NOT NULL,
    bytes_per_knowledge_base BIGINT NOT NULL,
    max_file_bytes BIGINT NOT NULL,
    max_text_bytes BIGINT NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_oa_tenant_quota_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES organization_access.tenant (tenant_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_oa_tenant_quota_knowledge_bases
        CHECK (knowledge_bases > 0),
    CONSTRAINT ck_oa_tenant_quota_documents
        CHECK (documents_per_knowledge_base > 0),
    CONSTRAINT ck_oa_tenant_quota_bytes
        CHECK (bytes_per_knowledge_base > 0),
    CONSTRAINT ck_oa_tenant_quota_file_bytes
        CHECK (max_file_bytes > 0 AND max_file_bytes <= bytes_per_knowledge_base),
    CONSTRAINT ck_oa_tenant_quota_text_bytes
        CHECK (max_text_bytes > 0),
    CONSTRAINT ck_oa_tenant_quota_version
        CHECK (version >= 0)
);

-- 只初始化迁移时已经存在的租户，后续写入不能依赖隐藏默认配置。
INSERT INTO organization_access.tenant_quota (
    tenant_id, knowledge_bases, documents_per_knowledge_base,
    bytes_per_knowledge_base, max_file_bytes, max_text_bytes, version
)
SELECT tenant_id, 3, 1000, 10000000000, 50000000, 1048576, 0
FROM organization_access.tenant;
