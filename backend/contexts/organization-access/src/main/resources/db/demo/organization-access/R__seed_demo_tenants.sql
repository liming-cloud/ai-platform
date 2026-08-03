-- organization-access 演示基线：两个历史演示租户已完成受控生命周期回填及认证切换。
-- 影响：演示环境直接携带可复核的合法事件序列；不替代生产环境所需的人工主体映射和原子回填。
-- 回滚：仅用于可重建演示数据库，删除演示库后重新执行 Flyway 即可恢复。

INSERT INTO organization_access.tenant (
    tenant_id,
    tenant_code,
    display_name,
    status,
    version
) VALUES
    (100000000000000001, 'demo-store', '门店演示租户', 'ENABLED', 0),
    (100000000000000002, 'demo-campus', '校园演示租户', 'ENABLED', 0)
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.subject_identity (
    subject_id,
    login_name,
    password_hash,
    status,
    version
) VALUES
    (
        4921825928971026434,
        'admin',
        'pbkdf2-sha256$v1$600000$xCeuZmvRbROcHBGNFfKU2A$OWzHrRHZCZ38xi3NaEHw1qEzE_-3X7F6vM37aNz--bk',
        'ACTIVE',
        0
    ),
    (
        4921825928971026436,
        'platform-admin',
        'pbkdf2-sha256$v1$600000$xCeuZmvRbROcHBGNFfKU2A$OWzHrRHZCZ38xi3NaEHw1qEzE_-3X7F6vM37aNz--bk',
        'ACTIVE',
        0
    )
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.platform_access_grant (
    subject_id,
    status,
    permission_version,
    version
) VALUES (
    4921825928971026436,
    'ENABLED',
    1,
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.platform_access_permission (
    subject_id,
    permission_code
) VALUES (
    4921825928971026436,
    'tenant-governance:write'
)
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.tenant_membership (
    membership_id,
    tenant_id,
    subject_id,
    membership_role,
    status,
    permission_version,
    version
) VALUES
    (
        3382738893456539650,
        100000000000000001,
        4921825928971026434,
        'MEMBER',
        'ENABLED',
        1,
        0
    ),
    (
        3382738893456539651,
        100000000000000002,
        4921825928971026434,
        'MEMBER',
        'ENABLED',
        1,
        0
    ),
    (
        3382738893456539652,
        100000000000000001,
        4921825928971026436,
        'MEMBER',
        'ENABLED',
        1,
        0
    )
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.membership_permission (
    tenant_id,
    membership_id,
    permission_code
) VALUES
    (
        100000000000000001,
        3382738893456539650,
        'knowledge:read'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'knowledge:write'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'model:read'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'model:write'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'model-tenant:write'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'model-personal:write'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'assistant:read'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'assistant:write'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'assistant:query'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'usage:read'
    ),
    (
        100000000000000001,
        3382738893456539650,
        'audit:read'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'knowledge:read'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'knowledge:write'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'model:read'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'model:write'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'model-tenant:write'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'model-personal:write'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'assistant:read'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'assistant:write'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'assistant:query'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'usage:read'
    ),
    (
        100000000000000002,
        3382738893456539651,
        'audit:read'
    ),
    (
        100000000000000001,
        3382738893456539652,
        'model-platform:write'
    )
ON CONFLICT DO NOTHING;

UPDATE organization_access.tenant_membership
SET permission_version = 2
WHERE tenant_id IN (
          100000000000000001,
          100000000000000002
      )
  AND subject_id IN (4921825928971026434, 4921825928971026436)
  AND permission_version < 2;

UPDATE organization_access.tenant_membership
SET membership_role = 'TENANT_ADMINISTRATOR',
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id IN (
          100000000000000001,
          100000000000000002
      )
  AND subject_id = 4921825928971026434
  AND membership_role = 'MEMBER';

INSERT INTO organization_access.tenant_lifecycle (
    tenant_id,
    tenant_code,
    display_name,
    initial_administrator_subject_id,
    status,
    version,
    created_at
) VALUES
    (
        100000000000000001,
        'demo-store',
        '门店演示租户',
        4921825928971026434,
        'ACTIVE',
        1,
        TIMESTAMPTZ '2026-07-27T00:00:00Z'
    ),
    (
        100000000000000002,
        'demo-campus',
        '校园演示租户',
        4921825928971026434,
        'ACTIVE',
        1,
        TIMESTAMPTZ '2026-07-27T00:00:00Z'
    )
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.tenant_lifecycle_outbox (
    event_id,
    tenant_id,
    event_type,
    lifecycle_status,
    lifecycle_version,
    occurred_at
) VALUES
    (3382738893456539700, 100000000000000001, 'TENANT_LIFECYCLE_CHANGED', 'PROVISIONING', 0,
        TIMESTAMPTZ '2026-08-03T00:00:00Z'),
    (3382738893456539701, 100000000000000001, 'TENANT_LIFECYCLE_CHANGED', 'ACTIVE', 1,
        TIMESTAMPTZ '2026-08-03T00:00:00Z'),
    (3382738893456539702, 100000000000000002, 'TENANT_LIFECYCLE_CHANGED', 'PROVISIONING', 0,
        TIMESTAMPTZ '2026-08-03T00:00:00Z'),
    (3382738893456539703, 100000000000000002, 'TENANT_LIFECYCLE_CHANGED', 'ACTIVE', 1,
        TIMESTAMPTZ '2026-08-03T00:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.tenant_lifecycle_authentication_cutover (
    singleton_key,
    activated_at
) VALUES (
    TRUE,
    TIMESTAMPTZ '2026-08-03T00:00:00Z'
)
ON CONFLICT DO NOTHING;
