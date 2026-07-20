\getenv bootstrap_admin_password_hash AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH

\if :bootstrap_credentials_only
UPDATE iam_user
SET password_hash = :'bootstrap_admin_password_hash',
    updated_at = now()
WHERE id = 1001
  AND tenant_id = 2001
  AND username = 'admin'
  AND password_hash IS DISTINCT FROM :'bootstrap_admin_password_hash';
\else

INSERT INTO iam_tenant (id, tenant_code, tenant_name, status, created_at, updated_at, version)
VALUES (2001, 'platform', '平台管理租户', 'ACTIVE', now(), now(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO iam_user (
  id,
  tenant_id,
  username,
  display_name,
  email,
  password_hash,
  status,
  created_at,
  updated_at,
  version
)
VALUES (
  1001,
  2001,
  'admin',
  '平台管理员',
  'admin@local.aip',
  :'bootstrap_admin_password_hash',
  'ACTIVE',
  now(),
  now(),
  0
)
ON CONFLICT DO NOTHING;

INSERT INTO iam_role (
  id,
  tenant_id,
  role_code,
  role_name,
  role_type,
  default_menu_code,
  status,
  description,
  created_at,
  updated_at,
  version
)
VALUES (
  3001,
  2001,
  'platform-admin',
  '平台管理员',
  'system',
  'admin.platform-overview',
  'ACTIVE',
  '平台初始化管理角色',
  now(),
  now(),
  0
)
ON CONFLICT DO NOTHING;

INSERT INTO iam_permission (
  id,
  permission_code,
  permission_name,
  resource_type,
  action_code,
  description,
  status,
  created_at,
  updated_at
)
VALUES
  (4001, 'identity-access:session:read', '读取当前登录态', 'session', 'read', '允许读取当前登录用户与租户信息', 'ACTIVE', now(), now()),
  (4002, 'identity-access:session:logout', '退出当前登录态', 'session', 'logout', '允许退出当前登录态', 'ACTIVE', now(), now()),
  (4003, 'identity-access:iam-config:read', '读取 IAM 配置', 'iam-config', 'read', '允许读取菜单、租户功能、角色和授权矩阵配置', 'ACTIVE', now(), now()),
  (4004, 'identity-access:iam-menu:manage', '管理 IAM 菜单', 'iam-menu', 'manage', '允许维护菜单、菜单权限和菜单功能绑定', 'ACTIVE', now(), now()),
  (4005, 'identity-access:tenant-feature:manage', '管理租户功能', 'tenant-feature', 'manage', '允许维护租户功能开关', 'ACTIVE', now(), now()),
  (4006, 'identity-access:role-menu:manage', '管理角色菜单', 'role-menu', 'manage', '允许维护本租户角色菜单矩阵', 'ACTIVE', now(), now()),
  (4007, 'identity-access:role-permission:manage', '管理角色权限', 'role-permission', 'manage', '允许维护本租户角色权限矩阵', 'ACTIVE', now(), now()),
  (4008, 'identity-access:role-default-menu:manage', '管理角色默认首页', 'role-default-menu', 'manage', '允许维护本租户角色默认首页', 'ACTIVE', now(), now()),
  (4009, 'observability-audit:security-log:read', '读取安全审计日志', 'security-log', 'read', '允许读取本租户安全审计日志', 'ACTIVE', now(), now()),
  (4010, 'model-hub:provider:create', '创建模型供应商', 'provider', 'create', '允许创建模型供应商配置', 'ACTIVE', now(), now()),
  (4013, 'model-hub:provider:read', '读取模型供应商', 'provider', 'read', '允许读取模型供应商配置列表', 'ACTIVE', now(), now()),
  (4015, 'model-hub:provider:disable', '停用模型供应商', 'provider', 'disable', '允许停用模型供应商配置', 'ACTIVE', now(), now()),
  (4011, 'model-hub:model-config:create', '创建模型配置', 'model-config', 'create', '允许在供应商下创建模型配置', 'ACTIVE', now(), now()),
  (4014, 'model-hub:model-config:read', '读取模型配置', 'model-config', 'read', '允许读取模型配置列表', 'ACTIVE', now(), now()),
  (4016, 'model-hub:model-config:update', '更新模型配置', 'model-config', 'update', '允许更新模型配置运行参数', 'ACTIVE', now(), now()),
  (4017, 'model-hub:model-config:disable', '停用模型配置', 'model-config', 'disable', '允许停用模型配置', 'ACTIVE', now(), now()),
  (4012, 'model-hub:model-config:test-connectivity', '测试模型连通性', 'model-config', 'test-connectivity', '允许执行模型连通性测试', 'ACTIVE', now(), now()),
  (4030, 'approval-center:task:read', '读取审批任务', 'approval-task', 'read', '允许读取审批中心任务列表', 'ACTIVE', now(), now()),
  (4031, 'approval-center:task:decide', '决策审批任务', 'approval-task', 'decide', '允许审批或拒绝审批中心任务', 'ACTIVE', now(), now()),
  (4020, 'knowledge-base:knowledge-base:create', '创建知识库', 'knowledge-base', 'create', '允许创建企业知识库', 'ACTIVE', now(), now()),
  (4025, 'knowledge-base:knowledge-base:read', '读取知识库', 'knowledge-base', 'read', '允许读取知识库列表和详情', 'ACTIVE', now(), now()),
  (4026, 'knowledge-base:knowledge-base:update', '更新知识库', 'knowledge-base', 'update', '允许更新知识库名称、说明、可见性和向量模型', 'ACTIVE', now(), now()),
  (4027, 'knowledge-base:knowledge-base:disable', '停用知识库', 'knowledge-base', 'disable', '允许停用知识库并阻止继续检索', 'ACTIVE', now(), now()),
  (4028, 'knowledge-base:document:read', '读取知识库文档', 'document', 'read', '允许读取知识库文档入库结果', 'ACTIVE', now(), now()),
  (4021, 'knowledge-base:document:upload', '上传知识库文档', 'document', 'upload', '允许上传知识库文档', 'ACTIVE', now(), now()),
  (4022, 'knowledge-base:document:parse', '解析知识库文档', 'document', 'parse', '允许解析知识库文档', 'ACTIVE', now(), now()),
  (4023, 'knowledge-base:document:index', '索引知识库文档', 'document', 'index', '允许触发知识库文档向量化索引', 'ACTIVE', now(), now()),
  (4029, 'knowledge-base:document:delete', '移除知识库文档', 'document', 'delete', '允许从知识库中移除文档', 'ACTIVE', now(), now()),
  (4024, 'knowledge-base:retrieval:query', '查询知识库检索', 'retrieval', 'query', '允许执行知识库检索问答', 'ACTIVE', now(), now()),
  (4043, 'app-studio:application:read', '查看 AI 应用', 'application', 'read', '允许查看 AI 应用及其会话历史', 'ACTIVE', now(), now()),
  (4040, 'app-studio:application:create', '创建 AI 应用', 'application', 'create', '允许创建 AI 应用', 'ACTIVE', now(), now()),
  (4044, 'app-studio:application:update', '更新 AI 应用', 'application', 'update', '允许更新 AI 应用配置', 'ACTIVE', now(), now()),
  (4045, 'app-studio:application:publish', '发布 AI 应用', 'application', 'publish', '允许发布 AI 应用', 'ACTIVE', now(), now()),
  (4041, 'app-studio:application:use', '使用 AI 应用', 'application', 'use', '允许基于 AI 应用创建会话', 'ACTIVE', now(), now()),
  (4042, 'app-studio:model:use', '调用 AI 应用模型', 'model', 'use', '允许在 AI 应用会话中发起模型调用', 'ACTIVE', now(), now()),
  (4060, 'summary-ops:platform-overview:read', '读取平台总览汇总', 'summary-platform-overview', 'read', '允许读取平台级运营和安全汇总', 'ACTIVE', now(), now()),
  (4061, 'summary-ops:tenant-workbench:read', '读取租户工作台汇总', 'summary-tenant-workbench', 'read', '允许读取当前租户工作台汇总', 'ACTIVE', now(), now()),
  (4063, 'app-studio:debug-evidence:read', '读取调试证据', 'debug-evidence', 'read', '允许读取 AI 应用完整调试证据，必须填写原因并审计', 'ACTIVE', now(), now()),
  (4064, 'app-studio:application:manage-acl', '维护 AI 应用授权', 'application', 'manage-acl', '允许应用创建者维护用户和角色使用授权，必须填写原因并审计', 'ACTIVE', now(), now()),
  (4065, 'identity-access:permission-request:create', '提交权限申请', 'permission-request', 'create', '允许提交租户内权限受理工单', 'ACTIVE', now(), now()),
  (4066, 'identity-access:permission-request:manage', '处理权限申请', 'permission-request', 'manage', '允许读取并决策本租户权限受理工单', 'ACTIVE', now(), now()),
  (4067, 'knowledge-base:document:cancel', '取消知识文档作业', 'document', 'cancel', '允许管理员填写理由并取消本租户非终态知识文档作业', 'ACTIVE', now(), now()),
  (4050, 'identity-access:platform-admin:use', '使用平台管理员控制台', 'console', 'platform-admin-use', '允许访问平台管理员总览和全局管理菜单', 'ACTIVE', now(), now()),
  (4051, 'identity-access:tenant-admin:use', '使用租户管理员控制台', 'console', 'tenant-admin-use', '允许访问租户管理员工作台和租户级配置菜单', 'ACTIVE', now(), now()),
  (4052, 'identity-access:workspace:use', '使用个人工作台', 'console', 'workspace-use', '允许普通用户访问个人 AI 工作台', 'ACTIVE', now(), now()),
  (4053, 'identity-access:system-setting:manage', '管理平台系统设置', 'console', 'system-setting-manage', '允许平台管理员访问权限、菜单和功能配置入口', 'ACTIVE', now(), now()),
  (4054, 'identity-access:tenant-setting:manage', '管理租户设置', 'console', 'tenant-setting-manage', '允许租户管理员访问本租户权限、菜单和功能配置入口', 'ACTIVE', now(), now()),
  (4062, 'identity-access:tenant-delegation:manage', '代管租户配置', 'tenant-delegation', 'manage', '允许平台管理员带原因代管租户角色、权限或管理员配置', 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO iam_menu (
  id,
  menu_code,
  menu_name,
  scope,
  path,
  route_name,
  component_key,
  icon,
  required_permission_code,
  sort_order,
  status,
  system_builtin,
  created_at,
  updated_at,
  version
)
VALUES
  (5001, 'admin.platform-overview', '平台总览', 'ADMIN', '/admin/platform-overview', 'AdminPlatformOverview', 'admin/platform-overview', 'dashboard', 'identity-access:platform-admin:use', 1, 'ACTIVE', true, now(), now(), 0),
  (5002, 'tenant.overview', '租户工作台', 'TENANT', '/tenant/overview', 'TenantOverview', 'tenant/overview', 'team', 'identity-access:tenant-admin:use', 5, 'ACTIVE', true, now(), now(), 0),
  (5003, 'business.my-workspace', '我的工作台', 'BUSINESS', '/workspace/home', 'BusinessMyWorkspace', 'workspace/home', 'user', 'identity-access:workspace:use', 8, 'ACTIVE', true, now(), now(), 0),
  (5004, 'business.knowledge-bases', '知识库', 'BUSINESS', '/workspace/knowledge-bases', 'BusinessKnowledgeBases', 'business/knowledge-bases/list', 'database', 'knowledge-base:retrieval:query', 10, 'ACTIVE', true, now(), now(), 0),
  (5005, 'admin.model-providers', '模型', 'ADMIN', '/admin/model-providers', 'AdminModelProviders', 'admin/model-providers/list', 'experiment', 'model-hub:provider:read', 20, 'ACTIVE', true, now(), now(), 0),
  (5006, 'tenant.settings', '租户设置', 'TENANT', '/tenant/settings', 'TenantSettings', 'admin/system-settings', 'setting', 'identity-access:tenant-setting:manage', 25, 'ACTIVE', true, now(), now(), 0),
  (5007, 'business.app-studio', 'AI应用', 'BUSINESS', '/workspace/app-studio', 'BusinessAppStudio', 'business/app-studio/list', 'appstore', 'app-studio:application:read', 30, 'ACTIVE', true, now(), now(), 0),
  (5008, 'admin.approvals', '审批', 'ADMIN', '/admin/approvals', 'AdminApprovals', 'admin/approvals/list', 'safety-certificate', 'approval-center:task:read', 40, 'ACTIVE', true, now(), now(), 0),
  (5009, 'admin.system-settings', '系统设置', 'ADMIN', '/admin/system-settings', 'AdminSystemSettings', 'admin/system-settings', 'setting', 'identity-access:system-setting:manage', 50, 'ACTIVE', true, now(), now(), 0),
  (5010, 'admin.tenants', '租户与代管', 'ADMIN', '/admin/tenants', 'AdminTenants', 'admin/tenants', 'team', 'identity-access:tenant-delegation:manage', 10, 'ACTIVE', true, now(), now(), 0),
  (5011, 'tenant.users', '用户管理', 'TENANT', '/tenant/users', 'TenantUsers', 'tenant/users', 'user', 'identity-access:tenant-setting:manage', 10, 'ACTIVE', true, now(), now(), 0),
  (5012, 'tenant.roles', '角色权限', 'TENANT', '/tenant/roles', 'TenantRoles', 'tenant/roles', 'safety-certificate', 'identity-access:tenant-setting:manage', 15, 'ACTIVE', true, now(), now(), 0),
  (5013, 'tenant.permission-requests', '权限申请', 'TENANT', '/tenant/permission-requests', 'TenantPermissionRequests', 'tenant/permission-requests', 'audit', 'identity-access:permission-request:manage', 40, 'ACTIVE', true, now(), now(), 0)
ON CONFLICT DO NOTHING;

SELECT setval(
  pg_get_serial_sequence('iam_menu', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_menu), 1),
  true
);

INSERT INTO iam_menu_feature (menu_id, feature_code, created_at)
SELECT m.id, v.feature_code, now()
FROM iam_menu m
JOIN (
  VALUES
    ('business.knowledge-bases', 'knowledge-base'),
    ('admin.model-providers', 'model-hub'),
    ('tenant.settings', 'identity-settings'),
    ('business.app-studio', 'app-studio'),
    ('admin.approvals', 'approval-center'),
    ('admin.system-settings', 'identity-settings'),
    ('admin.tenants', 'identity-settings'),
    ('tenant.users', 'identity-settings'),
    ('tenant.roles', 'identity-settings'),
    ('tenant.permission-requests', 'identity-settings')
) AS v(menu_code, feature_code) ON v.menu_code = m.menu_code
ON CONFLICT DO NOTHING;

INSERT INTO iam_tenant_feature (
  tenant_id,
  feature_code,
  feature_name,
  enabled,
  source,
  created_at,
  updated_at,
  version
)
VALUES
  (2001, 'model-hub', '模型供应商', true, 'BOOTSTRAP', now(), now(), 0),
  (2001, 'approval-center', '审批中心', true, 'BOOTSTRAP', now(), now(), 0),
  (2001, 'knowledge-base', '知识库', true, 'BOOTSTRAP', now(), now(), 0),
  (2001, 'app-studio', 'AI 应用', true, 'BOOTSTRAP', now(), now(), 0),
  (2001, 'identity-settings', '身份与租户设置', true, 'BOOTSTRAP', now(), now(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO iam_role_menu (tenant_id, role_id, menu_id, created_at, updated_at, version)
SELECT 2001, 3001, menu.id, now(), now(), 0
FROM iam_menu AS menu
WHERE menu.scope = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO iam_user_role (
  id,
  tenant_id,
  user_id,
  role_id,
  grant_source,
  created_at,
  updated_at,
  version
)
VALUES (3101, 2001, 1001, 3001, 'bootstrap', now(), now(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO iam_role_permission (
  tenant_id,
  role_id,
  permission_id,
  created_at,
  updated_at,
  version
)
SELECT 2001, 3001, permission.id, now(), now(), 0
FROM iam_permission AS permission
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('iam_tenant', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_tenant), 1), true);
SELECT setval(pg_get_serial_sequence('iam_user', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_user), 1), true);
SELECT setval(pg_get_serial_sequence('iam_role', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_role), 1), true);
SELECT setval(pg_get_serial_sequence('iam_permission', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_permission), 1), true);
SELECT setval(pg_get_serial_sequence('iam_user_role', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM iam_user_role), 1), true);

\endif
