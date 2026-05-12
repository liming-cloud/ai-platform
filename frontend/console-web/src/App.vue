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

type ChatInvocationResponse = {
  success: boolean;
  providerId: string;
  modelId: string;
  providerName: string;
  modelCode: string;
  displayName: string;
  capabilityType: string;
  traceId: string;
  latencyMs: number;
  output: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  usageMissingReason: string | null;
  costUsd: number | null;
  costMissingReason: string | null;
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

type PermissionGroup = {
  groupCode: string;
  title: string;
  permissions: PermissionResponse[];
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
const invocationForm = reactive({
  modelId: '',
  prompt: '请用一句话回复：AI Platform 模型调用链路正常。'
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
const invocationResult = ref<ChatInvocationResponse | null>(null);
const invocationError = ref('');
const chatInvoking = ref(false);
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
const roleManagementPermissions = computed(() => (roleManagement.value?.permissions || []).filter((permission) => permission.status !== 'DISABLED'));
const permissionGroups = computed(() => groupPermissions(roleManagementPermissions.value));
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
const canInvokeModel = computed(() => hasPermission('model:invocation:execute'));
const configuredChatModelRows = computed(() => tenantModelRows.value.filter((row) => row.provider.status === 'ENABLED'
  && row.model.status === 'ENABLED'
  && row.model.capabilityType === 'CHAT'));
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

watch(configuredChatModelRows, () => {
  normalizeInvocationModelSelection();
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
  normalizeInvocationModelSelection();
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
    normalizeInvocationModelSelection();
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

async function invokeChatModel() {
  invocationError.value = '';
  invocationResult.value = null;
  if (!canInvokeModel.value) {
    invocationError.value = '缺少模型调用权限';
    return;
  }
  if (!invocationForm.modelId) {
    invocationError.value = '请先选择已启用的聊天模型';
    return;
  }
  if (!invocationForm.prompt.trim()) {
    invocationError.value = '请输入调用 prompt';
    return;
  }
  chatInvoking.value = true;
  try {
    invocationResult.value = await request<ChatInvocationResponse>('/api/model-invocations/chat', {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({
        modelId: invocationForm.modelId,
        prompt: invocationForm.prompt
      })
    });
  } catch (error) {
    invocationError.value = error instanceof Error ? error.message : '模型调用失败';
  } finally {
    chatInvoking.value = false;
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
  invocationResult.value = null;
  invocationError.value = '';
  invocationForm.modelId = '';
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
  closeDialog();
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

function normalizeInvocationModelSelection() {
  if (invocationForm.modelId && configuredChatModelRows.value.some((row) => row.model.modelId === invocationForm.modelId)) {
    return;
  }
  invocationForm.modelId = configuredChatModelRows.value[0]?.model.modelId || '';
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

function pageItems<T>(items: T[], page: number) {
  const boundedPage = Math.min(Math.max(page, 1), totalPages(items.length));
  const start = (boundedPage - 1) * pageSize;
  return items.slice(start, start + pageSize);
}

function totalPages(total: number) {
  return Math.max(1, Math.ceil(total / pageSize));
}

function pageStart(total: number, page: number) {
  if (total === 0) {
    return 0;
  }
  return (Math.min(Math.max(page, 1), totalPages(total)) - 1) * pageSize + 1;
}

function pageEnd(total: number, page: number) {
  if (total === 0) {
    return 0;
  }
  return Math.min(pageStart(total, page) + pageSize - 1, total);
}

function setPage(pageKey: keyof typeof listPage, nextPage: number, total: number) {
  listPage[pageKey] = Math.min(Math.max(nextPage, 1), totalPages(total));
}

function buildMenuTree(menus: MenuResponse[]) {
  const sorted = [...menus].sort((left, right) => left.sortOrder - right.sortOrder || left.menuCode.localeCompare(right.menuCode));
  const menuCodes = new Set(sorted.map((menu) => menu.menuCode));
  const childrenByParent = new Map<string, MenuResponse[]>();
  for (const menu of sorted) {
    const parentKey = menu.parentCode && menuCodes.has(menu.parentCode) ? menu.parentCode : '';
    childrenByParent.set(parentKey, [...(childrenByParent.get(parentKey) || []), menu]);
  }

  const rows: MenuTreeRow[] = [];
  const visited = new Set<string>();
  const appendChildren = (parentCode: string, level: number) => {
    for (const menu of childrenByParent.get(parentCode) || []) {
      if (visited.has(menu.menuCode)) {
        continue;
      }
      visited.add(menu.menuCode);
      rows.push({
        ...menu,
        level,
        childCount: childrenByParent.get(menu.menuCode)?.length || 0
      });
      appendChildren(menu.menuCode, level + 1);
    }
  };

  appendChildren('', 0);
  for (const menu of sorted) {
    if (!visited.has(menu.menuCode)) {
      rows.push({
        ...menu,
        level: 0,
        childCount: childrenByParent.get(menu.menuCode)?.length || 0
      });
    }
  }
  return rows;
}

function groupPermissions(permissions: PermissionResponse[]): PermissionGroup[] {
  const groups = new Map<string, PermissionResponse[]>();
  for (const permission of permissions) {
    const groupCode = permission.permissionCode.split(':')[0] || 'other';
    groups.set(groupCode, [...(groups.get(groupCode) || []), permission]);
  }
  return [...groups.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([groupCode, groupPermissions]) => ({
      groupCode,
      title: permissionGroupName(groupCode),
      permissions: groupPermissions.sort((left, right) => left.permissionCode.localeCompare(right.permissionCode))
    }));
}

function permissionGroupName(groupCode: string) {
  const labels: Record<string, string> = {
    model: '模型',
    platform: '平台',
    rbac: '权限',
    tenant: '租户'
  };
  return labels[groupCode] || groupCode;
}

function providerTypeName(providerTypeId: string) {
  return platformProviderTypes.value.find((providerType) => providerType.providerTypeId === providerTypeId)?.displayName || providerTypeId;
}

function menuParentName(parentCode: string | null) {
  if (!parentCode) {
    return '根节点';
  }
  const parent = roleManagement.value?.menus.find((menu) => menu.menuCode === parentCode);
  return parent ? `${parent.menuName} · ${parent.menuCode}` : parentCode;
}

function openDialog(name: DialogName, mode: ModalMode, title: string) {
  dialog.name = name;
  dialog.mode = mode;
  dialog.title = title;
}

function closeDialog() {
  dialog.name = '';
  dialog.mode = 'create';
  dialog.title = '';
  selectedProvider.value = null;
  selectedProviderModel.value = null;
  selectedPlatformProvider.value = null;
  selectedPlatformModel.value = null;
  selectedTenant.value = null;
  selectedRole.value = null;
  selectedPermission.value = null;
  selectedMenu.value = null;
}

function openTenantModelCreate() {
  providerError.value = '';
  providerMessage.value = '';
  providerForm.apiKey = '';
  providerForm.budgetUsd = 5;
  selectedProviderType.value = enabledProviderTypes.value[0]?.providerType || '';
  selectedModelOptionId.value = modelOptionsForProvider.value[0]?.modelOptionId || '';
  openDialog('tenant-model', 'create', '新增模型配置');
}

function openTenantModelEdit(row: ProviderModelRow) {
  providerError.value = '';
  providerMessage.value = '';
  selectedProvider.value = row.provider;
  selectedProviderModel.value = row.model;
  providerForm.apiKey = '';
  providerForm.budgetUsd = row.provider.budgetUsd ?? 0;
  selectedProviderType.value = row.provider.providerType;
  selectedModelOptionId.value = modelOptionsForProvider.value.find((option) => option.modelCode === row.model.modelCode)?.modelOptionId
    || modelOptionsForProvider.value[0]?.modelOptionId
    || '';
  openDialog('tenant-model', 'edit', `编辑模型配置 · ${row.model.displayName || row.model.modelCode}`);
}

function openTenantModelDetail(row: ProviderModelRow) {
  selectedProvider.value = row.provider;
  selectedProviderModel.value = row.model;
  openDialog('tenant-model-detail', 'detail', `模型配置详情 · ${row.model.displayName || row.model.modelCode}`);
}

function resetPlatformProviderForm() {
  const existingTypes = new Set(platformProviderTypes.value.map((providerType) => providerType.providerType));
  const providerType = providerTypeValues.find((value) => !existingTypes.has(value)) || providerTypeValues[0];
  platformProviderForm.providerTypeId = '';
  platformProviderForm.providerType = providerType;
  platformProviderForm.displayName = providerType === 'OPENAI_COMPATIBLE' ? 'OpenAI 兼容供应商' : providerType;
  platformProviderForm.defaultBaseUrl = providerType === 'OPENAI_COMPATIBLE' ? 'https://api.siliconflow.cn' : '';
  platformProviderForm.status = 'ENABLED';
  platformProviderForm.sortOrder = 20;
}

function seedPlatformProviderForm(option: ProviderTypeOptionResponse) {
  selectedPlatformProvider.value = option;
  platformProviderForm.providerTypeId = option.providerTypeId;
  platformProviderForm.providerType = option.providerType;
  platformProviderForm.displayName = option.displayName;
  platformProviderForm.defaultBaseUrl = option.defaultBaseUrl;
  platformProviderForm.status = option.status;
  platformProviderForm.sortOrder = option.sortOrder;
}

function openPlatformProviderCreate() {
  platformCatalogError.value = '';
  platformCatalogMessage.value = '';
  resetPlatformProviderForm();
  openDialog('platform-provider', 'create', '新增供应商类型');
}

function openPlatformProviderEdit(option: ProviderTypeOptionResponse) {
  platformCatalogError.value = '';
  platformCatalogMessage.value = '';
  seedPlatformProviderForm(option);
  openDialog('platform-provider', 'edit', `编辑供应商类型 · ${option.displayName}`);
}

function openPlatformProviderDetail(option: ProviderTypeOptionResponse) {
  seedPlatformProviderForm(option);
  openDialog('platform-provider', 'detail', `供应商类型详情 · ${option.displayName}`);
}

function resetPlatformModelForm() {
  platformModelForm.modelOptionId = '';
  platformModelForm.providerTypeId = platformProviderTypes.value[0]?.providerTypeId || '';
  platformModelForm.modelCode = '';
  platformModelForm.displayName = '';
  platformModelForm.capabilityType = 'CHAT';
  platformModelForm.contextWindow = '';
  platformModelForm.maxOutputTokens = '';
  platformModelForm.embeddingDimensions = '';
  platformModelForm.temperature = '';
  platformModelForm.topP = '';
  platformModelForm.topK = '';
  platformModelForm.frequencyPenalty = '';
  platformModelForm.presencePenalty = '';
  platformModelForm.reasoningEffort = '';
  platformModelForm.status = 'ENABLED';
  platformModelForm.sortOrder = 30;
}

function seedPlatformModelForm(option: ModelOptionResponse) {
  selectedPlatformModel.value = option;
  platformModelForm.modelOptionId = option.modelOptionId;
  platformModelForm.providerTypeId = option.providerTypeId;
  platformModelForm.modelCode = option.modelCode;
  platformModelForm.displayName = option.displayName;
  platformModelForm.capabilityType = option.capabilityType;
  platformModelForm.contextWindow = option.contextWindow?.toString() || '';
  platformModelForm.maxOutputTokens = option.maxOutputTokens?.toString() || '';
  platformModelForm.embeddingDimensions = option.embeddingDimensions?.toString() || '';
  platformModelForm.temperature = option.temperature?.toString() || '';
  platformModelForm.topP = option.topP?.toString() || '';
  platformModelForm.topK = option.topK?.toString() || '';
  platformModelForm.frequencyPenalty = option.frequencyPenalty?.toString() || '';
  platformModelForm.presencePenalty = option.presencePenalty?.toString() || '';
  platformModelForm.reasoningEffort = option.reasoningEffort || '';
  platformModelForm.status = option.status;
  platformModelForm.sortOrder = option.sortOrder;
}

function openPlatformModelCreate() {
  platformCatalogError.value = '';
  platformCatalogMessage.value = '';
  resetPlatformModelForm();
  openDialog('platform-model', 'create', '新增模型选项');
}

function openPlatformModelEdit(option: ModelOptionResponse) {
  platformCatalogError.value = '';
  platformCatalogMessage.value = '';
  seedPlatformModelForm(option);
  openDialog('platform-model', 'edit', `编辑模型选项 · ${option.displayName}`);
}

function openPlatformModelDetail(option: ModelOptionResponse) {
  seedPlatformModelForm(option);
  openDialog('platform-model', 'detail', `模型选项详情 · ${option.displayName}`);
}

function openTenantCreate() {
  tenantError.value = '';
  tenantMessage.value = '';
  resetTenantForm();
  openDialog('tenant', 'create', '新增租户');
}

function openTenantEdit(tenant: TenantResponse) {
  tenantError.value = '';
  tenantMessage.value = '';
  selectedTenant.value = tenant;
  tenantForm.tenantId = tenant.tenantId;
  tenantForm.tenantCode = tenant.tenantCode;
  tenantForm.tenantName = tenant.tenantName;
  tenantForm.adminUsername = '';
  tenantForm.adminDisplayName = '';
  tenantForm.adminPassword = '';
  tenantForm.status = tenant.status;
  openDialog('tenant', 'edit', `编辑租户 · ${tenant.tenantName}`);
}

function openTenantDetail(tenant: TenantResponse) {
  selectedTenant.value = tenant;
  openDialog('tenant', 'detail', `租户详情 · ${tenant.tenantName}`);
}

function openRoleCreate() {
  rbacError.value = '';
  rbacMessage.value = '';
  resetRoleForm();
  openDialog('role', 'create', '新增角色');
}

function openRoleEdit(role: RoleAccessResponse) {
  rbacError.value = '';
  rbacMessage.value = '';
  selectedRole.value = role;
  roleForm.roleId = role.roleId;
  roleForm.roleCode = role.roleCode;
  roleForm.roleName = role.roleName;
  roleForm.status = role.status;
  openDialog('role', 'edit', `编辑角色 · ${role.roleName}`);
}

function openRoleDetail(role: RoleAccessResponse) {
  selectedRole.value = role;
  openDialog('role', 'detail', `角色详情 · ${role.roleName}`);
}

function openRoleAccess(role: RoleAccessResponse) {
  selectedRole.value = role;
  roleBinding(role.roleId);
  openDialog('role-access', 'access', `角色权限设置 · ${role.roleName}`);
}

function saveRoleBindingsFromDialog() {
  if (selectedRole.value) {
    saveRoleBindings(selectedRole.value);
  }
}

function openPermissionCreate() {
  rbacError.value = '';
  rbacMessage.value = '';
  resetPermissionForm();
  openDialog('permission', 'create', '新增权限点');
}

function openPermissionEdit(permission: PermissionResponse) {
  rbacError.value = '';
  rbacMessage.value = '';
  selectedPermission.value = permission;
  permissionForm.permissionId = permission.permissionId;
  permissionForm.permissionCode = permission.permissionCode;
  permissionForm.permissionName = permission.permissionName;
  permissionForm.status = permission.status || 'ENABLED';
  openDialog('permission', 'edit', `编辑权限点 · ${permission.permissionName}`);
}

function openPermissionDetail(permission: PermissionResponse) {
  selectedPermission.value = permission;
  openDialog('permission', 'detail', `权限点详情 · ${permission.permissionName}`);
}

function openMenuCreate(parentCode = '') {
  rbacError.value = '';
  rbacMessage.value = '';
  resetMenuForm();
  menuForm.parentCode = parentCode;
  openDialog('menu', 'create', parentCode ? `新增子菜单 · ${parentCode}` : '新增菜单');
}

function openMenuEdit(menu: MenuResponse) {
  rbacError.value = '';
  rbacMessage.value = '';
  selectedMenu.value = menu;
  menuForm.menuId = menu.menuId;
  menuForm.menuCode = menu.menuCode;
  menuForm.menuName = menu.menuName;
  menuForm.parentCode = menu.parentCode || '';
  menuForm.routePath = menu.routePath;
  menuForm.componentKey = menu.componentKey;
  menuForm.platformScope = menu.platformScope;
  menuForm.sortOrder = menu.sortOrder;
  menuForm.status = menu.status || 'ENABLED';
  openDialog('menu', 'edit', `编辑菜单 · ${menu.menuName}`);
}

function openMenuDetail(menu: MenuResponse) {
  selectedMenu.value = menu;
  openDialog('menu', 'detail', `菜单详情 · ${menu.menuName}`);
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

        <section v-if="activeMenu === 'tenant.models'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>租户模型配置</h2>
              <p>模型配置以列表验收，API Key 只输入不回显。</p>
            </div>
            <div class="toolbar-actions">
              <button data-test="provider-test" class="secondary-action" type="button" :disabled="providerTesting || !selectedConfiguredChatModel" @click="testFirstChatModel">
                <span class="button-icon plug-icon" aria-hidden="true"></span>
                {{ providerTesting ? '测试中' : '连接测试' }}
              </button>
              <button data-test="open-tenant-model-create" class="primary-action" type="button" @click="openTenantModelCreate">
                新增模型配置
              </button>
            </div>
          </div>
          <p v-if="providerError" class="message error">{{ providerError }}</p>
          <p v-if="providerMessage" class="message success">{{ providerMessage }}</p>
          <p class="hint">将测试：{{ modelTestTargetLabel }}</p>

          <section class="panel list-panel">
            <div class="list-header">
              <div>
                <h2>已配置模型</h2>
                <p>共 {{ tenantModelRows.length }} 条，按供应商和模型展开。</p>
              </div>
              <span>{{ providers.length }} 个供应商</span>
            </div>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>供应商</th>
                    <th>模型</th>
                    <th>能力</th>
                    <th>上下文</th>
                    <th>预算</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="tenantModelRows.length === 0">
                    <td colspan="7"><div class="empty-state">暂无模型配置</div></td>
                  </tr>
                  <tr v-for="row in pagedTenantModelRows" :key="row.model.modelId">
                    <td>
                      <strong>{{ row.provider.providerName }}</strong><br />
                      <small>{{ row.provider.baseUrl }} · ****{{ row.provider.apiKeyLast4 || '-' }}</small>
                    </td>
                    <td>{{ row.model.displayName || row.model.modelCode }}<br /><small>{{ row.model.modelCode }}</small></td>
                    <td>{{ capabilityLabel(row.model.capabilityType) }}</td>
                    <td>{{ row.model.contextWindow ?? '-' }}</td>
                    <td>${{ row.provider.budgetUsd ?? '-' }}</td>
                    <td><em :class="['status-badge', statusTone(row.model.status)]">{{ statusLabel(row.model.status) }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openTenantModelDetail(row)">详情</button>
                        <button class="secondary-action compact-action" type="button" @click="openTenantModelEdit(row)">编辑</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(tenantModelRows.length, listPage.tenantModels) }}-{{ pageEnd(tenantModelRows.length, listPage.tenantModels) }} / {{ tenantModelRows.length }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.tenantModels <= 1" @click="setPage('tenantModels', listPage.tenantModels - 1, tenantModelRows.length)">上一页</button>
                <strong>{{ listPage.tenantModels }} / {{ totalPages(tenantModelRows.length) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.tenantModels >= totalPages(tenantModelRows.length)" @click="setPage('tenantModels', listPage.tenantModels + 1, tenantModelRows.length)">下一页</button>
              </div>
            </div>
          </section>

          <section class="panel invocation-panel">
            <div class="list-header">
              <div>
                <h2>模型调用验收</h2>
                <p>正式调用链路返回 trace、延迟、token usage 与成本状态。</p>
              </div>
              <span>{{ configuredChatModelRows.length }} 个可调用模型</span>
            </div>
            <div class="invocation-layout">
              <form class="invocation-form" @submit.prevent="invokeChatModel">
                <label>
                  <span>调用模型</span>
                  <select v-model="invocationForm.modelId" data-test="invocation-model" name="invocationModel" :disabled="chatInvoking || configuredChatModelRows.length === 0">
                    <option v-for="row in configuredChatModelRows" :key="row.model.modelId" :value="row.model.modelId">
                      {{ row.provider.providerName }} · {{ row.model.displayName || row.model.modelCode }}
                    </option>
                  </select>
                </label>
                <label class="wide-field">
                  <span>Prompt</span>
                  <textarea v-model="invocationForm.prompt" data-test="invocation-prompt" name="invocationPrompt" rows="4" :disabled="chatInvoking"></textarea>
                </label>
                <div class="action-row">
                  <button data-test="invoke-chat" class="primary-action" type="submit" :disabled="chatInvoking || !canInvokeModel || configuredChatModelRows.length === 0">
                    {{ chatInvoking ? '调用中' : '执行调用' }}
                  </button>
                </div>
              </form>
              <div class="invocation-result">
                <p v-if="invocationError" class="message error">{{ invocationError }}</p>
                <div v-if="invocationResult" class="invocation-result-body">
                  <dl class="invocation-metrics">
                    <div>
                      <dt>Trace ID</dt>
                      <dd>{{ invocationResult.traceId }}</dd>
                    </div>
                    <div>
                      <dt>延迟</dt>
                      <dd>{{ invocationResult.latencyMs }}ms</dd>
                    </div>
                    <div>
                      <dt>Token</dt>
                      <dd>{{ invocationResult.totalTokens ?? '-' }}</dd>
                    </div>
                    <div>
                      <dt>成本</dt>
                      <dd>{{ invocationResult.costUsd ?? invocationResult.costMissingReason ?? '-' }}</dd>
                    </div>
                  </dl>
                  <pre>{{ invocationResult.output }}</pre>
                  <p v-if="invocationResult.usageMissingReason" class="hint">{{ invocationResult.usageMissingReason }}</p>
                </div>
                <div v-else-if="!invocationError" class="empty-state">选择模型并执行一次调用</div>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.model-catalog'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>平台模型基础配置</h2>
              <p>供应商类型和模型选项分别以列表维护。</p>
            </div>
            <div class="toolbar-actions">
              <button data-test="open-platform-provider-create" class="secondary-action" type="button" @click="openPlatformProviderCreate">新增供应商类型</button>
              <button data-test="open-platform-model-create" class="primary-action" type="button" @click="openPlatformModelCreate">新增模型选项</button>
            </div>
          </div>
          <p v-if="platformCatalogError" class="message error">{{ platformCatalogError }}</p>
          <p v-if="platformCatalogMessage" class="message success">{{ platformCatalogMessage }}</p>

          <section class="panel list-panel">
            <div class="list-header">
              <div>
                <h2>供应商类型</h2>
                <p>平台协议、默认地址和租户下拉入口。</p>
              </div>
              <span>{{ platformProviderTypes.length }}</span>
            </div>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>显示名称</th>
                    <th>类型</th>
                    <th>默认 Base URL</th>
                    <th>排序</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="option in pagedPlatformProviderTypes" :key="option.providerTypeId">
                    <td><strong>{{ option.displayName }}</strong></td>
                    <td>{{ option.providerType }}</td>
                    <td>{{ option.defaultBaseUrl || '-' }}</td>
                    <td>{{ option.sortOrder }}</td>
                    <td><em :class="['status-badge', statusTone(option.status)]">{{ statusLabel(option.status) }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openPlatformProviderDetail(option)">详情</button>
                        <button :data-test="`edit-platform-provider-${option.providerTypeId}`" class="secondary-action compact-action" type="button" @click="openPlatformProviderEdit(option)">编辑</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(platformProviderTypes.length, listPage.platformProviders) }}-{{ pageEnd(platformProviderTypes.length, listPage.platformProviders) }} / {{ platformProviderTypes.length }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.platformProviders <= 1" @click="setPage('platformProviders', listPage.platformProviders - 1, platformProviderTypes.length)">上一页</button>
                <strong>{{ listPage.platformProviders }} / {{ totalPages(platformProviderTypes.length) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.platformProviders >= totalPages(platformProviderTypes.length)" @click="setPage('platformProviders', listPage.platformProviders + 1, platformProviderTypes.length)">下一页</button>
              </div>
            </div>
          </section>

          <section class="panel list-panel">
            <div class="list-header">
              <div>
                <h2>模型选项</h2>
                <p>租户模型配置只能从启用目录中选择。</p>
              </div>
              <span>{{ platformModelOptions.length }}</span>
            </div>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>供应商</th>
                    <th>模型</th>
                    <th>能力</th>
                    <th>上下文</th>
                    <th>排序</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="option in pagedPlatformModelOptions" :key="option.modelOptionId">
                    <td>{{ providerTypeName(option.providerTypeId) }}</td>
                    <td>{{ option.displayName }}<br /><small>{{ option.modelCode }}</small></td>
                    <td>{{ capabilityLabel(option.capabilityType) }}</td>
                    <td>{{ option.contextWindow ?? '-' }}</td>
                    <td>{{ option.sortOrder }}</td>
                    <td><em :class="['status-badge', statusTone(option.status)]">{{ statusLabel(option.status) }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openPlatformModelDetail(option)">详情</button>
                        <button class="secondary-action compact-action" type="button" @click="openPlatformModelEdit(option)">编辑</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(platformModelOptions.length, listPage.platformModels) }}-{{ pageEnd(platformModelOptions.length, listPage.platformModels) }} / {{ platformModelOptions.length }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.platformModels <= 1" @click="setPage('platformModels', listPage.platformModels - 1, platformModelOptions.length)">上一页</button>
                <strong>{{ listPage.platformModels }} / {{ totalPages(platformModelOptions.length) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.platformModels >= totalPages(platformModelOptions.length)" @click="setPage('platformModels', listPage.platformModels + 1, platformModelOptions.length)">下一页</button>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.tenants'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>租户账户</h2>
              <p>默认进入分页列表，新增和编辑在弹窗中完成。</p>
            </div>
            <button data-test="open-tenant-create" class="primary-action" type="button" :disabled="!canSaveTenant" @click="openTenantCreate">新增租户</button>
          </div>
          <p v-if="tenantError" class="message error">{{ tenantError }}</p>
          <p v-if="tenantMessage" class="message success">{{ tenantMessage }}</p>
          <section class="panel list-panel">
            <div class="table-scroll">
              <table class="tenant-table data-table">
                <thead>
                  <tr>
                    <th>租户编码</th>
                    <th>租户名称</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="tenant in pagedTenants" :key="tenant.tenantId">
                    <td><strong>{{ tenant.tenantCode }}</strong></td>
                    <td>{{ tenant.tenantName }}</td>
                    <td><em :class="['status-badge', statusTone(tenant.status)]">{{ statusLabel(tenant.status) }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openTenantDetail(tenant)">详情</button>
                        <button class="secondary-action compact-action" type="button" :disabled="!canSaveTenant" @click="openTenantEdit(tenant)">编辑</button>
                        <button
                          v-if="tenant.status === 'ENABLED'"
                          :data-test="`tenant-disable-${tenant.tenantId}`"
                          class="secondary-action compact-action"
                          type="button"
                          :disabled="tenantUpdatingId === tenant.tenantId || !canSaveTenant"
                          @click="updateTenantStatus(tenant, 'DISABLED')"
                        >
                          {{ tenantUpdatingId === tenant.tenantId ? '处理中' : '禁用' }}
                        </button>
                        <button
                          v-else
                          :data-test="`tenant-enable-${tenant.tenantId}`"
                          class="secondary-action compact-action"
                          type="button"
                          :disabled="tenantUpdatingId === tenant.tenantId || !canSaveTenant"
                          @click="updateTenantStatus(tenant, 'ENABLED')"
                        >
                          {{ tenantUpdatingId === tenant.tenantId ? '处理中' : '启用' }}
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(tenants.length, listPage.tenants) }}-{{ pageEnd(tenants.length, listPage.tenants) }} / {{ tenants.length }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.tenants <= 1" @click="setPage('tenants', listPage.tenants - 1, tenants.length)">上一页</button>
                <strong>{{ listPage.tenants }} / {{ totalPages(tenants.length) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.tenants >= totalPages(tenants.length)" @click="setPage('tenants', listPage.tenants + 1, tenants.length)">下一页</button>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.roles' || activeMenu === 'platform.rbac'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>角色管理</h2>
              <p>角色基本信息和权限配置分离，权限在专用弹窗中验收。</p>
            </div>
            <button data-test="open-role-create" class="primary-action" type="button" :disabled="!canSaveRole" @click="openRoleCreate">新增角色</button>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <section class="panel list-panel">
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>角色</th>
                    <th>权限点</th>
                    <th>菜单</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="role in pagedRoles" :key="role.roleId">
                    <td><strong>{{ role.roleName }}</strong><br /><small>{{ role.roleCode }}</small></td>
                    <td>{{ role.permissions.length }}</td>
                    <td>{{ role.menus.length }}</td>
                    <td><em :class="['status-badge', statusTone(role.status)]">{{ statusLabel(role.status) }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openRoleDetail(role)">详情</button>
                        <button class="secondary-action compact-action" type="button" :disabled="!canSaveRole" @click="openRoleEdit(role)">编辑</button>
                        <button :data-test="`open-role-access-${role.roleId}`" class="primary-action compact-action" type="button" :disabled="!canSaveRole" @click="openRoleAccess(role)">权限设置</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(roleManagement?.roles.length || 0, listPage.roles) }}-{{ pageEnd(roleManagement?.roles.length || 0, listPage.roles) }} / {{ roleManagement?.roles.length || 0 }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.roles <= 1" @click="setPage('roles', listPage.roles - 1, roleManagement?.roles.length || 0)">上一页</button>
                <strong>{{ listPage.roles }} / {{ totalPages(roleManagement?.roles.length || 0) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.roles >= totalPages(roleManagement?.roles.length || 0)" @click="setPage('roles', listPage.roles + 1, roleManagement?.roles.length || 0)">下一页</button>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.permissions'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>权限管理</h2>
              <p>权限点以列表维护，长编码允许换行但不撑破表格。</p>
            </div>
            <button data-test="open-permission-create" class="primary-action" type="button" :disabled="!canSavePermission" @click="openPermissionCreate">新增权限点</button>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <section class="panel list-panel">
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>权限编码</th>
                    <th>权限名称</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="permission in pagedPermissions" :key="permission.permissionId">
                    <td><strong>{{ permission.permissionCode }}</strong></td>
                    <td>{{ permission.permissionName }}</td>
                    <td><em :class="['status-badge', statusTone(permission.status || 'ENABLED')]">{{ statusLabel(permission.status || 'ENABLED') }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="ghost-action compact-action" type="button" @click="openPermissionDetail(permission)">详情</button>
                        <button class="secondary-action compact-action" type="button" :disabled="!canSavePermission" @click="openPermissionEdit(permission)">编辑</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(roleManagement?.permissions.length || 0, listPage.permissions) }}-{{ pageEnd(roleManagement?.permissions.length || 0, listPage.permissions) }} / {{ roleManagement?.permissions.length || 0 }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.permissions <= 1" @click="setPage('permissions', listPage.permissions - 1, roleManagement?.permissions.length || 0)">上一页</button>
                <strong>{{ listPage.permissions }} / {{ totalPages(roleManagement?.permissions.length || 0) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.permissions >= totalPages(roleManagement?.permissions.length || 0)" @click="setPage('permissions', listPage.permissions + 1, roleManagement?.permissions.length || 0)">下一页</button>
              </div>
            </div>
          </section>
        </section>

        <section v-else-if="activeMenu === 'platform.menus'" class="management-page">
          <div class="page-toolbar">
            <div>
              <h2>菜单管理</h2>
              <p>以菜单树展示导航层级，支持节点新增、编辑和详情。</p>
            </div>
            <button data-test="open-menu-create" class="primary-action" type="button" :disabled="!canSaveMenu" @click="openMenuCreate()">新增菜单</button>
          </div>
          <p v-if="rbacError" class="message error">{{ rbacError }}</p>
          <p v-if="rbacMessage" class="message success">{{ rbacMessage }}</p>
          <section class="panel list-panel">
            <div class="table-scroll">
              <table class="data-table menu-tree-table">
                <thead>
                  <tr>
                    <th>菜单树</th>
                    <th>上级</th>
                    <th>路由</th>
                    <th>组件</th>
                    <th>范围</th>
                    <th>状态</th>
                    <th class="operation-column">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="menu in pagedMenuTreeRows" :key="menu.menuId">
                    <td>
                      <div class="tree-cell" :style="{ '--level': menu.level }">
                        <span class="tree-branch" aria-hidden="true"></span>
                        <div>
                          <strong>{{ menu.menuName }}</strong><br />
                          <small>{{ menu.menuCode }} · {{ menu.childCount }} 个子节点</small>
                        </div>
                      </div>
                    </td>
                    <td>{{ menuParentName(menu.parentCode) }}</td>
                    <td>{{ menu.routePath || '-' }}</td>
                    <td>{{ menu.componentKey || '-' }}</td>
                    <td>{{ menu.platformScope }}</td>
                    <td><em :class="['status-badge', statusTone(menu.status || 'ENABLED')]">{{ statusLabel(menu.status || 'ENABLED') }}</em></td>
                    <td class="operation-column">
                      <div class="row-actions">
                        <button class="secondary-action compact-action" type="button" :disabled="!canSaveMenu" @click="openMenuCreate(menu.menuCode)">新增子菜单</button>
                        <button class="ghost-action compact-action" type="button" @click="openMenuDetail(menu)">详情</button>
                        <button class="secondary-action compact-action" type="button" :disabled="!canSaveMenu" @click="openMenuEdit(menu)">编辑</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{ pageStart(menuTreeRows.length, listPage.menus) }}-{{ pageEnd(menuTreeRows.length, listPage.menus) }} / {{ menuTreeRows.length }}</span>
              <div>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.menus <= 1" @click="setPage('menus', listPage.menus - 1, menuTreeRows.length)">上一页</button>
                <strong>{{ listPage.menus }} / {{ totalPages(menuTreeRows.length) }}</strong>
                <button class="ghost-action compact-action" type="button" :disabled="listPage.menus >= totalPages(menuTreeRows.length)" @click="setPage('menus', listPage.menus + 1, menuTreeRows.length)">下一页</button>
              </div>
            </div>
          </section>
        </section>

        <div v-if="isDialogOpen" class="modal-backdrop" @click.self="closeDialog">
          <section class="modal-panel" role="dialog" aria-modal="true">
            <header class="modal-header">
              <div>
                <p class="eyebrow">{{ dialog.mode === 'detail' ? '详情' : dialog.mode === 'edit' ? '编辑' : dialog.mode === 'access' ? '权限配置' : '新增' }}</p>
                <h2>{{ dialog.title }}</h2>
              </div>
              <button data-test="modal-close" class="ghost-action compact-action" type="button" @click="closeDialog">关闭</button>
            </header>

            <form v-if="dialog.name === 'tenant-model'" class="provider-form provider-config-form modal-form" @submit.prevent="saveProvider">
              <div class="form-grid modal-grid">
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
              </div>
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
              <p class="hint">编辑已有供应商时后端不会回传密钥明文，需要重新输入 API Key。</p>
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">取消</button>
                <button class="primary-action" type="submit" :disabled="providerSaving">
                  <span class="button-icon save-icon" aria-hidden="true"></span>
                  {{ providerSaving ? '保存中' : '保存配置' }}
                </button>
              </footer>
            </form>

            <div v-else-if="dialog.name === 'tenant-model-detail'" class="modal-body">
              <div class="detail-grid">
                <div><span>供应商</span><strong>{{ selectedProvider?.providerName }}</strong></div>
                <div><span>供应商类型</span><strong>{{ selectedProvider?.providerType }}</strong></div>
                <div><span>Base URL</span><strong>{{ selectedProvider?.baseUrl }}</strong></div>
                <div><span>API Key</span><strong>****{{ selectedProvider?.apiKeyLast4 || '-' }}</strong></div>
                <div><span>模型</span><strong>{{ selectedProviderModel?.displayName || selectedProviderModel?.modelCode }}</strong></div>
                <div><span>模型编码</span><strong>{{ selectedProviderModel?.modelCode }}</strong></div>
                <div><span>能力</span><strong>{{ capabilityLabel(selectedProviderModel?.capabilityType || '') }}</strong></div>
                <div><span>状态</span><strong>{{ statusLabel(selectedProviderModel?.status || '') }}</strong></div>
              </div>
              <footer class="modal-footer">
                <button class="primary-action" type="button" @click="closeDialog">完成</button>
              </footer>
            </div>

            <form v-else-if="dialog.name === 'platform-provider'" class="provider-form platform-provider-form modal-form" @submit.prevent="savePlatformProviderType">
              <div class="form-grid modal-grid">
                <label>
                  供应商类型
                  <select v-model="platformProviderForm.providerType" name="platformProviderType" :disabled="dialog.mode === 'detail'">
                    <option v-for="providerType in providerTypeValues" :key="providerType" :value="providerType">
                      {{ providerType }}
                    </option>
                  </select>
                </label>
                <label>
                  显示名称
                  <input v-model="platformProviderForm.displayName" name="platformProviderDisplayName" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  默认 Base URL
                  <input v-model="platformProviderForm.defaultBaseUrl" name="platformProviderBaseUrl" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  状态
                  <select v-model="platformProviderForm.status" name="platformProviderStatus" :disabled="dialog.mode === 'detail'">
                    <option value="ENABLED">已启用</option>
                    <option value="DISABLED">已禁用</option>
                  </select>
                </label>
                <label>
                  排序
                  <input v-model.number="platformProviderForm.sortOrder" name="platformProviderSortOrder" type="number" min="0" :readonly="dialog.mode === 'detail'" />
                </label>
              </div>
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="platformProviderSaving">
                  {{ platformProviderSaving ? '保存中' : '保存供应商类型' }}
                </button>
              </footer>
            </form>

            <form v-else-if="dialog.name === 'platform-model'" class="provider-form platform-model-form modal-form" @submit.prevent="savePlatformModelOption">
              <div class="form-grid modal-grid">
                <label>
                  供应商类型
                  <select v-model="platformModelForm.providerTypeId" name="platformModelProviderTypeId" :disabled="dialog.mode === 'detail'">
                    <option v-for="option in platformProviderTypes" :key="option.providerTypeId" :value="option.providerTypeId">
                      {{ option.displayName }} · {{ option.providerType }}
                    </option>
                  </select>
                </label>
                <label>
                  模型编码
                  <input v-model="platformModelForm.modelCode" name="platformModelCode" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  显示名称
                  <input v-model="platformModelForm.displayName" name="platformModelDisplayName" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  能力类型
                  <select v-model="platformModelForm.capabilityType" name="platformModelCapabilityType" :disabled="dialog.mode === 'detail'">
                    <option v-for="capability in capabilityValues" :key="capability" :value="capability">
                      {{ capabilityLabel(capability) }}
                    </option>
                  </select>
                </label>
              </div>
              <div class="model-fields">
                <label>
                  上下文窗口
                  <input v-model="platformModelForm.contextWindow" name="platformModelContextWindow" type="number" min="1" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  最大输出 token
                  <input v-model="platformModelForm.maxOutputTokens" name="platformModelMaxOutputTokens" type="number" min="1" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  向量维度
                  <input v-model="platformModelForm.embeddingDimensions" name="platformModelEmbeddingDimensions" type="number" min="1" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  Temperature
                  <input v-model="platformModelForm.temperature" name="platformModelTemperature" type="number" min="0" max="2" step="0.01" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  Top P
                  <input v-model="platformModelForm.topP" name="platformModelTopP" type="number" min="0" max="1" step="0.01" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  Top K
                  <input v-model="platformModelForm.topK" name="platformModelTopK" type="number" min="1" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  频率惩罚
                  <input v-model="platformModelForm.frequencyPenalty" name="platformModelFrequencyPenalty" type="number" min="-2" max="2" step="0.01" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  存在惩罚
                  <input v-model="platformModelForm.presencePenalty" name="platformModelPresencePenalty" type="number" min="-2" max="2" step="0.01" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  推理强度
                  <input v-model="platformModelForm.reasoningEffort" name="platformModelReasoningEffort" :readonly="dialog.mode === 'detail'" />
                </label>
                <label>
                  状态
                  <select v-model="platformModelForm.status" name="platformModelStatus" :disabled="dialog.mode === 'detail'">
                    <option value="ENABLED">已启用</option>
                    <option value="DISABLED">已禁用</option>
                  </select>
                </label>
                <label>
                  排序
                  <input v-model.number="platformModelForm.sortOrder" name="platformModelSortOrder" type="number" min="0" :readonly="dialog.mode === 'detail'" />
                </label>
              </div>
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="platformModelSaving || !platformModelForm.providerTypeId">
                  {{ platformModelSaving ? '保存中' : '保存模型选项' }}
                </button>
              </footer>
            </form>

            <form v-else-if="dialog.name === 'tenant'" class="tenant-form modal-form" @submit.prevent="saveTenant">
              <div v-if="dialog.mode === 'detail'" class="detail-grid">
                <div><span>租户编码</span><strong>{{ selectedTenant?.tenantCode }}</strong></div>
                <div><span>租户名称</span><strong>{{ selectedTenant?.tenantName }}</strong></div>
                <div><span>状态</span><strong>{{ statusLabel(selectedTenant?.status || '') }}</strong></div>
              </div>
              <div v-else class="form-grid modal-grid">
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
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="tenantSaving || !canSaveTenant">
                  {{ tenantSaving ? '保存中' : '保存租户' }}
                </button>
              </footer>
            </form>

            <form v-else-if="dialog.name === 'role'" class="role-form modal-form" @submit.prevent="saveRole">
              <div v-if="dialog.mode === 'detail'" class="detail-grid">
                <div><span>角色编码</span><strong>{{ selectedRole?.roleCode }}</strong></div>
                <div><span>角色名称</span><strong>{{ selectedRole?.roleName }}</strong></div>
                <div><span>权限点</span><strong>{{ selectedRole?.permissions.length || 0 }}</strong></div>
                <div><span>菜单</span><strong>{{ selectedRole?.menus.length || 0 }}</strong></div>
              </div>
              <div v-else class="form-grid modal-grid">
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
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="roleSaving || !canSaveRole">
                  {{ roleSaving ? '保存中' : '保存角色' }}
                </button>
              </footer>
            </form>

            <div v-else-if="dialog.name === 'role-access' && selectedRole" class="modal-body access-modal">
              <div class="access-summary">
                <strong>{{ selectedRole.roleName }}</strong>
                <span>{{ selectedRole.roleCode }}</span>
              </div>
              <div class="access-layout">
                <section class="check-panel">
                  <h3>权限点</h3>
                  <div v-for="group in permissionGroups" :key="group.groupCode" class="check-group">
                    <p class="check-group-title">{{ group.title }}</p>
                    <label v-for="permission in group.permissions" :key="permission.permissionCode" class="check-row">
                      <input
                        v-model="roleBinding(selectedRole.roleId).permissionCodes"
                        :data-test="bindingTestId('permission', selectedRole.roleId, permission.permissionCode)"
                        type="checkbox"
                        :value="permission.permissionCode"
                      />
                      <span>{{ permission.permissionName }} · {{ permission.permissionCode }}</span>
                    </label>
                  </div>
                </section>
                <section class="check-panel">
                  <h3>菜单树</h3>
                  <label v-for="menu in menuTreeRows" :key="menu.menuCode" class="check-row tree-check-row" :style="{ paddingLeft: `${menu.level * 16}px` }">
                    <input
                      v-model="roleBinding(selectedRole.roleId).menuCodes"
                      :data-test="bindingTestId('menu', selectedRole.roleId, menu.menuCode)"
                      type="checkbox"
                      :value="menu.menuCode"
                    />
                    <span>{{ menu.menuName }} · {{ menu.menuCode }}</span>
                  </label>
                </section>
              </div>
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">取消</button>
                <button
                  :data-test="`save-role-${selectedRole.roleId}`"
                  class="primary-action"
                  type="button"
                  :disabled="rbacSavingRoleId === selectedRole.roleId"
                  @click="saveRoleBindingsFromDialog"
                >
                  {{ rbacSavingRoleId === selectedRole.roleId ? '保存中' : '保存角色配置' }}
                </button>
              </footer>
            </div>

            <form v-else-if="dialog.name === 'permission'" class="permission-form modal-form" @submit.prevent="savePermission">
              <div v-if="dialog.mode === 'detail'" class="detail-grid">
                <div><span>权限编码</span><strong>{{ selectedPermission?.permissionCode }}</strong></div>
                <div><span>权限名称</span><strong>{{ selectedPermission?.permissionName }}</strong></div>
                <div><span>状态</span><strong>{{ statusLabel(selectedPermission?.status || 'ENABLED') }}</strong></div>
              </div>
              <div v-else class="form-grid modal-grid">
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
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="permissionSaving || !canSavePermission">
                  {{ permissionSaving ? '保存中' : '保存权限点' }}
                </button>
              </footer>
            </form>

            <form v-else-if="dialog.name === 'menu'" class="menu-form modal-form" @submit.prevent="saveMenu">
              <div v-if="dialog.mode === 'detail'" class="detail-grid">
                <div><span>菜单编码</span><strong>{{ selectedMenu?.menuCode }}</strong></div>
                <div><span>菜单名称</span><strong>{{ selectedMenu?.menuName }}</strong></div>
                <div><span>上级菜单</span><strong>{{ menuParentName(selectedMenu?.parentCode || null) }}</strong></div>
                <div><span>路由</span><strong>{{ selectedMenu?.routePath || '-' }}</strong></div>
                <div><span>组件</span><strong>{{ selectedMenu?.componentKey || '-' }}</strong></div>
                <div><span>范围</span><strong>{{ selectedMenu?.platformScope }}</strong></div>
                <div><span>状态</span><strong>{{ statusLabel(selectedMenu?.status || 'ENABLED') }}</strong></div>
              </div>
              <div v-else class="form-grid modal-grid">
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
              <footer class="modal-footer">
                <button class="ghost-action" type="button" @click="closeDialog">{{ dialog.mode === 'detail' ? '关闭' : '取消' }}</button>
                <button v-if="dialog.mode !== 'detail'" class="primary-action" type="submit" :disabled="menuSaving || !canSaveMenu">
                  {{ menuSaving ? '保存中' : '保存菜单' }}
                </button>
              </footer>
            </form>
          </section>
        </div>
      </section>
    </section>
  </main>
</template>
