<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';

type ApiResponse<T> = {
  code: string;
  message: string;
  traceId: string;
  data: T;
};

type LoginResponse = {
  tokenName: string;
  tokenValue: string;
  tenantId: string;
  tenantCode: string;
  userId: string;
  username: string;
  displayName: string;
};

type RoleResponse = {
  roleId: string;
  roleCode: string;
  roleName: string;
};

type PermissionResponse = {
  permissionId: string;
  permissionCode: string;
  permissionName: string;
  status?: string;
};

type MenuResponse = {
  menuId: string;
  menuCode: string;
  menuName: string;
  parentCode: string | null;
  routePath: string;
  componentKey: string;
  platformScope: 'PLATFORM' | 'TENANT';
  sortOrder: number;
  status?: string;
};

type CurrentUserResponse = {
  tenantId: string;
  tenantCode: string;
  userId: string;
  username: string;
  displayName: string;
  roles: RoleResponse[];
  permissions: PermissionResponse[];
  menus: MenuResponse[];
};

type TenantResponse = {
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  status: string;
};

type ModelResponse = {
  modelId: string;
  providerId: string;
  modelCode: string;
  displayName: string;
  capabilityType: string;
  status: string;
  contextWindow: number | null;
  maxOutputTokens: number | null;
  embeddingDimensions: number | null;
  temperature: number | null;
  topP: number | null;
  topK: number | null;
  frequencyPenalty: number | null;
  presencePenalty: number | null;
  reasoningEffort: string | null;
};

type ModelProviderResponse = {
  providerId: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKeyLast4: string;
  status: string;
  budgetUsd: number | null;
  models: ModelResponse[];
};

type ProviderTypeOptionResponse = {
  providerTypeId: string;
  providerType: string;
  displayName: string;
  defaultBaseUrl: string;
  status: string;
  sortOrder: number;
};

type ModelOptionResponse = {
  modelOptionId: string;
  providerTypeId: string;
  modelCode: string;
  displayName: string;
  capabilityType: string;
  contextWindow: number | null;
  maxOutputTokens: number | null;
  embeddingDimensions: number | null;
  temperature: number | null;
  topP: number | null;
  topK: number | null;
  frequencyPenalty: number | null;
  presencePenalty: number | null;
  reasoningEffort: string | null;
  status: string;
  sortOrder: number;
};

type ModelProviderOptionsResponse = {
  providerTypes: ProviderTypeOptionResponse[];
  modelOptions: ModelOptionResponse[];
};

type ModelTestResponse = {
  success: boolean;
  providerId: string;
  modelId: string;
  providerName: string;
  modelCode: string;
  capabilityType: string;
  latencyMs: number;
  message: string;
};

type RoleAccessResponse = RoleResponse & {
  status: string;
  permissions: PermissionResponse[];
  menus: MenuResponse[];
};

type RoleManagementResponse = {
  roles: RoleAccessResponse[];
  permissions: PermissionResponse[];
  menus: MenuResponse[];
};

type RoleBindingState = {
  permissionCodes: string[];
  menuCodes: string[];
};

type MenuGroup = {
  root: MenuResponse;
  items: MenuItem[];
};

type MenuItem = {
  menu: MenuResponse;
  level: number;
  hasChildren: boolean;
};

type ModalMode = 'create' | 'edit' | 'detail' | 'access';

type DialogName =
  | ''
  | 'tenant-model'
  | 'tenant-model-detail'
  | 'platform-provider'
  | 'platform-model'
  | 'tenant'
  | 'role'
  | 'role-access'
  | 'permission'
  | 'menu';

type DialogState = {
  name: DialogName;
  mode: ModalMode;
  title: string;
};

type ProviderModelRow = {
  provider: ModelProviderResponse;
  model: ModelResponse;
};

type MenuTreeRow = MenuResponse & {
  level: number;
  childCount: number;
};

const credentials = reactive({
  tenantCode: 'demo',
  username: 'admin',
  password: ''
});
const providerForm = reactive({
  apiKey: '',
  budgetUsd: 5
});
const platformProviderForm = reactive({
  providerTypeId: '',
  providerType: 'OPENAI_COMPATIBLE',
  displayName: 'OpenAI 兼容供应商',
  defaultBaseUrl: 'https://api.siliconflow.cn',
  status: 'ENABLED',
  sortOrder: 20
});
const platformModelForm = reactive({
  modelOptionId: '',
  providerTypeId: '',
  modelCode: 'Qwen/Qwen2.5-7B-Instruct',
  displayName: 'Qwen2.5-7B 免费对话模型',
  capabilityType: 'CHAT',
  contextWindow: '32768',
  maxOutputTokens: '2048',
  embeddingDimensions: '',
  temperature: '0.2',
  topP: '0.7',
  topK: '',
  frequencyPenalty: '',
  presencePenalty: '',
  reasoningEffort: '',
  status: 'ENABLED',
  sortOrder: 30
});
const tenantForm = reactive({
  tenantId: '',
  tenantCode: '',
  tenantName: '',
  adminUsername: '',
  adminDisplayName: '',
  adminPassword: '',
  status: 'ENABLED'
});
const roleForm = reactive({
  roleId: '',
  roleCode: '',
  roleName: '',
  status: 'ENABLED'
});
const permissionForm = reactive({
  permissionId: '',
  permissionCode: '',
  permissionName: '',
  status: 'ENABLED'
});
const menuForm = reactive({
  menuId: '',
  menuCode: '',
  menuName: '',
  parentCode: '',
  routePath: '',
  componentKey: '',
  platformScope: 'PLATFORM',
  sortOrder: 10,
  status: 'ENABLED'
});
const tokenName = ref(localStorage.getItem('ai-platform.tokenName') || 'satoken');
const tokenValue = ref(localStorage.getItem('ai-platform.tokenValue') || '');
const currentUser = ref<CurrentUserResponse | null>(null);
const currentTenant = ref<TenantResponse | null>(null);
const providers = ref<ModelProviderResponse[]>([]);
const providerOptions = ref<ModelProviderOptionsResponse | null>(null);
const platformCatalog = ref<ModelProviderOptionsResponse | null>(null);
const tenants = ref<TenantResponse[]>([]);
const roleManagement = ref<RoleManagementResponse | null>(null);
const roleBindings = reactive<Record<string, RoleBindingState>>({});
const selectedProviderType = ref('OPENAI_COMPATIBLE');
const selectedModelOptionId = ref('');
const activeMenu = ref('tenant.models');
const loading = ref(false);
const providerSaving = ref(false);
const providerTesting = ref(false);
const platformProviderSaving = ref(false);
const platformModelSaving = ref(false);
const tenantSaving = ref(false);
const roleSaving = ref(false);
const permissionSaving = ref(false);
const menuSaving = ref(false);
const rbacSavingRoleId = ref('');
const tenantUpdatingId = ref('');
const errorMessage = ref('');
const permissionMessage = ref('');
const providerMessage = ref('');
const providerError = ref('');
const platformCatalogMessage = ref('');
const platformCatalogError = ref('');
const rbacMessage = ref('');
const rbacError = ref('');
const tenantMessage = ref('');
const tenantError = ref('');
const dialog = reactive<DialogState>({
  name: '',
  mode: 'create',
  title: ''
});
const listPage = reactive({
  tenantModels: 1,
  platformProviders: 1,
  platformModels: 1,
  tenants: 1,
  roles: 1,
  permissions: 1,
  menus: 1
});
const selectedProvider = ref<ModelProviderResponse | null>(null);
const selectedProviderModel = ref<ModelResponse | null>(null);
const selectedPlatformProvider = ref<ProviderTypeOptionResponse | null>(null);
const selectedPlatformModel = ref<ModelOptionResponse | null>(null);
const selectedTenant = ref<TenantResponse | null>(null);
const selectedRole = ref<RoleAccessResponse | null>(null);
const selectedPermission = ref<PermissionResponse | null>(null);
const selectedMenu = ref<MenuResponse | null>(null);
const providerTypeValues = ['OPENAI', 'OPENAI_COMPATIBLE', 'AZURE_OPENAI', 'NATIVE_VENDOR'];
const capabilityValues = ['CHAT', 'EMBEDDING', 'RERANK', 'IMAGE', 'AUDIO', 'MODERATION'];
const menuScopeValues = ['PLATFORM', 'TENANT'];
const pageSize = 8;

