INSERT INTO organization_access.tenant (
    tenant_id,
    tenant_code,
    display_name,
    status,
    version
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'demo-store', '门店演示租户', 'ENABLED', 0),
    ('10000000-0000-0000-0000-000000000002', 'demo-campus', '校园演示租户', 'ENABLED', 0)
ON CONFLICT DO NOTHING;

INSERT INTO organization_access.membership_credential (
    membership_id,
    tenant_id,
    subject_id,
    login_name,
    password_hash,
    status,
    permission_version,
    version
) VALUES
    (
        3382738893456539650,
        '10000000-0000-0000-0000-000000000001',
        4921825928971026434,
        'admin',
        'pbkdf2-sha256$v1$600000$xCeuZmvRbROcHBGNFfKU2A$OWzHrRHZCZ38xi3NaEHw1qEzE_-3X7F6vM37aNz--bk',
        'ENABLED',
        1,
        0
    ),
    (
        3382738893456539651,
        '10000000-0000-0000-0000-000000000002',
        4921825928971026435,
        'admin',
        'pbkdf2-sha256$v1$600000$GAa1p3JpTYWan32T2ZpTMQ$CiQUoyizrJQJ4FgJmi_Xx21uNiChpNcRFwCchLmukb4',
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
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'knowledge:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'knowledge:write'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'model:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'model:write'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'assistant:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'assistant:write'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'assistant:query'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'usage:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        3382738893456539650,
        'audit:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'knowledge:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'knowledge:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'model:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'model:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'assistant:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'assistant:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'assistant:query'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'usage:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        3382738893456539651,
        'audit:read'
    )
ON CONFLICT DO NOTHING;

UPDATE organization_access.membership_credential
SET permission_version = 2
WHERE tenant_id IN (
          '10000000-0000-0000-0000-000000000001',
          '10000000-0000-0000-0000-000000000002'
      )
  AND login_name = 'admin'
  AND permission_version < 2;
