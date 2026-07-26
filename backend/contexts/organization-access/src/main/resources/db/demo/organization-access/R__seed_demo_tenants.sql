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
        '30000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'admin',
        'pbkdf2-sha256$v1$600000$xCeuZmvRbROcHBGNFfKU2A$OWzHrRHZCZ38xi3NaEHw1qEzE_-3X7F6vM37aNz--bk',
        'ENABLED',
        1,
        0
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
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
        '30000000-0000-0000-0000-000000000001',
        'knowledge:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'knowledge:write'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'model:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'model:write'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'assistant:read'
    ),
    (
        '10000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'assistant:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'knowledge:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'knowledge:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'model:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'model:write'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'assistant:read'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'assistant:write'
    )
ON CONFLICT DO NOTHING;