const isAuthenticated = computed(() => Boolean(tokenValue.value && currentUser.value && currentTenant.value));
const permissionCodes = computed(() => new Set((currentUser.value?.permissions || []).map((permission) => permission.permissionCode)));
const enabledProviderTypes = computed(() => sortCatalog(providerOptions.value?.providerTypes || []));
const platformProviderTypes = computed(() => [...(platformCatalog.value?.providerTypes || [])]
  .sort((left, right) => left.sortOrder - right.sortOrder));
const platformModelOptions = computed(() => [...(platformCatalog.value?.modelOptions || [])]
  .sort((left, right) => left.sortOrder - right.sortOrder));
const selectedProviderTypeOption = computed(() => {
  return enabledProviderTypes.value.find((option) => option.providerType === selectedProviderType.value) || enabledProviderTypes.value[0] || null;
});
const modelOptionsForProvider = computed(() => {
  if (!selectedProviderTypeOption.value) {
    return [];
  }
  return sortCatalog((providerOptions.value?.modelOptions || []).filter((option) => option.providerTypeId === selectedProviderTypeOption.value?.providerTypeId));
});
const selectedModelOption = computed(() => {
  return modelOptionsForProvider.value.find((option) => option.modelOptionId === selectedModelOptionId.value) || modelOptionsForProvider.value[0] || null;
});
const selectedConfiguredChatModel = computed(() => {
  if (!selectedModelOption.value || selectedModelOption.value.capabilityType !== 'CHAT') {
    return null;
  }
  return providers.value
    .flatMap((provider) => provider.models)
    .find((model) => model.capabilityType === 'CHAT' && model.modelCode === selectedModelOption.value?.modelCode) || null;
});
const sortedMenus = computed(() => [...(currentUser.value?.menus || [])].sort((left, right) => left.sortOrder - right.sortOrder));
const menuGroups = computed<MenuGroup[]>(() => {
  const menus = sortedMenus.value;
  const roots = menus.filter((menu) => menu.parentCode === null);
  return roots.map((root) => ({
    root,
    items: flattenMenuItems(root.menuCode, 1)
  }));
});
const currentMenuChildren = computed(() => sortedMenus.value.reduce<Record<string, MenuResponse[]>>((childrenByParent, menu) => {
  if (menu.parentCode) {
    childrenByParent[menu.parentCode] = [...(childrenByParent[menu.parentCode] || []), menu];
  }
  return childrenByParent;
}, {}));
const activeMenuTitle = computed(() => {
  return currentUser.value?.menus.find((menu) => menu.menuCode === activeMenu.value)?.menuName || '工作台';
});
const rbacMenus = computed(() => roleManagement.value?.menus || []);
const roleManagementMenus = computed(() => rbacMenus.value.filter((menu) => menu.status !== 'DISABLED'));
const roleManagementPermissions = computed(() => (roleManagement.value?.permissions || []).filter((permission) => permission.status !== 'DISABLED'));
const selectedConfiguredChatModelProvider = computed(() => {
  if (!selectedConfiguredChatModel.value) {
    return null;
  }
  return providers.value.find((provider) => provider.providerId === selectedConfiguredChatModel.value?.providerId) || null;
});
const tenantModelRows = computed<ProviderModelRow[]>(() => providers.value.flatMap((provider) => provider.models.map((model) => ({ provider, model }))));
const pagedTenantModelRows = computed(() => pageItems(tenantModelRows.value, listPage.tenantModels));
const pagedPlatformProviderTypes = computed(() => pageItems(platformProviderTypes.value, listPage.platformProviders));
const pagedPlatformModelOptions = computed(() => pageItems(platformModelOptions.value, listPage.platformModels));
const pagedTenants = computed(() => pageItems(tenants.value, listPage.tenants));
const pagedRoles = computed(() => pageItems(roleManagement.value?.roles || [], listPage.roles));
const pagedPermissions = computed(() => pageItems(roleManagement.value?.permissions || [], listPage.permissions));
const menuTreeRows = computed(() => buildMenuTree(roleManagement.value?.menus || []));
const pagedMenuTreeRows = computed(() => pageItems(menuTreeRows.value, listPage.menus));
const canSaveTenant = computed(() => hasPermission('platform:tenant:write'));
const canSaveRole = computed(() => hasPermission('rbac:role:write'));
const canSavePermission = computed(() => hasPermission('rbac:permission:write'));
const canSaveMenu = computed(() => hasPermission('rbac:menu:write'));
const isDialogOpen = computed(() => dialog.name !== '');
const modelTestTargetLabel = computed(() => {
  if (!selectedConfiguredChatModel.value) {
    return '请先保存当前聊天模型配置';
  }
  return `${selectedConfiguredChatModelProvider.value?.providerName || '未知供应商'} / ${selectedConfiguredChatModel.value.modelCode}`;
});

watch(selectedProviderType, () => {
  selectedModelOptionId.value = modelOptionsForProvider.value[0]?.modelOptionId || '';
});

watch(() => platformProviderForm.providerType, () => {
  fillPlatformProviderForm();
});

onMounted(async () => {
  if (!tokenValue.value) {
    return;
  }
  try {
    await refreshSession();
  } catch {
    clearSession();
  }
});

async function login() {
  loading.value = true;
  errorMessage.value = '';
  permissionMessage.value = '';
  try {
    const session = await request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials)
    });
    tokenName.value = session.tokenName;
    tokenValue.value = session.tokenValue;
    localStorage.setItem('ai-platform.tokenName', tokenName.value);
    localStorage.setItem('ai-platform.tokenValue', tokenValue.value);
    await refreshSession();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

async function logout() {
  if (tokenValue.value) {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: authHeaders()
    });
  }
  clearSession();
}

async function checkForbidden() {
  permissionMessage.value = '';
  try {
    const forbiddenTenantId = currentTenant.value?.tenantId === '1' ? '2' : '1';
    await request<TenantResponse>(`/api/tenants/${forbiddenTenantId}`, {
      headers: authHeaders()
    });
  } catch (error) {
    permissionMessage.value = error instanceof Error ? error.message : '权限校验失败';
  }
}

async function refreshSession() {
  currentUser.value = await request<CurrentUserResponse>('/api/auth/me', {
    headers: authHeaders()
  });
  currentTenant.value = await request<TenantResponse>('/api/tenants/current', {
    headers: authHeaders()
  });
  normalizeActiveMenu();
  await loadPlatformCatalogIfAllowed();
  await loadModelConfigIfAllowed();
  await loadTenantsIfAllowed();
  await loadRoleManagementIfAllowed();
  normalizeActiveMenu();
}

