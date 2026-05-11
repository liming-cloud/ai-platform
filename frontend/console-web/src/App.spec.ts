import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders platform and tenant menus from RBAC and uses catalog options for model config', async () => {
    const tenantOptions = optionsFixture();
    const platformCatalog = adminModelCatalogFixture();
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method || 'GET';
      if (url === '/api/auth/login') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-login',
          data: {
            tokenName: 'satoken',
            tokenValue: 'token-demo',
            tenantId: '1001',
            tenantCode: 'demo',
            userId: '2001',
            username: 'admin',
            displayName: '平台管理员'
          }
        });
      }
      if (url === '/api/auth/me') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-me',
          data: currentUserFixture()
        });
      }
      if (url === '/api/tenants/current') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-tenant',
          data: {
            tenantId: '1001',
            tenantCode: 'demo',
            tenantName: '演示租户',
            status: 'ENABLED'
          }
        });
      }
      if (url === '/api/model-providers/options') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-options', data: tenantOptions });
      }
      if (url === '/api/model-providers' && method === 'GET') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-providers', data: [] });
      }
      if (url === '/api/admin/model-catalog' && method === 'GET') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-platform-catalog', data: platformCatalog });
      }
      if (url === '/api/admin/model-catalog/provider-types' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.providerType).toBe('OPENAI_COMPATIBLE');
        expect(request.displayName).toBe('硅基流动');
        expect(request.defaultBaseUrl).toBe('https://api.siliconflow.cn');
        platformCatalog.providerTypes[1] = {
          ...platformCatalog.providerTypes[1],
          displayName: request.displayName,
          defaultBaseUrl: request.defaultBaseUrl,
          status: request.status,
          sortOrder: request.sortOrder
        };
        tenantOptions.providerTypes[1] = platformCatalog.providerTypes[1];
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-platform-provider-save', data: platformCatalog.providerTypes[1] });
      }
      if (url === '/api/admin/model-catalog/model-options' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.providerTypeId).toBe('5002');
        expect(request.modelCode).toBe('Qwen/Qwen2.5-7B-Instruct');
        expect(request.capabilityType).toBe('CHAT');
        const option = {
          modelOptionId: '5003',
          providerTypeId: '5002',
          modelCode: request.modelCode,
          displayName: request.displayName,
          capabilityType: request.capabilityType,
          contextWindow: request.contextWindow,
          maxOutputTokens: request.maxOutputTokens,
          embeddingDimensions: null,
          temperature: request.temperature,
          topP: request.topP,
          topK: null,
          frequencyPenalty: null,
          presencePenalty: null,
          reasoningEffort: null,
          status: request.status,
          sortOrder: request.sortOrder
        };
        platformCatalog.modelOptions.push(option);
        tenantOptions.modelOptions.push(option);
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-platform-model-save', data: option });
      }
      if (url === '/api/admin/tenants' && method === 'GET') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-tenants', data: tenantsFixture() });
      }
      if (url === '/api/admin/tenants/1001/status' && method === 'PATCH') {
        const request = JSON.parse(String(init?.body));
        expect(request.status).toBe('DISABLED');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-tenant-status',
          data: { tenantId: '1001', tenantCode: 'demo', tenantName: '演示租户', status: 'DISABLED' }
        });
      }
      if (url === '/api/admin/tenants' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.tenantCode).toBe('acme');
        expect(request.adminUsername).toBe('tenant_admin');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-tenant-create',
          data: { tenantId: '1002', tenantCode: 'acme', tenantName: request.tenantName, status: request.status }
        });
      }
      if (url === '/api/admin/roles' && method === 'GET') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-roles', data: roleManagementFixture() });
      }
      if (url === '/api/admin/permissions' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.permissionCode).toBe('report:dataset:read');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-permission-save',
          data: { permissionId: '9109', permissionCode: request.permissionCode, permissionName: request.permissionName, status: request.status }
        });
      }
      if (url === '/api/admin/menus' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.menuCode).toBe('tenant.reports');
        expect(request.parentCode).toBe('tenant.root');
        expect(request.componentKey).toBe('TenantReports');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-menu-save',
          data: {
            menuId: '9210',
            menuCode: request.menuCode,
            menuName: request.menuName,
            parentCode: request.parentCode,
            routePath: request.routePath,
            componentKey: request.componentKey,
            platformScope: request.platformScope,
            sortOrder: request.sortOrder,
            status: request.status
          }
        });
      }
      if (url === '/api/admin/roles' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.roleCode).toBe('REPORT_VIEWER');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-role-create',
          data: {
            roleId: '9002',
            roleCode: request.roleCode,
            roleName: request.roleName,
            status: request.status,
            permissions: [],
            menus: []
          }
        });
      }
      if (url === '/api/admin/roles/9001/bindings' && method === 'PUT') {
        const request = JSON.parse(String(init?.body));
        expect(request.permissionCodes).toContain('platform:tenant:write');
        expect(request.menuCodes).toContain('tenant.models');
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-role-save', data: roleManagementFixture().roles[0] });
      }
      if (url === '/api/admin/roles/9002/bindings' && method === 'PUT') {
        const request = JSON.parse(String(init?.body));
        expect(request.permissionCodes).toContain('report:dataset:read');
        expect(request.menuCodes).toContain('tenant.reports');
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-report-role-save',
          data: {
            roleId: '9002',
            roleCode: 'REPORT_VIEWER',
            roleName: '报表查看员',
            status: 'ENABLED',
            permissions: [{ permissionId: '9109', permissionCode: 'report:dataset:read', permissionName: '查看报表数据集', status: 'ENABLED' }],
            menus: [{ menuId: '9210', menuCode: 'tenant.reports', menuName: '报表中心', parentCode: 'tenant.root', routePath: '/tenant/reports', componentKey: 'TenantReports', platformScope: 'TENANT', sortOrder: 30, status: 'ENABLED' }]
          }
        });
      }
      if (url === '/api/model-providers' && method === 'POST') {
        const request = JSON.parse(String(init?.body));
        expect(request.providerType).toBe('OPENAI_COMPATIBLE');
        expect(request.baseUrl).toBe('https://api.siliconflow.cn');
        expect(request.models[0].modelCode).toBe('Qwen/Qwen2.5-7B-Instruct');
        expect(request.models[0].capabilityType).toBe('CHAT');
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-provider-save', data: providerFixture() });
      }
      if (url === '/api/model-providers/test') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-provider-test',
          data: {
            success: true,
            providerId: '3001',
            modelId: '4001',
            providerName: 'SiliconFlow',
            modelCode: 'Qwen/Qwen2.5-7B-Instruct',
            capabilityType: 'CHAT',
            latencyMs: 320,
            message: 'provider ok'
          }
        });
      }
      if (url === '/api/tenants/1') {
        return jsonResponse({ code: 'COMMON-403', message: '没有权限访问该资源', traceId: 'trace-forbidden', data: null }, 403);
      }
      return jsonResponse({ code: 'COMMON-404', message: `未匹配请求 ${url}`, traceId: 'trace-missing', data: null }, 404);
    });

    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(App);
    await wrapper.get('input[name="tenantCode"]').setValue('demo');
    await wrapper.get('input[name="username"]').setValue('admin');
    await wrapper.get('input[name="password"]').setValue('AiPlatform2026!');
    await wrapper.get('form').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).toContain('租户管理');
    expect(wrapper.text()).toContain('角色管理');
    expect(wrapper.text()).toContain('权限管理');
    expect(wrapper.text()).toContain('菜单管理');
    expect(wrapper.text()).toContain('模型基础配置');
    expect(wrapper.text()).toContain('模型配置');
    expect(wrapper.text()).toContain('演示租户');

    await wrapper.get('[data-test="menu-platform-model-catalog"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('平台模型基础配置');
    await wrapper.get('input[name="platformProviderDisplayName"]').setValue('硅基流动');
    await wrapper.get('input[name="platformProviderBaseUrl"]').setValue('https://api.siliconflow.cn');
    await wrapper.get('.platform-provider-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('供应商类型已保存');

    await wrapper.get('select[name="platformModelProviderTypeId"]').setValue('5002');
    await wrapper.get('input[name="platformModelCode"]').setValue('Qwen/Qwen2.5-7B-Instruct');
    await wrapper.get('input[name="platformModelDisplayName"]').setValue('Qwen2.5-7B 免费对话模型');
    await wrapper.get('input[name="platformModelContextWindow"]').setValue('32768');
    await wrapper.get('input[name="platformModelMaxOutputTokens"]').setValue('2048');
    await wrapper.get('input[name="platformModelTemperature"]').setValue('0.2');
    await wrapper.get('input[name="platformModelTopP"]').setValue('0.7');
    await wrapper.get('.platform-model-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('模型选项已保存');

    await wrapper.get('[data-test="menu-tenant-models"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('select[name="providerType"]').text()).toContain('硅基流动');
    expect(wrapper.find('select[name="modelOption"]').text()).toContain('Hunyuan-MT-7B 免费翻译模型');
    expect(wrapper.find('select[name="modelOption"]').text()).toContain('Qwen2.5-7B 免费对话模型');

    await wrapper.get('select[name="providerType"]').setValue('OPENAI_COMPATIBLE');
    await wrapper.get('select[name="modelOption"]').setValue('5003');
    expect(wrapper.text()).toContain('请先保存当前聊天模型配置');
    expect(wrapper.get('[data-test="provider-test"]').attributes('disabled')).toBeDefined();
    await wrapper.get('input[name="apiKey"]').setValue('sk-test-secret');
    await wrapper.get('.provider-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('模型配置已保存');
    expect(wrapper.text()).not.toContain('sk-test-secret');
    expect(wrapper.get('[data-test="provider-test"]').attributes('disabled')).toBeUndefined();

    await wrapper.get('[data-test="provider-test"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('连接成功');
    expect(wrapper.text()).toContain('将测试');
    expect(wrapper.text()).toContain('SiliconFlow / Qwen/Qwen2.5-7B-Instruct');

    await wrapper.get('[data-test="menu-platform-tenants"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('租户账户');
    expect(wrapper.text()).toContain('demo');
    expect(wrapper.text()).toContain('已启用');
    await wrapper.get('[data-test="tenant-disable-1001"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('已禁用');
    expect(wrapper.text()).toContain('租户状态已更新为已禁用');
    await wrapper.get('input[name="tenantCode"]').setValue('acme');
    await wrapper.get('input[name="tenantName"]').setValue('Acme 租户');
    await wrapper.get('input[name="adminUsername"]').setValue('tenant_admin');
    await wrapper.get('input[name="adminDisplayName"]').setValue('Acme 管理员');
    await wrapper.get('input[name="adminPassword"]').setValue('Tenant2026!');
    await wrapper.get('.tenant-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('租户已保存');
    expect(wrapper.text()).toContain('acme');

    await wrapper.get('[data-test="menu-platform-permissions"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('权限管理');
    await wrapper.get('input[name="permissionCode"]').setValue('report:dataset:read');
    await wrapper.get('input[name="permissionName"]').setValue('查看报表数据集');
    await wrapper.get('.permission-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('权限点已保存');

    await wrapper.get('[data-test="menu-platform-menus"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('菜单管理');
    await wrapper.get('input[name="menuCode"]').setValue('tenant.reports');
    await wrapper.get('input[name="menuName"]').setValue('报表中心');
    await wrapper.get('select[name="menuParentCode"]').setValue('tenant.root');
    await wrapper.get('input[name="menuRoutePath"]').setValue('/tenant/reports');
    await wrapper.get('input[name="menuComponentKey"]').setValue('TenantReports');
    await wrapper.get('input[name="menuSortOrder"]').setValue('30');
    await wrapper.get('.menu-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('菜单已保存');

    await wrapper.get('[data-test="menu-platform-roles"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('角色管理');
    await wrapper.get('input[name="roleCode"]').setValue('REPORT_VIEWER');
    await wrapper.get('input[name="roleName"]').setValue('报表查看员');
    await wrapper.get('.role-form').trigger('submit.prevent');
    await flushPromises();
    expect(wrapper.text()).toContain('角色已保存');
    await wrapper.get('[data-test="permission-9002-report-dataset-read"]').setValue(true);
    await wrapper.get('[data-test="menu-9002-tenant-reports"]').setValue(true);
    await wrapper.get('[data-test="save-role-9002"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('角色权限已保存');
    await wrapper.get('[data-test="save-role-9001"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('角色权限已保存');

    await wrapper.get('[data-test="forbidden-check"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('没有权限访问该资源');
  });

  it('does not preload tenant model APIs when the current user has no model menu or permission', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === '/api/auth/login') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-login',
          data: {
            tokenName: 'satoken',
            tokenValue: 'token-demo',
            tenantId: '1001',
            tenantCode: 'demo',
            userId: '2001',
            username: 'viewer',
            displayName: '只读用户'
          }
        });
      }
      if (url === '/api/auth/me') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-me',
          data: {
            ...currentUserFixture(),
            username: 'viewer',
            displayName: '只读用户',
            permissions: [],
            menus: [
              { menuId: '9201', menuCode: 'platform.root', menuName: '平台管理', parentCode: null, routePath: '/platform', componentKey: 'PlatformLayout', platformScope: 'PLATFORM', sortOrder: 10 },
              { menuId: '9202', menuCode: 'platform.tenants', menuName: '租户管理', parentCode: 'platform.root', routePath: '/platform/tenants', componentKey: 'PlatformTenants', platformScope: 'PLATFORM', sortOrder: 11 }
            ]
          }
        });
      }
      if (url === '/api/tenants/current') {
        return jsonResponse({
          code: '0',
          message: '成功',
          traceId: 'trace-tenant',
          data: {
            tenantId: '1001',
            tenantCode: 'demo',
            tenantName: '演示租户',
            status: 'ENABLED'
          }
        });
      }
      if (url === '/api/admin/tenants') {
        return jsonResponse({ code: '0', message: '成功', traceId: 'trace-tenants', data: tenantsFixture() });
      }
      return jsonResponse({ code: 'COMMON-404', message: `未匹配请求 ${url}`, traceId: 'trace-missing', data: null }, 404);
    });

    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(App);
    await wrapper.get('input[name="tenantCode"]').setValue('demo');
    await wrapper.get('input[name="username"]').setValue('viewer');
    await wrapper.get('input[name="password"]').setValue('AiPlatform2026!');
    await wrapper.get('form').trigger('submit.prevent');
    await flushPromises();

    expect(wrapper.text()).not.toContain('模型配置');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/model-providers/options', expect.anything());
    expect(fetchMock).not.toHaveBeenCalledWith('/api/model-providers', expect.objectContaining({ method: undefined }));
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body
  } as Response;
}

function currentUserFixture() {
  return {
    tenantId: '1001',
    tenantCode: 'demo',
    userId: '2001',
    username: 'admin',
    displayName: '平台管理员',
    roles: [{ roleId: '9001', roleCode: 'PLATFORM_ADMIN', roleName: '平台管理员' }],
    permissions: [
      { permissionId: '9101', permissionCode: 'platform:tenant:write', permissionName: '启用或禁用租户' },
      { permissionId: '9102', permissionCode: 'model:platform-catalog:read', permissionName: '查看平台模型基础配置' },
      { permissionId: '9103', permissionCode: 'model:platform-catalog:write', permissionName: '维护平台模型基础配置' },
      { permissionId: '9104', permissionCode: 'model:tenant-config:write', permissionName: '维护租户模型配置' },
      { permissionId: '9105', permissionCode: 'rbac:role:write', permissionName: '维护角色' },
      { permissionId: '9106', permissionCode: 'rbac:permission:write', permissionName: '维护权限点' },
      { permissionId: '9107', permissionCode: 'rbac:menu:write', permissionName: '维护菜单' }
    ],
    menus: [
      { menuId: '9201', menuCode: 'platform.root', menuName: '平台管理', parentCode: null, routePath: '/platform', componentKey: 'PlatformLayout', platformScope: 'PLATFORM', sortOrder: 10 },
      { menuId: '9202', menuCode: 'platform.tenants', menuName: '租户管理', parentCode: 'platform.root', routePath: '/platform/tenants', componentKey: 'PlatformTenants', platformScope: 'PLATFORM', sortOrder: 11 },
      { menuId: '9203', menuCode: 'platform.roles', menuName: '角色管理', parentCode: 'platform.root', routePath: '/platform/roles', componentKey: 'PlatformRoles', platformScope: 'PLATFORM', sortOrder: 12 },
      { menuId: '9204', menuCode: 'platform.permissions', menuName: '权限管理', parentCode: 'platform.root', routePath: '/platform/permissions', componentKey: 'PlatformPermissions', platformScope: 'PLATFORM', sortOrder: 13 },
      { menuId: '9207', menuCode: 'platform.menus', menuName: '菜单管理', parentCode: 'platform.root', routePath: '/platform/menus', componentKey: 'PlatformMenus', platformScope: 'PLATFORM', sortOrder: 14 },
      { menuId: '9208', menuCode: 'platform.model-catalog', menuName: '模型基础配置', parentCode: 'platform.root', routePath: '/platform/model-catalog', componentKey: 'PlatformModelCatalog', platformScope: 'PLATFORM', sortOrder: 15 },
      { menuId: '9205', menuCode: 'tenant.root', menuName: '租户平台', parentCode: null, routePath: '/tenant', componentKey: 'TenantLayout', platformScope: 'TENANT', sortOrder: 20 },
      { menuId: '9206', menuCode: 'tenant.models', menuName: '模型配置', parentCode: 'tenant.root', routePath: '/tenant/models', componentKey: 'TenantModels', platformScope: 'TENANT', sortOrder: 21 }
    ]
  };
}

function optionsFixture() {
  return {
    providerTypes: [
      { providerTypeId: '5001', providerType: 'OPENAI', displayName: 'OpenAI', defaultBaseUrl: 'https://api.openai.com', status: 'ENABLED', sortOrder: 10 },
      { providerTypeId: '5002', providerType: 'OPENAI_COMPATIBLE', displayName: 'OpenAI 兼容供应商', defaultBaseUrl: 'https://api.siliconflow.cn', status: 'ENABLED', sortOrder: 20 }
    ],
    modelOptions: [
      { modelOptionId: '5001', providerTypeId: '5001', modelCode: 'o3', displayName: 'o3', capabilityType: 'CHAT', contextWindow: 200000, maxOutputTokens: 1024, embeddingDimensions: null, temperature: null, topP: null, topK: null, frequencyPenalty: null, presencePenalty: null, reasoningEffort: 'medium', status: 'ENABLED', sortOrder: 10 },
      { modelOptionId: '5002', providerTypeId: '5002', modelCode: 'tencent/Hunyuan-MT-7B', displayName: 'Hunyuan-MT-7B 免费翻译模型', capabilityType: 'CHAT', contextWindow: 33792, maxOutputTokens: 33792, embeddingDimensions: null, temperature: 0.2, topP: 0.7, topK: null, frequencyPenalty: null, presencePenalty: null, reasoningEffort: null, status: 'ENABLED', sortOrder: 20 }
    ]
  };
}

function adminModelCatalogFixture() {
  return optionsFixture();
}

function tenantsFixture() {
  return [{ tenantId: '1001', tenantCode: 'demo', tenantName: '演示租户', status: 'ENABLED' }];
}

function providerFixture() {
  return {
    providerId: '3001',
    providerName: 'SiliconFlow',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: 'https://api.siliconflow.cn',
    apiKeyLast4: 'cret',
    status: 'ENABLED',
    budgetUsd: 0,
    models: [{
      modelId: '4001',
      providerId: '3001',
      modelCode: 'Qwen/Qwen2.5-7B-Instruct',
      displayName: 'Qwen2.5-7B 免费对话模型',
      capabilityType: 'CHAT',
      status: 'ENABLED',
      contextWindow: 33792,
      maxOutputTokens: 33792,
      embeddingDimensions: null,
      temperature: 0.2,
      topP: 0.7,
      topK: null,
      frequencyPenalty: null,
      presencePenalty: null,
      reasoningEffort: null
    }]
  };
}

function roleManagementFixture() {
  return {
    roles: [{
      roleId: '9001',
      roleCode: 'PLATFORM_ADMIN',
      roleName: '平台管理员',
      status: 'ENABLED',
      permissions: [
        { permissionId: '9101', permissionCode: 'platform:tenant:write', permissionName: '启用或禁用租户' },
        { permissionId: '9102', permissionCode: 'model:platform-catalog:read', permissionName: '查看平台模型基础配置' },
        { permissionId: '9103', permissionCode: 'model:platform-catalog:write', permissionName: '维护平台模型基础配置' },
        { permissionId: '9104', permissionCode: 'model:tenant-config:write', permissionName: '维护租户模型配置' },
        { permissionId: '9105', permissionCode: 'rbac:role:write', permissionName: '维护角色' },
        { permissionId: '9106', permissionCode: 'rbac:permission:write', permissionName: '维护权限点' },
        { permissionId: '9107', permissionCode: 'rbac:menu:write', permissionName: '维护菜单' }
      ],
      menus: [
        { menuId: '9201', menuCode: 'platform.root', menuName: '平台管理', parentCode: null, routePath: '/platform', componentKey: 'PlatformLayout', platformScope: 'PLATFORM', sortOrder: 10 },
        { menuId: '9202', menuCode: 'platform.tenants', menuName: '租户管理', parentCode: 'platform.root', routePath: '/platform/tenants', componentKey: 'PlatformTenants', platformScope: 'PLATFORM', sortOrder: 11 },
        { menuId: '9203', menuCode: 'platform.roles', menuName: '角色管理', parentCode: 'platform.root', routePath: '/platform/roles', componentKey: 'PlatformRoles', platformScope: 'PLATFORM', sortOrder: 12 },
        { menuId: '9204', menuCode: 'platform.permissions', menuName: '权限管理', parentCode: 'platform.root', routePath: '/platform/permissions', componentKey: 'PlatformPermissions', platformScope: 'PLATFORM', sortOrder: 13 },
        { menuId: '9207', menuCode: 'platform.menus', menuName: '菜单管理', parentCode: 'platform.root', routePath: '/platform/menus', componentKey: 'PlatformMenus', platformScope: 'PLATFORM', sortOrder: 14 },
        { menuId: '9208', menuCode: 'platform.model-catalog', menuName: '模型基础配置', parentCode: 'platform.root', routePath: '/platform/model-catalog', componentKey: 'PlatformModelCatalog', platformScope: 'PLATFORM', sortOrder: 15 },
        { menuId: '9205', menuCode: 'tenant.root', menuName: '租户平台', parentCode: null, routePath: '/tenant', componentKey: 'TenantLayout', platformScope: 'TENANT', sortOrder: 20 },
        { menuId: '9206', menuCode: 'tenant.models', menuName: '模型配置', parentCode: 'tenant.root', routePath: '/tenant/models', componentKey: 'TenantModels', platformScope: 'TENANT', sortOrder: 21 }
      ]
    }],
    permissions: [
      { permissionId: '9101', permissionCode: 'platform:tenant:write', permissionName: '启用或禁用租户' },
      { permissionId: '9102', permissionCode: 'model:platform-catalog:read', permissionName: '查看平台模型基础配置' },
      { permissionId: '9103', permissionCode: 'model:platform-catalog:write', permissionName: '维护平台模型基础配置' },
      { permissionId: '9104', permissionCode: 'model:tenant-config:write', permissionName: '维护租户模型配置' },
      { permissionId: '9105', permissionCode: 'rbac:role:write', permissionName: '维护角色' },
      { permissionId: '9106', permissionCode: 'rbac:permission:write', permissionName: '维护权限点' },
      { permissionId: '9107', permissionCode: 'rbac:menu:write', permissionName: '维护菜单' }
    ],
    menus: [
      { menuId: '9201', menuCode: 'platform.root', menuName: '平台管理', parentCode: null, routePath: '/platform', componentKey: 'PlatformLayout', platformScope: 'PLATFORM', sortOrder: 10 },
      { menuId: '9202', menuCode: 'platform.tenants', menuName: '租户管理', parentCode: 'platform.root', routePath: '/platform/tenants', componentKey: 'PlatformTenants', platformScope: 'PLATFORM', sortOrder: 11 },
      { menuId: '9203', menuCode: 'platform.roles', menuName: '角色管理', parentCode: 'platform.root', routePath: '/platform/roles', componentKey: 'PlatformRoles', platformScope: 'PLATFORM', sortOrder: 12 },
      { menuId: '9204', menuCode: 'platform.permissions', menuName: '权限管理', parentCode: 'platform.root', routePath: '/platform/permissions', componentKey: 'PlatformPermissions', platformScope: 'PLATFORM', sortOrder: 13 },
      { menuId: '9207', menuCode: 'platform.menus', menuName: '菜单管理', parentCode: 'platform.root', routePath: '/platform/menus', componentKey: 'PlatformMenus', platformScope: 'PLATFORM', sortOrder: 14 },
      { menuId: '9208', menuCode: 'platform.model-catalog', menuName: '模型基础配置', parentCode: 'platform.root', routePath: '/platform/model-catalog', componentKey: 'PlatformModelCatalog', platformScope: 'PLATFORM', sortOrder: 15 },
      { menuId: '9205', menuCode: 'tenant.root', menuName: '租户平台', parentCode: null, routePath: '/tenant', componentKey: 'TenantLayout', platformScope: 'TENANT', sortOrder: 20 },
      { menuId: '9206', menuCode: 'tenant.models', menuName: '模型配置', parentCode: 'tenant.root', routePath: '/tenant/models', componentKey: 'TenantModels', platformScope: 'TENANT', sortOrder: 21 }
    ]
  };
}
