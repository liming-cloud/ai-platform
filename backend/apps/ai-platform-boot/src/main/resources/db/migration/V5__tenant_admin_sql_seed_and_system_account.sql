ALTER TABLE iam_user
    ADD COLUMN IF NOT EXISTS system_account BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
DECLARE
    demo_tenant_id BIGINT;
    admin_user_id BIGINT;
    platform_admin_role_id BIGINT;
BEGIN
    INSERT INTO platform_tenant (id, tenant_code, tenant_name, status, created_at, updated_at)
    VALUES (1000000000000000001, 'demo', '演示租户', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_code) DO UPDATE
    SET tenant_name = EXCLUDED.tenant_name,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO demo_tenant_id;

    INSERT INTO iam_user (id, tenant_id, username, display_name, status, system_account, created_at, updated_at)
    VALUES (1000000000000000002, demo_tenant_id, 'admin', '平台管理员', 'ENABLED', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_id, username) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        status = EXCLUDED.status,
        system_account = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO admin_user_id;

    INSERT INTO iam_user_credential (id, tenant_id, user_id, password_hash, updated_at)
    VALUES (
        1000000000000000003,
        demo_tenant_id,
        admin_user_id,
        'pbkdf2$120000$YWktcGxhdGZvcm0tYWRtaQ==$J3lp16+0pNoYj0n5iKrtkcmsMzJlhkXP/JHzYeXPLW8=',
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (tenant_id, user_id) DO NOTHING;

    INSERT INTO iam_role (id, tenant_id, role_code, role_name, status, created_at, updated_at)
    VALUES (1000000000000000004, demo_tenant_id, 'PLATFORM_ADMIN', '平台管理员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_id, role_code) DO UPDATE
    SET role_name = EXCLUDED.role_name,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO platform_admin_role_id;

    INSERT INTO iam_permission (id, tenant_id, permission_code, permission_name, status, created_at, updated_at)
    VALUES
        (1000000000000001101, demo_tenant_id, 'platform:tenant:read', '查看租户', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001102, demo_tenant_id, 'platform:tenant:write', '维护租户', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001103, demo_tenant_id, 'rbac:role:read', '查看角色', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001104, demo_tenant_id, 'rbac:role:write', '维护角色', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001105, demo_tenant_id, 'rbac:permission:read', '查看权限点', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001106, demo_tenant_id, 'rbac:permission:write', '维护权限点', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001107, demo_tenant_id, 'rbac:menu:read', '查看菜单', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001108, demo_tenant_id, 'rbac:menu:write', '维护菜单', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001109, demo_tenant_id, 'model:platform-catalog:read', '查看平台模型基础配置', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001110, demo_tenant_id, 'model:platform-catalog:write', '维护平台模型基础配置', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001111, demo_tenant_id, 'model:tenant-config:read', '查看租户模型配置', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001112, demo_tenant_id, 'model:tenant-config:write', '维护租户模型配置', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000001113, demo_tenant_id, 'model:invocation:execute', '执行模型调用', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_id, permission_code) DO UPDATE
    SET permission_name = EXCLUDED.permission_name,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP;

    INSERT INTO iam_menu (id, tenant_id, menu_code, menu_name, parent_code, route_path, component_key, platform_scope, sort_order, status, created_at, updated_at)
    VALUES
        (1000000000000002101, demo_tenant_id, 'platform.root', '平台管理', NULL, '/platform', 'PlatformLayout', 'PLATFORM', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002102, demo_tenant_id, 'platform.tenants', '租户管理', 'platform.root', '/platform/tenants', 'PlatformTenants', 'PLATFORM', 11, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002103, demo_tenant_id, 'platform.roles', '角色管理', 'platform.root', '/platform/roles', 'PlatformRoles', 'PLATFORM', 12, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002104, demo_tenant_id, 'platform.permissions', '权限管理', 'platform.root', '/platform/permissions', 'PlatformPermissions', 'PLATFORM', 13, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002105, demo_tenant_id, 'platform.menus', '菜单管理', 'platform.root', '/platform/menus', 'PlatformMenus', 'PLATFORM', 14, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002106, demo_tenant_id, 'platform.model-catalog', '模型基础配置', 'platform.root', '/platform/model-catalog', 'PlatformModelCatalog', 'PLATFORM', 15, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002107, demo_tenant_id, 'tenant.root', '租户平台', NULL, '/tenant', 'TenantLayout', 'TENANT', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (1000000000000002108, demo_tenant_id, 'tenant.models', '模型配置', 'tenant.root', '/tenant/models', 'TenantModels', 'TENANT', 21, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_id, menu_code) DO UPDATE
    SET menu_name = EXCLUDED.menu_name,
        parent_code = EXCLUDED.parent_code,
        route_path = EXCLUDED.route_path,
        component_key = EXCLUDED.component_key,
        platform_scope = EXCLUDED.platform_scope,
        sort_order = EXCLUDED.sort_order,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP;

    UPDATE iam_menu
    SET status = 'DISABLED',
        updated_at = CURRENT_TIMESTAMP
    WHERE tenant_id = demo_tenant_id
      AND menu_code = 'platform.rbac';

    INSERT INTO iam_user_role (id, tenant_id, user_id, role_id, created_at)
    VALUES (1000000000000005101, demo_tenant_id, admin_user_id, platform_admin_role_id, CURRENT_TIMESTAMP)
    ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;

    INSERT INTO iam_role_permission (id, tenant_id, role_id, permission_id, created_at)
    SELECT 1000000000000003100 + ROW_NUMBER() OVER (ORDER BY permission_code),
           demo_tenant_id,
           platform_admin_role_id,
           id,
           CURRENT_TIMESTAMP
    FROM iam_permission
    WHERE tenant_id = demo_tenant_id
      AND permission_code IN (
          'platform:tenant:read',
          'platform:tenant:write',
          'rbac:role:read',
          'rbac:role:write',
          'rbac:permission:read',
          'rbac:permission:write',
          'rbac:menu:read',
          'rbac:menu:write',
          'model:platform-catalog:read',
          'model:platform-catalog:write',
          'model:tenant-config:read',
          'model:tenant-config:write',
          'model:invocation:execute'
      )
    ON CONFLICT (tenant_id, role_id, permission_id) DO NOTHING;

    INSERT INTO iam_role_menu (id, tenant_id, role_id, menu_id, created_at)
    SELECT 1000000000000004100 + ROW_NUMBER() OVER (ORDER BY sort_order, menu_code),
           demo_tenant_id,
           platform_admin_role_id,
           id,
           CURRENT_TIMESTAMP
    FROM iam_menu
    WHERE tenant_id = demo_tenant_id
      AND menu_code IN (
          'platform.root',
          'platform.tenants',
          'platform.roles',
          'platform.permissions',
          'platform.menus',
          'platform.model-catalog',
          'tenant.root',
          'tenant.models'
      )
    ON CONFLICT (tenant_id, role_id, menu_id) DO NOTHING;
END $$;