async function loadPlatformCatalogIfAllowed() {
  if (!hasMenu('platform.model-catalog') && !hasPermission('model:platform-catalog:read') && !hasPermission('model:platform-catalog:write')) {
    platformCatalog.value = null;
    return;
  }
  platformCatalog.value = await request<ModelProviderOptionsResponse>('/api/admin/model-catalog', {
    headers: authHeaders()
  });
  fillPlatformProviderForm();
  normalizePlatformModelProvider();
}

async function loadModelConfigIfAllowed() {
  if (!hasMenu('tenant.models') && !hasPermission('model:tenant-config:read') && !hasPermission('model:tenant-config:write')) {
    providerOptions.value = null;
    providers.value = [];
    return;
  }
  await loadProviderOptions();
  await loadProviders();
}

async function loadProviderOptions() {
  providerOptions.value = await request<ModelProviderOptionsResponse>('/api/model-providers/options', {
    headers: authHeaders()
  });
  normalizeCatalogSelection();
}

async function loadProviders() {
  providers.value = await request<ModelProviderResponse[]>('/api/model-providers', {
    headers: authHeaders()
  });
}

async function loadTenantsIfAllowed() {
  if (!hasMenu('platform.tenants') && !hasPermission('platform:tenant:read') && !hasPermission('platform:tenant:write')) {
    tenants.value = [];
    return;
  }
  tenants.value = await request<TenantResponse[]>('/api/admin/tenants', {
    headers: authHeaders()
  });
}

async function loadRoleManagementIfAllowed() {
  if (!hasAnyMenu(['platform.rbac', 'platform.roles', 'platform.permissions', 'platform.menus'])
    && !hasAnyPermission(['rbac:role:read', 'rbac:role:write', 'rbac:permission:read', 'rbac:permission:write', 'rbac:menu:read', 'rbac:menu:write'])) {
    roleManagement.value = null;
    return;
  }
  roleManagement.value = await request<RoleManagementResponse>('/api/admin/roles', {
    headers: authHeaders()
  });
  seedRoleBindings();
}

async function savePlatformProviderType() {
  platformProviderSaving.value = true;
  platformCatalogMessage.value = '';
  platformCatalogError.value = '';
  try {
    const providerType = await request<ProviderTypeOptionResponse>('/api/admin/model-catalog/provider-types', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerTypeId: platformProviderForm.providerTypeId || null,
        providerType: platformProviderForm.providerType,
        displayName: platformProviderForm.displayName,
        defaultBaseUrl: platformProviderForm.defaultBaseUrl,
        status: platformProviderForm.status,
        sortOrder: platformProviderForm.sortOrder
      })
    });
    upsertPlatformProviderType(providerType);
    platformProviderForm.providerTypeId = providerType.providerTypeId;
    normalizePlatformModelProvider();
    platformCatalogMessage.value = '供应商类型已保存';
    closeDialog();
  } catch (error) {
    platformCatalogError.value = error instanceof Error ? error.message : '供应商类型保存失败';
  } finally {
    platformProviderSaving.value = false;
  }
}

async function savePlatformModelOption() {
  platformModelSaving.value = true;
  platformCatalogMessage.value = '';
  platformCatalogError.value = '';
  try {
    const modelOption = await request<ModelOptionResponse>('/api/admin/model-catalog/model-options', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        modelOptionId: platformModelForm.modelOptionId || null,
        providerTypeId: platformModelForm.providerTypeId,
        modelCode: platformModelForm.modelCode,
        displayName: platformModelForm.displayName,
        capabilityType: platformModelForm.capabilityType,
        contextWindow: optionalNumber(platformModelForm.contextWindow),
        maxOutputTokens: optionalNumber(platformModelForm.maxOutputTokens),
        embeddingDimensions: optionalNumber(platformModelForm.embeddingDimensions),
        temperature: optionalNumber(platformModelForm.temperature),
        topP: optionalNumber(platformModelForm.topP),
        topK: optionalNumber(platformModelForm.topK),
        frequencyPenalty: optionalNumber(platformModelForm.frequencyPenalty),
        presencePenalty: optionalNumber(platformModelForm.presencePenalty),
        reasoningEffort: platformModelForm.reasoningEffort || null,
        status: platformModelForm.status,
        sortOrder: platformModelForm.sortOrder
      })
    });
    upsertPlatformModelOption(modelOption);
    platformModelForm.modelOptionId = modelOption.modelOptionId;
    platformCatalogMessage.value = '模型选项已保存';
    closeDialog();
  } catch (error) {
    platformCatalogError.value = error instanceof Error ? error.message : '模型选项保存失败';
  } finally {
    platformModelSaving.value = false;
  }
}

async function updateTenantStatus(tenant: TenantResponse, status: string) {
  tenantUpdatingId.value = tenant.tenantId;
  tenantMessage.value = '';
  tenantError.value = '';
  try {
    const updatedTenant = await request<TenantResponse>(`/api/admin/tenants/${tenant.tenantId}/status`, {
      method: 'PATCH',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    });
    tenants.value = tenants.value.map((item) => (item.tenantId === updatedTenant.tenantId ? updatedTenant : item));
    if (currentTenant.value?.tenantId === updatedTenant.tenantId) {
      currentTenant.value = updatedTenant;
    }
    tenantMessage.value = `租户状态已更新为${statusLabel(updatedTenant.status)}`;
  } catch (error) {
    tenantError.value = error instanceof Error ? error.message : '租户状态更新失败';
  } finally {
    tenantUpdatingId.value = '';
  }
}

async function saveTenant() {
  tenantSaving.value = true;
  tenantMessage.value = '';
  tenantError.value = '';
  try {
    const isUpdate = Boolean(tenantForm.tenantId);
    const tenant = await request<TenantResponse>(isUpdate ? `/api/admin/tenants/${tenantForm.tenantId}` : '/api/admin/tenants', {
      method: isUpdate ? 'PUT' : 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(isUpdate ? {
        tenantName: tenantForm.tenantName,
        status: tenantForm.status
      } : {
        tenantCode: tenantForm.tenantCode,
        tenantName: tenantForm.tenantName,
        adminUsername: tenantForm.adminUsername,
        adminDisplayName: tenantForm.adminDisplayName,
        adminPassword: tenantForm.adminPassword,
        status: tenantForm.status
      })
    });
    upsertTenant(tenant);
    if (currentTenant.value?.tenantId === tenant.tenantId) {
      currentTenant.value = tenant;
    }
    resetTenantForm();
    tenantMessage.value = '租户已保存';
    closeDialog();
  } catch (error) {
    tenantError.value = error instanceof Error ? error.message : '租户保存失败';
  } finally {
    tenantSaving.value = false;
  }
}

async function saveRoleBindings(role: RoleAccessResponse) {
  rbacSavingRoleId.value = role.roleId;
  rbacMessage.value = '';
  rbacError.value = '';
  try {
    const binding = roleBinding(role.roleId);
    const updatedRole = await request<RoleAccessResponse>(`/api/admin/roles/${role.roleId}/bindings`, {
      method: 'PUT',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        permissionCodes: binding.permissionCodes,
        menuCodes: binding.menuCodes
      })
    });
    if (roleManagement.value) {
      roleManagement.value = {
        ...roleManagement.value,
        roles: roleManagement.value.roles.map((item) => (item.roleId === updatedRole.roleId ? updatedRole : item))
      };
    }
    roleBindings[updatedRole.roleId] = {
      permissionCodes: updatedRole.permissions.map((permission) => permission.permissionCode),
      menuCodes: updatedRole.menus.map((menu) => menu.menuCode)
    };
    selectedRole.value = updatedRole;
    rbacMessage.value = '角色权限已保存';
    closeDialog();
  } catch (error) {
    rbacError.value = error instanceof Error ? error.message : '角色权限保存失败';
  } finally {
    rbacSavingRoleId.value = '';
  }
}

async function saveRole() {
  roleSaving.value = true;
  rbacMessage.value = '';
  rbacError.value = '';
  try {
    const role = await request<RoleAccessResponse>('/api/admin/roles', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        roleId: roleForm.roleId || null,
        roleCode: roleForm.roleCode,
        roleName: roleForm.roleName,
        status: roleForm.status
      })
    });
    upsertRole(role);
    resetRoleForm();
    rbacMessage.value = '角色已保存';
    closeDialog();
  } catch (error) {
    rbacError.value = error instanceof Error ? error.message : '角色保存失败';
  } finally {
    roleSaving.value = false;
  }
}

async function savePermission() {
  permissionSaving.value = true;
  rbacMessage.value = '';
  rbacError.value = '';
  try {
    const permission = await request<PermissionResponse>('/api/admin/permissions', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        permissionId: permissionForm.permissionId || null,
        permissionCode: permissionForm.permissionCode,
        permissionName: permissionForm.permissionName,
        status: permissionForm.status
      })
    });
    upsertPermission(permission);
    resetPermissionForm();
    rbacMessage.value = '权限点已保存';
    closeDialog();
  } catch (error) {
    rbacError.value = error instanceof Error ? error.message : '权限点保存失败';
  } finally {
    permissionSaving.value = false;
  }
}

async function saveMenu() {
  menuSaving.value = true;
  rbacMessage.value = '';
  rbacError.value = '';
  try {
    const menu = await request<MenuResponse>('/api/admin/menus', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        menuId: menuForm.menuId || null,
        menuCode: menuForm.menuCode,
        menuName: menuForm.menuName,
        parentCode: menuForm.parentCode || null,
        routePath: menuForm.routePath,
        componentKey: menuForm.componentKey,
        platformScope: menuForm.platformScope,
        sortOrder: Number(menuForm.sortOrder),
        status: menuForm.status
      })
    });
    upsertMenu(menu);
    resetMenuForm();
    rbacMessage.value = '菜单已保存';
    closeDialog();
  } catch (error) {
    rbacError.value = error instanceof Error ? error.message : '菜单保存失败';
  } finally {
    menuSaving.value = false;
  }
}

async function saveProvider() {
  providerSaving.value = true;
  providerMessage.value = '';
  providerError.value = '';
  try {
    if (!selectedProviderTypeOption.value || !selectedModelOption.value) {
      throw new Error('缺少可用的模型目录配置');
    }
    const provider = await request<ModelProviderResponse>('/api/model-providers', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        providerName: selectedProviderTypeOption.value.displayName,
        providerType: selectedProviderTypeOption.value.providerType,
        baseUrl: selectedProviderTypeOption.value.defaultBaseUrl,
        apiKey: providerForm.apiKey,
        budgetUsd: providerForm.budgetUsd,
        models: [{
          modelCode: selectedModelOption.value.modelCode,
          displayName: selectedModelOption.value.displayName,
          capabilityType: selectedModelOption.value.capabilityType,
          contextWindow: selectedModelOption.value.contextWindow,
          maxOutputTokens: selectedModelOption.value.maxOutputTokens,
          embeddingDimensions: selectedModelOption.value.embeddingDimensions,
          temperature: selectedModelOption.value.temperature,
          topP: selectedModelOption.value.topP,
          topK: selectedModelOption.value.topK,
          frequencyPenalty: selectedModelOption.value.frequencyPenalty,
          presencePenalty: selectedModelOption.value.presencePenalty,
          reasoningEffort: selectedModelOption.value.reasoningEffort
        }]
      })
    });
    providerForm.apiKey = '';
    providers.value = [provider, ...providers.value.filter((item) => item.providerId !== provider.providerId)];
    providerMessage.value = '模型配置已保存';
    closeDialog();
  } catch (error) {
    providerError.value = error instanceof Error ? error.message : '模型配置保存失败';
  } finally {
    providerSaving.value = false;
  }
}

async function testFirstChatModel() {
  if (!selectedConfiguredChatModel.value) {
    providerError.value = '请先保存当前聊天模型配置';
    return;
  }
  providerTesting.value = true;
  providerMessage.value = '';
  providerError.value = '';
  try {
    const result = await request<ModelTestResponse>('/api/model-providers/test', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        modelId: selectedConfiguredChatModel.value.modelId,
        prompt: '请用一句话回复：provider test ok'
      })
    });
    providerMessage.value = `${result.providerName} / ${result.modelCode} 连接成功，耗时 ${result.latencyMs}ms`;
  } catch (error) {
    providerError.value = error instanceof Error ? error.message : '供应商连接测试失败';
  } finally {
    providerTesting.value = false;
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || payload.code !== '0') {
    throw new Error(payload.message || '请求失败');
  }
  return payload.data;
}

function authHeaders(): Record<string, string> {
  return tokenValue.value ? { [tokenName.value]: tokenValue.value } : {};
}

function clearSession() {
  tokenValue.value = '';
  currentUser.value = null;
  currentTenant.value = null;
  providers.value = [];
  providerOptions.value = null;
  platformCatalog.value = null;
  tenants.value = [];
  roleManagement.value = null;
  Object.keys(roleBindings).forEach((key) => {
    delete roleBindings[key];
  });
  permissionMessage.value = '';
  providerMessage.value = '';
  providerError.value = '';
  platformCatalogMessage.value = '';
  platformCatalogError.value = '';
  rbacMessage.value = '';
  rbacError.value = '';
  tenantMessage.value = '';
  tenantError.value = '';
  localStorage.removeItem('ai-platform.tokenName');
  localStorage.removeItem('ai-platform.tokenValue');
}

function hasPermission(permissionCode: string) {
  return permissionCodes.value.has(permissionCode);
}

function hasMenu(menuCode: string) {
  return Boolean(currentUser.value?.menus.some((menu) => menu.menuCode === menuCode));
}

function hasAnyMenu(menuCodes: string[]) {
  return menuCodes.some((menuCode) => hasMenu(menuCode));
}

function hasAnyPermission(codes: string[]) {
  return codes.some((code) => hasPermission(code));
}

function setActiveMenu(menuCode: string) {
  activeMenu.value = menuCode;
}

function menuTestId(menuCode: string) {
  return `menu-${menuCode.replace('.', '-')}`;
}

function normalizeActiveMenu() {
  if (hasMenu(activeMenu.value)) {
    return;
  }
  activeMenu.value = sortedMenus.value.find((menu) => menu.parentCode !== null && !currentMenuChildren.value[menu.menuCode]?.length)?.menuCode
    || sortedMenus.value.find((menu) => menu.parentCode !== null)?.menuCode
    || 'tenant.models';
}

function flattenMenuItems(parentCode: string, level: number): MenuItem[] {
  return (currentMenuChildren.value[parentCode] || []).flatMap((menu) => {
    const children = currentMenuChildren.value[menu.menuCode] || [];
    return [
      { menu, level, hasChildren: children.length > 0 },
      ...flattenMenuItems(menu.menuCode, level + 1)
    ];
  });
}

function normalizeCatalogSelection() {
  const provider = enabledProviderTypes.value.find((option) => option.providerType === selectedProviderType.value) || enabledProviderTypes.value[0];
  selectedProviderType.value = provider?.providerType || '';
  selectedModelOptionId.value = modelOptionsForProvider.value[0]?.modelOptionId || '';
}

function fillPlatformProviderForm() {
  const option = platformProviderTypes.value.find((item) => item.providerType === platformProviderForm.providerType);
  if (!option) {
    platformProviderForm.providerTypeId = '';
    return;
  }
  platformProviderForm.providerTypeId = option.providerTypeId;
  platformProviderForm.displayName = option.displayName;
  platformProviderForm.defaultBaseUrl = option.defaultBaseUrl;
  platformProviderForm.status = option.status;
  platformProviderForm.sortOrder = option.sortOrder;
}

function normalizePlatformModelProvider() {
  if (platformModelForm.providerTypeId && platformProviderTypes.value.some((item) => item.providerTypeId === platformModelForm.providerTypeId)) {
    return;
  }
  const preferred = platformProviderTypes.value.find((item) => item.providerType === platformProviderForm.providerType) || platformProviderTypes.value[0];
  platformModelForm.providerTypeId = preferred?.providerTypeId || '';
}

function upsertPlatformProviderType(option: ProviderTypeOptionResponse) {
  const current = platformCatalog.value || { providerTypes: [], modelOptions: [] };
  platformCatalog.value = {
    ...current,
    providerTypes: [
      option,
      ...current.providerTypes.filter((item) => item.providerTypeId !== option.providerTypeId)
    ].sort((left, right) => left.sortOrder - right.sortOrder)
  };
  if (providerOptions.value) {
    providerOptions.value = {
      ...providerOptions.value,
      providerTypes: upsertEnabledCatalogOption(providerOptions.value.providerTypes, option, 'providerTypeId')
    };
  }
}

function upsertPlatformModelOption(option: ModelOptionResponse) {
  const current = platformCatalog.value || { providerTypes: [], modelOptions: [] };
  platformCatalog.value = {
    ...current,
    modelOptions: [
      option,
      ...current.modelOptions.filter((item) => item.modelOptionId !== option.modelOptionId)
    ].sort((left, right) => left.sortOrder - right.sortOrder)
  };
  if (providerOptions.value) {
    providerOptions.value = {
      ...providerOptions.value,
      modelOptions: upsertEnabledCatalogOption(providerOptions.value.modelOptions, option, 'modelOptionId')
    };
  }
}

function upsertEnabledCatalogOption<T extends { status: string; sortOrder: number }>(
  options: T[],
  option: T,
  idKey: keyof T
) {
  const filtered = options.filter((item) => item[idKey] !== option[idKey]);
  if (option.status === 'ENABLED') {
    filtered.push(option);
  }
  return filtered.sort((left, right) => left.sortOrder - right.sortOrder);
}

function optionalNumber(value: string | number | null) {
  if (value === null || value === '') {
    return null;
  }
  return Number(value);
}

function sortCatalog<T extends { status: string; sortOrder: number }>(items: T[]) {
  return items.filter((item) => item.status === 'ENABLED').sort((left, right) => left.sortOrder - right.sortOrder);
}

function seedRoleBindings() {
  Object.keys(roleBindings).forEach((key) => {
    delete roleBindings[key];
  });
  for (const role of roleManagement.value?.roles || []) {
    roleBindings[role.roleId] = {
      permissionCodes: role.permissions.map((permission) => permission.permissionCode),
      menuCodes: role.menus.map((menu) => menu.menuCode)
    };
  }
}

function upsertTenant(tenant: TenantResponse) {
  tenants.value = [
    tenant,
    ...tenants.value.filter((item) => item.tenantId !== tenant.tenantId)
  ].sort((left, right) => left.tenantCode.localeCompare(right.tenantCode));
}

function upsertRole(role: RoleAccessResponse) {
  const current = roleManagement.value || { roles: [], permissions: [], menus: [] };
  roleManagement.value = {
    ...current,
    roles: [
      role,
      ...current.roles.filter((item) => item.roleId !== role.roleId)
    ].sort((left, right) => left.roleCode.localeCompare(right.roleCode))
  };
  roleBindings[role.roleId] = {
    permissionCodes: role.permissions.map((permission) => permission.permissionCode),
    menuCodes: role.menus.map((menu) => menu.menuCode)
  };
}

function upsertPermission(permission: PermissionResponse) {
  const current = roleManagement.value || { roles: [], permissions: [], menus: [] };
  roleManagement.value = {
    ...current,
    permissions: [
      permission,
      ...current.permissions.filter((item) => item.permissionId !== permission.permissionId)
    ].sort((left, right) => left.permissionCode.localeCompare(right.permissionCode))
  };
}

function upsertMenu(menu: MenuResponse) {
  const current = roleManagement.value || { roles: [], permissions: [], menus: [] };
  roleManagement.value = {
    ...current,
    menus: [
      menu,
      ...current.menus.filter((item) => item.menuId !== menu.menuId)
    ].sort((left, right) => left.sortOrder - right.sortOrder)
  };
}

function roleBinding(roleId: string) {
  if (!roleBindings[roleId]) {
    roleBindings[roleId] = { permissionCodes: [], menuCodes: [] };
  }
  return roleBindings[roleId];
}

function bindingTestId(prefix: string, roleId: string, code: string) {
  return `${prefix}-${roleId}-${code.replace(/[^a-zA-Z0-9]/g, '-')}`;
}

function resetTenantForm() {
  tenantForm.tenantId = '';
  tenantForm.tenantCode = '';
  tenantForm.tenantName = '';
  tenantForm.adminUsername = '';
  tenantForm.adminDisplayName = '';
  tenantForm.adminPassword = '';
  tenantForm.status = 'ENABLED';
}

function resetRoleForm() {
  roleForm.roleId = '';
  roleForm.roleCode = '';
  roleForm.roleName = '';
  roleForm.status = 'ENABLED';
}

function resetPermissionForm() {
  permissionForm.permissionId = '';
  permissionForm.permissionCode = '';
  permissionForm.permissionName = '';
  permissionForm.status = 'ENABLED';
}

function resetMenuForm() {
  menuForm.menuId = '';
  menuForm.menuCode = '';
  menuForm.menuName = '';
  menuForm.parentCode = '';
  menuForm.routePath = '';
  menuForm.componentKey = '';
  menuForm.platformScope = 'PLATFORM';
  menuForm.sortOrder = 10;
  menuForm.status = 'ENABLED';
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    ENABLED: '已启用',
    DISABLED: '已禁用',
    SUCCESS: '成功',
    FAILED: '失败'
  };
  return labels[status] || status;
}

function statusTone(status?: string | null) {
  if (status === 'ENABLED' || status === 'SUCCESS') {
    return 'is-success';
  }
  if (status === 'DISABLED') {
    return 'is-muted';
  }
  if (status === 'FAILED') {
    return 'is-danger';
  }
  return 'is-neutral';
}

function menuIconClass(menuCode: string) {
  if (menuCode.includes('tenant')) {
    return 'nav-icon-tenant';
  }
  if (menuCode.includes('role') || menuCode.includes('rbac') || menuCode.includes('permission')) {
    return 'nav-icon-rbac';
  }
  if (menuCode.includes('menu')) {
    return 'nav-icon-menu';
  }
  if (menuCode.includes('model')) {
    return 'nav-icon-model';
  }
  return 'nav-icon-default';
}

function capabilityLabel(capabilityType: string) {
  const labels: Record<string, string> = {
    CHAT: '对话',
    EMBEDDING: '向量',
    RERANK: '重排',
    IMAGE: '图像',
    AUDIO: '音频',
    MODERATION: '审核'
  };
  return labels[capabilityType] || capabilityType;
}
</script>

<template>
  <main class="shell">
    <section v-if="!isAuthenticated" class="login-surface">
      <form class="login-panel" @submit.prevent="login">
        <div class="window-dots" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <div class="login-brand">
          <div class="brand-mark" aria-hidden="true"></div>
          <div>
            <p class="eyebrow">AI Platform Console</p>
            <h1>企业 AI 平台</h1>
          </div>
        </div>

        <label>
          租户编码
          <input name="tenantCode" v-model="credentials.tenantCode" autocomplete="organization" />
        </label>
        <label>
          用户名
          <input name="username" v-model="credentials.username" autocomplete="username" />
        </label>
        <label>
          密码
          <input name="password" v-model="credentials.password" type="password" autocomplete="current-password" />
        </label>

        <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
        <button class="primary-action" type="submit" :disabled="loading">
          {{ loading ? '登录中' : '登录' }}
        </button>
      </form>
    </section>

    <section v-else class="console-shell">
      <aside class="sidebar">
        <div class="window-dots sidebar-dots" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <div class="brand">
          <div class="brand-row">
            <div class="brand-mark" aria-hidden="true"></div>
            <div>
              <span>AI Platform</span>
              <strong>{{ currentTenant?.tenantCode }}</strong>
            </div>
          </div>
        </div>
        <nav class="side-nav">
          <section
            v-for="group in menuGroups"
            :key="group.root.menuCode"
            class="menu-group"
            :class="{ 'has-active-menu': group.items.some((item) => item.menu.menuCode === activeMenu) }"
          >
            <p>{{ group.root.menuName }}</p>
            <button
              v-for="item in group.items"
              :key="item.menu.menuCode"
              :data-test="menuTestId(item.menu.menuCode)"
              class="menu-button"
              :class="{ active: activeMenu === item.menu.menuCode }"
              :style="{ paddingLeft: `${10 + item.level * 12}px` }"
              type="button"
              @click="setActiveMenu(item.menu.menuCode)"
            >
              <span :class="['nav-icon', menuIconClass(item.menu.menuCode)]" aria-hidden="true"></span>
              <span>{{ item.menu.menuName }}</span>
            </button>
          </section>
        </nav>
        <div class="sidebar-footer">
          <span class="nav-icon" aria-hidden="true"></span>
          <span>收起导航</span>
        </div>
      </aside>

      <section class="console-content">
        <header class="topbar">
          <div>
            <p class="eyebrow">AI Platform Console</p>
            <h1>{{ activeMenuTitle }}</h1>
          </div>
          <div class="topbar-actions">
            <div class="tenant-switcher">
              <span>租户上下文</span>
              <strong>{{ currentTenant?.tenantName }}</strong>
              <em :class="['status-badge', statusTone(currentTenant?.status)]">{{ statusLabel(currentTenant?.status || '') }}</em>
            </div>
            <div class="user-chip">
              <span>{{ currentUser?.displayName?.slice(0, 1) || 'A' }}</span>
              <div>
                <strong>{{ currentUser?.username }}</strong>
                <small>{{ currentUser?.displayName }}</small>
              </div>
            </div>
            <button class="ghost-action" type="button" @click="logout">登出</button>
          </div>
        </header>

        <section class="summary-grid">
          <article>
            <div class="summary-icon user-icon" aria-hidden="true"></div>
            <div>
              <span>当前用户</span>
              <strong>{{ currentUser?.displayName }}</strong>
              <small>{{ currentUser?.username }}</small>
            </div>
          </article>
          <article>
            <div class="summary-icon tenant-icon" aria-hidden="true"></div>
            <div>
              <span>租户上下文</span>
              <strong>{{ currentTenant?.tenantName }}</strong>
              <small><em :class="['status-badge', statusTone(currentTenant?.status)]">{{ statusLabel(currentTenant?.status || '') }}</em></small>
            </div>
          </article>
          <article>
            <div class="summary-icon menu-icon" aria-hidden="true"></div>
            <div>
              <span>授权菜单</span>
              <strong>{{ currentUser?.menus.length }}</strong>
              <small>{{ currentUser?.roles.map((role) => role.roleCode).join(', ') }}</small>
            </div>
          </article>
        </section>

        <section class="workspace">
          <div class="workspace-title">
            <div class="guard-icon" aria-hidden="true"></div>
            <div>
              <h2>租户访问校验</h2>
              <p>tenant_id 强隔离</p>
            </div>
          </div>
          <button data-test="forbidden-check" class="secondary-action" type="button" @click="checkForbidden">
            校验 403
          </button>
        </section>

        <p v-if="permissionMessage" class="message error">{{ permissionMessage }}</p>

        <section v-if="activeMenu === 'tenant.models'" class="provider-grid">
          <form class="provider-form" @submit.prevent="saveProvider">
            <div class="section-heading">
              <div>
                <h2>模型配置</h2>
                <p>API Key 加密入库</p>
              </div>
              <span class="section-kicker">租户模型</span>
            </div>

            <label>
              供应商类型
              <select v-model="selectedProviderType" name="providerType">
                <option v-for="option in enabledProviderTypes" :key="option.providerTypeId" :value="option.providerType">
                  {{ option.displayName }}
                </option>
              </select>
            </label>
            <label>
              Base URL
              <input name="baseUrl" :value="selectedProviderTypeOption?.defaultBaseUrl || ''" readonly />
            </label>
            <label>
              模型型号
              <select v-model="selectedModelOptionId" name="modelOption">
                <option v-for="option in modelOptionsForProvider" :key="option.modelOptionId" :value="option.modelOptionId">
                  {{ option.displayName }}
                </option>
              </select>
            </label>
            <label>
              API Key
              <input v-model="providerForm.apiKey" name="apiKey" type="password" autocomplete="off" />
            </label>
            <label>
              调用预算 USD
              <input v-model.number="providerForm.budgetUsd" name="budgetUsd" type="number" min="0" step="0.01" />
            </label>

            <div class="model-fields">
              <label>
                模型编码
                <input :value="selectedModelOption?.modelCode || ''" name="modelCode" readonly />
              </label>
              <label>
                能力类型
                <input :value="selectedModelOption?.capabilityType || ''" name="capabilityType" readonly />
              </label>
              <label>
                上下文窗口
                <input :value="selectedModelOption?.contextWindow ?? ''" name="contextWindow" readonly />
              </label>
              <label>
                最大输出 token
                <input :value="selectedModelOption?.maxOutputTokens ?? ''" name="maxOutputTokens" readonly />
              </label>
              <label>
                Temperature
                <input :value="selectedModelOption?.temperature ?? ''" name="temperature" readonly />
              </label>
              <label>
                Top P
                <input :value="selectedModelOption?.topP ?? ''" name="topP" readonly />
              </label>
            </div>

            <p v-if="providerError" class="message error">{{ providerError }}</p>
            <p v-if="providerMessage" class="message success">{{ providerMessage }}</p>
            <p class="hint">将测试：{{ modelTestTargetLabel }}</p>
            <div class="action-row">
              <button class="primary-action" type="submit" :disabled="providerSaving">
                <span class="button-icon save-icon" aria-hidden="true"></span>
                {{ providerSaving ? '保存中' : '保存配置' }}
              </button>
              <button
                data-test="provider-test"
                class="secondary-action"
                type="button"
                :disabled="providerTesting || !selectedConfiguredChatModel"
                @click="testFirstChatModel"
              >
                <span class="button-icon plug-icon" aria-hidden="true"></span>
                {{ providerTesting ? '测试中' : '连接测试' }}
              </button>
            </div>
          </form>

          <section class="provider-list">
            <div class="list-header">
              <div>
                <h2>已配置模型</h2>
                <p>供应商清单</p>
              </div>
              <span>{{ providers.length }}</span>
            </div>
            <article v-for="provider in providers" :key="provider.providerId" class="provider-item">
              <div class="provider-title">
                <div>
                  <strong>{{ provider.providerName }}</strong>
                  <small>{{ provider.baseUrl }} · ****{{ provider.apiKeyLast4 }} · ${{ provider.budgetUsd ?? '-' }}</small>
                </div>
                <span>{{ provider.providerType }}</span>
              </div>
              <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>型号</th>
                    <th>能力</th>
                    <th>上下文</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="model in provider.models" :key="model.modelId">
                    <td>{{ model.displayName || model.modelCode }}</td>
                    <td>{{ capabilityLabel(model.capabilityType) }}</td>
                    <td>{{ model.contextWindow ?? '-' }}</td>
                    <td><em :class="['status-badge', statusTone(model.status)]">{{ statusLabel(model.status) }}</em></td>
                  </tr>
                </tbody>
              </table>
              </div>
            </article>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.model-catalog'" class="catalog-grid">
          <form class="provider-form platform-provider-form" @submit.prevent="savePlatformProviderType">
            <div>
              <h2>平台模型基础配置</h2>
              <p>供应商协议与默认地址</p>
            </div>

            <label>
              供应商类型
              <select v-model="platformProviderForm.providerType" name="platformProviderType">
                <option v-for="providerType in providerTypeValues" :key="providerType" :value="providerType">
                  {{ providerType }}
                </option>
              </select>
            </label>
            <label>
              显示名称
              <input v-model="platformProviderForm.displayName" name="platformProviderDisplayName" />
            </label>
            <label>
              默认 Base URL
              <input v-model="platformProviderForm.defaultBaseUrl" name="platformProviderBaseUrl" />
            </label>
            <label>
              状态
              <select v-model="platformProviderForm.status" name="platformProviderStatus">
                <option value="ENABLED">已启用</option>
                <option value="DISABLED">已禁用</option>
              </select>
            </label>
            <label>
              排序
              <input v-model.number="platformProviderForm.sortOrder" name="platformProviderSortOrder" type="number" min="0" />
            </label>

            <p v-if="platformCatalogError" class="message error">{{ platformCatalogError }}</p>
            <p v-if="platformCatalogMessage" class="message success">{{ platformCatalogMessage }}</p>
            <button class="primary-action" type="submit" :disabled="platformProviderSaving">
              {{ platformProviderSaving ? '保存中' : '保存供应商类型' }}
            </button>
          </form>

          <form class="provider-form platform-model-form" @submit.prevent="savePlatformModelOption">
            <div>
              <h2>模型选项</h2>
              <p>租户下拉可选模型目录</p>
            </div>

            <label>
              供应商类型
              <select v-model="platformModelForm.providerTypeId" name="platformModelProviderTypeId">
                <option v-for="option in platformProviderTypes" :key="option.providerTypeId" :value="option.providerTypeId">
                  {{ option.displayName }} · {{ option.providerType }}
                </option>
              </select>
            </label>
            <label>
              模型编码
              <input v-model="platformModelForm.modelCode" name="platformModelCode" />
            </label>
            <label>
              显示名称
              <input v-model="platformModelForm.displayName" name="platformModelDisplayName" />
            </label>
            <label>
              能力类型
              <select v-model="platformModelForm.capabilityType" name="platformModelCapabilityType">
                <option v-for="capability in capabilityValues" :key="capability" :value="capability">
                  {{ capabilityLabel(capability) }}
                </option>
              </select>
            </label>
            <div class="model-fields">
              <label>
                上下文窗口
                <input v-model="platformModelForm.contextWindow" name="platformModelContextWindow" type="number" min="1" />
              </label>
              <label>
                最大输出 token
                <input v-model="platformModelForm.maxOutputTokens" name="platformModelMaxOutputTokens" type="number" min="1" />
              </label>
              <label>
                向量维度
                <input v-model="platformModelForm.embeddingDimensions" name="platformModelEmbeddingDimensions" type="number" min="1" />
              </label>
              <label>
                Temperature
                <input v-model="platformModelForm.temperature" name="platformModelTemperature" type="number" min="0" max="2" step="0.01" />
              </label>
              <label>
                Top P
                <input v-model="platformModelForm.topP" name="platformModelTopP" type="number" min="0" max="1" step="0.01" />
              </label>
              <label>
                Top K
                <input v-model="platformModelForm.topK" name="platformModelTopK" type="number" min="1" />
              </label>
              <label>
                频率惩罚
                <input v-model="platformModelForm.frequencyPenalty" name="platformModelFrequencyPenalty" type="number" min="-2" max="2" step="0.01" />
              </label>
              <label>
                存在惩罚
                <input v-model="platformModelForm.presencePenalty" name="platformModelPresencePenalty" type="number" min="-2" max="2" step="0.01" />
              </label>
            </div>
            <label>
              推理强度
              <input v-model="platformModelForm.reasoningEffort" name="platformModelReasoningEffort" />
            </label>
            <label>
              状态
              <select v-model="platformModelForm.status" name="platformModelStatus">
                <option value="ENABLED">已启用</option>
                <option value="DISABLED">已禁用</option>
              </select>
            </label>
            <label>
              排序
              <input v-model.number="platformModelForm.sortOrder" name="platformModelSortOrder" type="number" min="0" />
            </label>

            <button class="primary-action" type="submit" :disabled="platformModelSaving || !platformModelForm.providerTypeId">
              {{ platformModelSaving ? '保存中' : '保存模型选项' }}
            </button>
          </form>

          <section class="provider-list catalog-list">
            <div class="list-header">
              <h2>目录状态</h2>
              <span>{{ platformModelOptions.length }}</span>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>供应商</th>
                    <th>模型</th>
                    <th>能力</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="option in platformModelOptions" :key="option.modelOptionId">
                    <td>{{ platformProviderTypes.find((providerType) => providerType.providerTypeId === option.providerTypeId)?.displayName || option.providerTypeId }}</td>
                    <td>{{ option.displayName }}<br /><small>{{ option.modelCode }}</small></td>
                    <td>{{ capabilityLabel(option.capabilityType) }}</td>
                    <td>{{ statusLabel(option.status) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.tenants'" class="panel">
          <form class="tenant-form management-form" @submit.prevent="saveTenant">
            <div class="list-header">
              <h2>租户账户</h2>
              <span>{{ tenantForm.tenantId ? '编辑' : '新建' }}</span>
            </div>
            <div class="form-grid">
              <label>
                租户编码
                <input v-model="tenantForm.tenantCode" name="tenantCode" :disabled="Boolean(tenantForm.tenantId)" />
              </label>
              <label>
                租户名称
                <input v-model="tenantForm.tenantName" name="tenantName" />
              </label>
              <label>
                管理员账号
                <input v-model="tenantForm.adminUsername" name="adminUsername" :disabled="Boolean(tenantForm.tenantId)" />
              </label>
              <label>
                管理员名称
                <input v-model="tenantForm.adminDisplayName" name="adminDisplayName" :disabled="Boolean(tenantForm.tenantId)" />
              </label>
              <label>
                管理员密码
                <input v-model="tenantForm.adminPassword" name="adminPassword" type="password" autocomplete="new-password" :disabled="Boolean(tenantForm.tenantId)" />
              </label>
              <label>
                状态
                <select v-model="tenantForm.status" name="tenantStatus">
                  <option value="ENABLED">已启用</option>
                  <option value="DISABLED">已禁用</option>
                </select>
              </label>
            </div>
            <button class="primary-action" type="submit" :disabled="tenantSaving || !hasPermission('platform:tenant:write')">
              {{ tenantSaving ? '保存中' : '保存租户' }}
            </button>
          </form>

          <div class="list-header">
            <h2>租户账户</h2>
            <span>{{ tenants.length }}</span>
          </div>
          <p v-if="tenantError" class="message error">{{ tenantError }}</p>
          <p v-if="tenantMessage" class="message success">{{ tenantMessage }}</p>
          <div class="table-scroll">
          <table class="tenant-table">
            <thead>
              <tr>
                <th>租户编码</th>
                <th>租户名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tenant in tenants" :key="tenant.tenantId">
                <td>{{ tenant.tenantCode }}</td>
                <td>{{ tenant.tenantName }}</td>
                <td>{{ statusLabel(tenant.status) }}</td>
                <td>
                  <button
                    v-if="tenant.status === 'ENABLED'"
                    :data-test="`tenant-disable-${tenant.tenantId}`"
                    class="secondary-action compact-action"
                    type="button"
                    :disabled="tenantUpdatingId === tenant.tenantId"
                    @click="updateTenantStatus(tenant, 'DISABLED')"
                  >
                    {{ tenantUpdatingId === tenant.tenantId ? '处理中' : '禁用' }}
                  </button>
                  <button
                    v-else
                    :data-test="`tenant-enable-${tenant.tenantId}`"
                    class="secondary-action compact-action"
                    type="button"
                    :disabled="tenantUpdatingId === tenant.tenantId"
                    @click="updateTenantStatus(tenant, 'ENABLED')"
                  >
                    {{ tenantUpdatingId === tenant.tenantId ? '处理中' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          </div>
        </section>

        <section v-else-if="activeMenu === 'platform.roles' || activeMenu === 'platform.rbac'" class="panel">
          <div class="list-header">
            <h2>角色管理</h2>
            <span>{{ roleManagement?.roles.length || 0 }}</span>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <form class="role-form management-form" @submit.prevent="saveRole">
            <div class="form-grid">
              <label>
                角色编码
                <input v-model="roleForm.roleCode" name="roleCode" />
              </label>
              <label>
                角色名称
                <input v-model="roleForm.roleName" name="roleName" />
              </label>
              <label>
                状态
                <select v-model="roleForm.status" name="roleStatus">
                  <option value="ENABLED">已启用</option>
                  <option value="DISABLED">已禁用</option>
                </select>
              </label>
            </div>
            <button class="primary-action" type="submit" :disabled="roleSaving || !hasPermission('rbac:role:write')">
              {{ roleSaving ? '保存中' : '保存角色' }}
            </button>
          </form>
          <article v-for="role in roleManagement?.roles" :key="role.roleId" class="role-card">
            <div class="provider-title">
              <strong>{{ role.roleName }}</strong>
              <span>{{ role.roleCode }}</span>
            </div>
            <div class="rbac-grid">
              <section>
                <h3>权限点</h3>
                <label v-for="permission in roleManagementPermissions" :key="permission.permissionCode" class="check-row">
                  <input
                    v-model="roleBinding(role.roleId).permissionCodes"
                    :data-test="bindingTestId('permission', role.roleId, permission.permissionCode)"
                    type="checkbox"
                    :value="permission.permissionCode"
                  />
                  <span>{{ permission.permissionName }} · {{ permission.permissionCode }}</span>
                </label>
              </section>
              <section>
                <h3>菜单</h3>
                <label v-for="menu in roleManagementMenus" :key="menu.menuCode" class="check-row">
                  <input
                    v-model="roleBinding(role.roleId).menuCodes"
                    :data-test="bindingTestId('menu', role.roleId, menu.menuCode)"
                    type="checkbox"
                    :value="menu.menuCode"
                  />
                  <span>{{ menu.menuName }} · {{ menu.menuCode }}</span>
                </label>
              </section>
            </div>
            <button
              :data-test="`save-role-${role.roleId}`"
              class="primary-action"
              type="button"
              :disabled="rbacSavingRoleId === role.roleId"
              @click="saveRoleBindings(role)"
            >
              {{ rbacSavingRoleId === role.roleId ? '保存中' : '保存角色配置' }}
            </button>
          </article>
        </section>

        <section v-else-if="activeMenu === 'platform.permissions'" class="panel">
          <div class="list-header">
            <h2>权限管理</h2>
            <span>{{ roleManagement?.permissions.length || 0 }}</span>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <form class="permission-form management-form" @submit.prevent="savePermission">
            <div class="form-grid">
              <label>
                权限编码
                <input v-model="permissionForm.permissionCode" name="permissionCode" />
              </label>
              <label>
                权限名称
                <input v-model="permissionForm.permissionName" name="permissionName" />
              </label>
              <label>
                状态
                <select v-model="permissionForm.status" name="permissionStatus">
                  <option value="ENABLED">已启用</option>
                  <option value="DISABLED">已禁用</option>
                </select>
              </label>
            </div>
            <button class="primary-action" type="submit" :disabled="permissionSaving || !hasPermission('rbac:permission:write')">
              {{ permissionSaving ? '保存中' : '保存权限点' }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>权限编码</th>
                  <th>权限名称</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="permission in roleManagement?.permissions" :key="permission.permissionId">
                  <td>{{ permission.permissionCode }}</td>
                  <td>{{ permission.permissionName }}</td>
                  <td>{{ statusLabel(permission.status || 'ENABLED') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-else-if="activeMenu === 'platform.menus'" class="panel">
          <div class="list-header">
            <h2>菜单管理</h2>
            <span>{{ roleManagement?.menus.length || 0 }}</span>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <form class="menu-form management-form" @submit.prevent="saveMenu">
            <div class="form-grid">
              <label>
                菜单编码
                <input v-model="menuForm.menuCode" name="menuCode" />
              </label>
              <label>
                菜单名称
                <input v-model="menuForm.menuName" name="menuName" />
              </label>
              <label>
                上级菜单
                <select v-model="menuForm.parentCode" name="menuParentCode">
                  <option value="">无</option>
                  <option v-for="menu in roleManagement?.menus" :key="menu.menuCode" :value="menu.menuCode">
                    {{ menu.menuName }} · {{ menu.menuCode }}
                  </option>
                </select>
              </label>
              <label>
                路由
                <input v-model="menuForm.routePath" name="menuRoutePath" />
              </label>
              <label>
                组件 Key
                <input v-model="menuForm.componentKey" name="menuComponentKey" />
              </label>
              <label>
                菜单范围
                <select v-model="menuForm.platformScope" name="menuPlatformScope">
                  <option v-for="scope in menuScopeValues" :key="scope" :value="scope">{{ scope }}</option>
                </select>
              </label>
              <label>
                排序
                <input v-model.number="menuForm.sortOrder" name="menuSortOrder" type="number" min="0" />
              </label>
              <label>
                状态
                <select v-model="menuForm.status" name="menuStatus">
                  <option value="ENABLED">已启用</option>
                  <option value="DISABLED">已禁用</option>
                </select>
              </label>
            </div>
            <button class="primary-action" type="submit" :disabled="menuSaving || !hasPermission('rbac:menu:write')">
              {{ menuSaving ? '保存中' : '保存菜单' }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>菜单编码</th>
                  <th>上级</th>
                  <th>路由</th>
                  <th>组件</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="menu in roleManagement?.menus" :key="menu.menuId">
                  <td>{{ menu.menuName }}<br /><small>{{ menu.menuCode }}</small></td>
                  <td>{{ menu.parentCode || '-' }}</td>
                  <td>{{ menu.routePath }}</td>
                  <td>{{ menu.componentKey }}</td>
                  <td>{{ statusLabel(menu.status || 'ENABLED') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>
