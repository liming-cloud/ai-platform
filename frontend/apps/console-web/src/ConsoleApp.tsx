import { Tab, TabList } from "@fluentui/react-components";
import { ProductShell, StatePanel, type NavigationItem } from "@foundation/design-system";
import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ControlPlaneClient } from "./api/control-plane-client";
import { KnowledgePublicationFlow } from "./features/knowledge-publication/KnowledgePublicationFlow";
import { BrowserSessionGate } from "./session/BrowserSessionGate";
import { isConsoleLoginPath } from "./session/login-workspace-choice";
import { defaultNavigationTarget, visibleNavigation } from "./session/navigation-capability";
import { resolvePostAuthenticationDestination } from "./session/post-authentication-navigation";
import {
  defaultConsoleRoute,
  formatConsoleRoute,
  isKnowledgeDataTask,
  knowledgeDataTaskRoute,
  parseConsoleRoute,
  resolveAuthorizedConsoleRoute,
  type ConsoleRoute,
} from "./session/console-route";

const GovernedFileDataWorkspace = lazy(async () => {
  const module = await import("./features/governed-data/GovernedFileDataWorkspace");
  return { default: module.GovernedFileDataWorkspace };
});

const StructuredDatasetRegistrationWorkspace = lazy(async () => {
  const module = await import(
    "./features/governed-data/StructuredDatasetRegistrationWorkspace"
  );
  return { default: module.StructuredDatasetRegistrationWorkspace };
});

const ObjectStorageDataWorkspace = lazy(async () => {
  const module = await import(
    "./features/governed-data/ObjectStorageDataWorkspace"
  );
  return { default: module.ObjectStorageDataWorkspace };
});

const KnowledgeSpaceWorkspace = lazy(async () => {
  const module = await import(
    "./features/knowledge-space/KnowledgeSpaceWorkspace"
  );
  return { default: module.KnowledgeSpaceWorkspace };
});

const QueryGovernanceWorkspace = lazy(async () => {
  const module = await import(
    "./features/query-governance/QueryGovernanceWorkspace"
  );
  return { default: module.QueryGovernanceWorkspace };
});

const EvaluationRunWorkspace = lazy(async () => {
  const module = await import("./features/evaluation/EvaluationRunWorkspace");
  return { default: module.EvaluationRunWorkspace };
});

const ModelConfigurationWorkspace = lazy(async () => {
  const module = await import(
    "./features/model-configuration/ModelConfigurationWorkspace"
  );
  return { default: module.ModelConfigurationWorkspace };
});

const ModelProviderWorkspace = lazy(async () => {
  const module = await import(
    "./features/model-configuration/ModelProviderWorkspace"
  );
  return { default: module.ModelProviderWorkspace };
});

const ModelDeploymentWorkspace = lazy(async () => {
  const module = await import(
    "./features/model-configuration/ModelDeploymentWorkspace"
  );
  return { default: module.ModelDeploymentWorkspace };
});

const ConsoleQueryWorkspace = lazy(async () => {
  const module = await import("./features/trusted-query/ConsoleQueryWorkspace");
  return { default: module.ConsoleQueryWorkspace };
});

const PlatformTenantLifecycleWorkspace = lazy(async () => {
  const module = await import("./features/platform-governance/PlatformTenantLifecycleWorkspace");
  return { default: module.PlatformTenantLifecycleWorkspace };
});

const PlatformAccountDirectoryWorkspace = lazy(async () => {
  const module = await import("./features/platform-governance/PlatformAccountDirectoryWorkspace");
  return { default: module.PlatformAccountDirectoryWorkspace };
});

const PlatformModelSupplyWorkspace = lazy(async () => {
  const module = await import("./features/platform-governance/PlatformModelSupplyWorkspace");
  return { default: module.PlatformModelSupplyWorkspace };
});

const PlatformModelDeploymentWorkspace = lazy(async () => {
  const module = await import("./features/platform-governance/PlatformModelDeploymentWorkspace");
  return { default: module.PlatformModelDeploymentWorkspace };
});

const TenantMemberWorkspace = lazy(async () => {
  const module = await import("./features/access/TenantMemberWorkspace");
  return { default: module.TenantMemberWorkspace };
});

const RoleDirectoryWorkspace = lazy(async () => {
  const module = await import("./features/access/RoleDirectoryWorkspace");
  return { default: module.RoleDirectoryWorkspace };
});

const OperationsOverviewWorkspace = lazy(async () => {
  const module = await import("./features/operations/OperationsOverviewWorkspace");
  return { default: module.OperationsOverviewWorkspace };
});

export function ConsoleApp() {
  const client = useMemo(() => new ControlPlaneClient(), []);

  return (
    <BrowserSessionGate
      client={client}
      onAuthenticated={(capability) => {
        const destination = resolvePostAuthenticationDestination(
          window.location.search,
          capability.workspaceCategories.includes("TENANT"),
        );
        if (destination !== undefined) {
          window.location.replace(destination);
        }
      }}
    >
      {(session) => (
        <AuthenticatedConsoleApp
          client={client}
          canUseTenantWorkspace={session.capability.workspaceCategories.includes("TENANT")}
          canUsePlatformGovernance={session.capability.workspaceCategories.includes("PLATFORM_TENANT_GOVERNANCE")}
          capabilityCodes={session.capability.capabilityCodes}
          displayRoles={session.capability.displayRoles}
          onLogout={session.logout}
        />
      )}
    </BrowserSessionGate>
  );
}

function AuthenticatedConsoleApp({
  client,
  canUseTenantWorkspace,
  canUsePlatformGovernance,
  capabilityCodes,
  displayRoles,
  onLogout,
}: {
  client: ControlPlaneClient;
  canUseTenantWorkspace: boolean;
  canUsePlatformGovernance: boolean;
  capabilityCodes: string[];
  displayRoles: string[];
  onLogout: () => Promise<void>;
}) {
  const navigation = visibleNavigation({
    canUseTenantWorkspace,
    canUsePlatformGovernance,
    capabilityCodes,
  });
  const navigationIds = navigationTargets(navigation);
  const defaultNavigationId = defaultNavigationTarget(navigation);
  const [sessionRevision, setSessionRevision] = useState(0);
  const [location, setLocation] = useState(readBrowserLocation);
  const parsedRoute = parseConsoleRoute(location.pathname, location.search);
  const requestedRoute = parsedRoute ?? ((location.pathname === "/" || isConsoleLoginPath(location.pathname)) && defaultNavigationId !== undefined
    ? defaultConsoleRoute(defaultNavigationId)
    : undefined);
  const resolvedRoute = resolveAuthorizedConsoleRoute(
    requestedRoute,
    navigationIds,
  );
  const accountLabel = browserSessionRoleLabel(displayRoles);

  useEffect(() => {
    const refreshLocation = () => setLocation(readBrowserLocation());
    window.addEventListener("popstate", refreshLocation);
    return () => window.removeEventListener("popstate", refreshLocation);
  }, []);

  const navigate = useCallback((route: ConsoleRoute, replace = false) => {
    const destination = formatConsoleRoute(route);
    window.history[replace ? "replaceState" : "pushState"](null, "", destination);
    setLocation(readBrowserLocation());
  }, [setLocation]);

  useEffect(() => {
    if ((location.pathname === "/" || isConsoleLoginPath(location.pathname)) && resolvedRoute.kind === "route") {
      window.history.replaceState(null, "", formatConsoleRoute(resolvedRoute.route));
    }
  }, [location.pathname, resolvedRoute]);

  function selectNavigation(navigationId: string) {
    if (!navigationIds.includes(navigationId as ConsoleRoute["navigationId"])) {
      return;
    }
    navigate(defaultConsoleRoute(navigationId as ConsoleRoute["navigationId"]));
  }

  function sessionChanged() {
    setSessionRevision((current) => current + 1);
  }

  if (navigation.length === 0) {
    return (
      <ProductShell
        productName="可信 RAG"
        pageTitle="当前没有可用工作区"
        pageSummary="系统只显示与当前会话能力相符的入口，不会因此展示租户正文或管理入口。"
        navigation={[]}
        activeNavigationId=""
        accountLabel={accountLabel}
        onLogout={() => void onLogout()}
      >
        <StatePanel
          kind="denied"
          title="当前会话没有可用工作区"
          description="请联系管理员分配与实际业务任务对应的角色和权限。"
          actionLabel="退出登录"
          onAction={() => void onLogout()}
        />
      </ProductShell>
    );
  }

  if (resolvedRoute.kind !== "route") {
    const denied = resolvedRoute.kind === "denied";
    return (
      <ProductShell
        productName="可信 RAG"
        pageTitle={denied ? "当前会话无权访问此工作区" : "未找到工作区"}
        pageSummary={denied
          ? "该 URL 不会被静默替换为其他工作区，请使用当前会话被授权的入口。"
          : "请从已授权的导航入口继续操作。"}
        navigation={navigation}
        activeNavigationId=""
        onNavigationChange={selectNavigation}
        accountLabel={accountLabel}
        onLogout={() => void onLogout()}
      >
        <StatePanel
          kind={denied ? "denied" : "error"}
          title={denied ? "当前会话无权访问此工作区" : "未找到工作区"}
          description={denied
            ? "浏览器没有加载目标工作区内容；请联系管理员分配相应角色和权限。"
            : "该地址不是可信知识平台提供的工作区路径。"}
        />
      </ProductShell>
    );
  }

      const route = resolvedRoute.route;
      const operationsActive = route.navigationId === "operations";
  const dataActive = route.navigationId === "data";
  const accessActive = route.navigationId === "access";
  const modelsActive = route.navigationId === "models";
  const governanceActive = route.navigationId === "governance";
  const queryActive = route.navigationId === "query";
      const platformOperationsActive = route.navigationId === "platform-operations";
  const platformActive = route.navigationId === "platform";
  const platformAccountsActive = route.navigationId === "platform-accounts";
  const platformModelSupplyActive = route.navigationId === "platform-model-supply";
  const dataPage = dataActive ? dataPageContent(route) : undefined;

  return (
    <ProductShell
      productName="可信 RAG"
      pageTitle={
        operationsActive ? "运营总览" : platformOperationsActive ? "平台运营总览" : accessActive ? route.task === "roles" ? "租户角色" : "成员与权限" : platformActive ? route.task === "create" ? "建立新租户" : "平台租户" : platformAccountsActive ? route.task === "roles" ? "角色目录" : "人员账户目录" : platformModelSupplyActive ? "平台模型供应链" : queryActive ? "从已授权资料中获得答案" : governanceActive ? route.task === "query" ? "查询用量与审计" : "受限评测运行" : modelsActive ? route.task === "configuration" ? "受控模型配置" : route.task === "providers" ? "供应商连接与模型发现" : "模型部署与能力验证" : dataPage?.title ?? "可信知识平台"
      }
      pageSummary={
        operationsActive
          ? "查看当前租户的成员、知识、模型、助手、评测和查询健康状态。"
          : platformOperationsActive
          ? "查看全平台脱敏运营指标和按租户分组的安全摘要，不进入租户内容。"
          : queryActive
          ? "答案必须附带可核验来源。没有足够证据或没有访问权限时，系统会明确拒绝回答。"
          : accessActive
          ? route.task === "roles" ? "查看当前租户的固定角色、能力范围和安全边界。" : "成员目录按页显示；停用成员前必须二次确认。"
          : platformActive
          ? route.task === "create"
            ? "填写租户信息、资源配额与管理员账号，一次创建即可使用。"
            : route.task === "detail" ? "核对租户信息与资源配额，获取管理员登录入口。"
              : "建立、激活和暂停租户，不展示租户正文、成员详情或平台操作者信息。"
          : platformAccountsActive
          ? route.task === "roles" ? "查看平台固定角色的任务范围、能力和信息边界。" : "账户目录按页显示，不展示凭据、会话或权限快照。"
          : platformModelSupplyActive
          ? "平台连接与后续部署独立于租户范围；Endpoint 和凭据始终受到保护。"
          : governanceActive && route.task === "query"
          ? "核对当前租户的查询配额、计量结果和脱敏运行记录。"
          : governanceActive
          ? "选择已有数据集和助手运行评测，只展示指标和质量结论。"
          : modelsActive && route.task === "configuration"
          ? "从平台发布目录创建、轮换和启用模型配置；API Key 不会再次显示。"
          : modelsActive && route.task === "providers"
          ? "核对供应商连接状态和可用模型；Endpoint 和凭据始终受到保护。"
          : modelsActive
          ? "选择当前有效发现候选建立草稿部署，在独立任务中执行真实能力探测和高风险发布确认。"
          : dataPage?.summary ?? ""
      }
      navigation={navigation}
      activeNavigationId={route.navigationId}
      onNavigationChange={selectNavigation}
      accountLabel={accountLabel}
      onLogout={() => void onLogout()}
    >
      <section hidden={!operationsActive && !platformOperationsActive}>
        {operationsActive || platformOperationsActive ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载运营总览" description="正在汇总安全运营指标。" />}>
            <OperationsOverviewWorkspace client={client} route={route as Extract<ConsoleRoute, { task: "overview" }>} onRouteChange={navigate} />
          </Suspense>
        ) : null}
      </section>

      <section hidden={!dataActive}>
        <TaskNavigation
          ariaLabel="知识库任务切换"
          activeValue={route.navigationId === "data" ? route.task : "knowledge-bases"}
          items={[
            { value: "knowledge-bases", label: "知识库目录" },
            { value: "governed-file", label: "文件来源" },
            { value: "structured-dataset", label: "结构化数据集登记" },
            { value: "object-storage", label: "对象存储范围" },
            { value: "first-publication", label: "快速入门" },
          ]}
          onSelect={(task) => {
            if (route.navigationId === "data" && isKnowledgeDataTask(task)) {
              navigate(knowledgeDataTaskRoute(route, task));
            }
          }}
        />
        {route.navigationId === "data" && route.task === "knowledge-bases" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载知识库" description="正在读取当前租户的知识库目录。" />}>
            <KnowledgeSpaceWorkspace client={client} route={route} onRouteChange={navigate} />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "governed-file" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据任务" description="正在准备受治理文件来源流程。" />}>
            <GovernedFileDataWorkspace
              client={client}
              route={route}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
              onRouteChange={navigate}
            />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "structured-dataset" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据集登记任务" description="正在准备私有 CSV 登记流程。" />}>
            <StructuredDatasetRegistrationWorkspace
              client={client}
              route={route}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
              onRouteChange={navigate}
            />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "object-storage" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载对象存储任务" description="正在准备平台签发范围登记流程。" />}>
            <ObjectStorageDataWorkspace
              client={client}
              route={route}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
              onRouteChange={navigate}
            />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "first-publication" ? (
          <KnowledgePublicationFlow
            client={client}
            route={route}
            sessionRevision={sessionRevision}
            onRouteChange={navigate}
          />
        ) : null}
      </section>
      <section hidden={!accessActive}>
        {accessActive ? <>
          <TaskNavigation
            ariaLabel="访问治理任务切换"
            activeValue={route.task}
            items={[{ value: "members", label: "成员目录" }, { value: "roles", label: "角色目录" }]}
            onSelect={(task) => navigate(task === "roles"
              ? { navigationId: "access", task: "roles" }
              : { navigationId: "access", task: "members", page: 1 })}
          />
          <Suspense fallback={<StatePanel kind="loading" title="正在加载成员与权限" description="正在准备受限访问治理入口。" />}>
            {route.navigationId === "access" && route.task === "members"
              ? <TenantMemberWorkspace client={client} route={route} sessionRevision={sessionRevision} onRouteChange={navigate} />
              : <RoleDirectoryWorkspace scope="TENANT" onManageMembers={() => navigate({ navigationId: "access", task: "members", page: 1 })} />}
          </Suspense>
        </> : null}
      </section>
      <section hidden={!modelsActive}>
        {modelsActive ? (
          <Suspense
            fallback={(
              <StatePanel
                kind="loading"
                title="正在加载模型配置工作区"
                description="正在准备受控模型目录和配置状态。"
              />
            )}
          >
            {route.navigationId === "models" && route.task === "configuration" ? (
              <ModelConfigurationWorkspace
                client={client}
                route={route}
                sessionRevision={sessionRevision}
                onSessionChanged={sessionChanged}
                onRouteChange={navigate}
              />
            ) : route.navigationId === "models" && route.task === "providers" ? (
              <ModelProviderWorkspace
                client={client}
                route={route}
                sessionRevision={sessionRevision}
                onRouteChange={navigate}
              />
            ) : route.navigationId === "models" && route.task === "deployments" ? (
              <ModelDeploymentWorkspace
                client={client}
                route={route}
                sessionRevision={sessionRevision}
                onRouteChange={navigate}
              />
            ) : null}
          </Suspense>
        ) : null}
      </section>
      <section hidden={!queryActive}>
        {queryActive ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载问答工作区" description="正在准备可信问答流程。" />}>
            <ConsoleQueryWorkspace
              assistantPage={route.assistantPage}
              onAssistantPageChange={(assistantPage) => navigate({ ...route, assistantPage })}
              onSessionExpired={() => void onLogout()}
            />
          </Suspense>
        ) : null}
      </section>
      <section hidden={!governanceActive}>
        {governanceActive ? (
          <>
            <TaskNavigation
              ariaLabel="治理任务切换"
              activeValue={route.navigationId === "governance" ? route.task : "query"}
              items={[
                { value: "query", label: "查询用量与审计" },
                { value: "evaluation", label: "受限评测运行" },
              ]}
              onSelect={(task) => {
                if (task === "query") {
                  navigate({ navigationId: "governance", task, auditPage: 1 });
                } else if (task === "evaluation") {
                  navigate({ navigationId: "governance", task });
                }
              }}
            />
            {route.navigationId === "governance" && route.task === "query" ? (
              <Suspense fallback={<StatePanel kind="loading" title="正在加载治理工作区" description="正在准备查询用量和脱敏审计视图。" />}>
                <QueryGovernanceWorkspace
                  client={client}
                  route={route}
                  sessionRevision={sessionRevision}
                  onSessionChanged={sessionChanged}
                  onRouteChange={navigate}
                />
              </Suspense>
            ) : (
              <Suspense fallback={<StatePanel kind="loading" title="正在加载评测任务" description="正在准备受限评测运行入口。" />}>
                <EvaluationRunWorkspace client={client} sessionRevision={sessionRevision} onSessionChanged={sessionChanged} />
              </Suspense>
            )}
          </>
        ) : null}
      </section>
      <section hidden={!platformActive}>
        {platformActive ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载平台租户工作区" description="正在准备受限生命周期任务。" />}>
            <PlatformTenantLifecycleWorkspace client={client} route={route} onRouteChange={navigate} />
          </Suspense>
        ) : null}
      </section>
      <section hidden={!platformAccountsActive}>
        {platformAccountsActive ? (
          <>
            <TaskNavigation
              ariaLabel="平台访问治理任务切换"
              activeValue={route.task}
              items={[{ value: "accounts", label: "账户目录" }, { value: "roles", label: "角色目录" }]}
              onSelect={(task) => navigate(task === "roles"
                ? { navigationId: "platform-accounts", task: "roles" }
                : { navigationId: "platform-accounts", task: "accounts", page: 1 })}
            />
            <Suspense fallback={<StatePanel kind="loading" title="正在加载平台访问治理" description="正在准备账户与角色目录。" />}>
              {route.task === "accounts"
                ? <PlatformAccountDirectoryWorkspace client={client} route={route} onRouteChange={navigate} />
                : <RoleDirectoryWorkspace scope="PLATFORM" onManageMembers={() => navigate({ navigationId: "platform-accounts", task: "accounts", page: 1 })} />}
            </Suspense>
          </>
        ) : null}
      </section>
      <section hidden={!platformModelSupplyActive}>
        {platformModelSupplyActive ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载平台模型供应链" description="正在准备平台范围连接目录。" />}>
            {route.task === "connections" ? <PlatformModelSupplyWorkspace client={client} route={route} sessionRevision={sessionRevision} onRouteChange={navigate} /> : <PlatformModelDeploymentWorkspace client={client} route={route} sessionRevision={sessionRevision} onRouteChange={navigate} />}
          </Suspense>
        ) : null}
      </section>
    </ProductShell>
  );
}

function TaskNavigation({ ariaLabel, activeValue, items, onSelect }: {
  ariaLabel: string;
  activeValue: string;
  items: readonly { value: string; label: string }[];
  onSelect: (value: string) => void;
}) {
  return (
    <TabList
      className="task-navigation"
      aria-label={ariaLabel}
      selectedValue={activeValue}
      onTabSelect={(_, data) => onSelect(String(data.value))}
    >
      {items.map((item) => (
        <Tab key={item.value} value={item.value}>{item.label}</Tab>
      ))}
    </TabList>
  );
}

const SESSION_ROLE_LABELS: Record<string, string> = {
  PLATFORM_ADMINISTRATOR: "平台管理员",
  TENANT_ADMINISTRATOR: "租户管理员",
  TENANT_USER: "租户用户",
};

function browserSessionRoleLabel(displayRoles: readonly string[]) {
  const labels = displayRoles
    .map((role) => SESSION_ROLE_LABELS[role])
    .filter((label): label is string => label !== undefined);
  return labels.length > 0 ? labels.join("、") : "当前账户";
}

function navigationTargets(navigation: readonly NavigationItem[]): ConsoleRoute["navigationId"][] {
  return navigation.flatMap((item) =>
    (item.children?.map((child) => child.id) ?? [item.id]) as ConsoleRoute["navigationId"][]);
}

function readBrowserLocation() {
  return {
    pathname: window.location.pathname,
    search: window.location.search,
  };
}

function dataPageContent(route: Extract<ConsoleRoute, { navigationId: "data" }>) {
  switch (route.task) {
    case "knowledge-bases":
      return {
        title: "知识库",
        summary: "每个知识库只属于当前租户；从目录建立知识边界，再登记来源、完成质量检查并发布索引。",
      };
    case "governed-file":
      return {
        title: "受治理文件来源",
        summary: "在当前租户知识库中登记并探测受控 UTF-8 文件，不展示对象位置或正文。",
      };
    case "structured-dataset":
      return {
        title: "结构化数据集登记",
        summary: "登记平台私有 UTF-8 CSV 与冻结字段映射，不展示对象位置、正文或敏感字段。",
      };
    case "object-storage":
      return {
        title: "对象存储范围登记",
        summary: "从当前知识库已获授权的对象范围中选择并登记数据来源。",
      };
    case "first-publication":
      return {
        title: "知识发布快速入门",
        summary: "选择当前租户已有知识库，完成文本上传、发布核验和检索索引；流程内不会隐式创建知识库。",
      };
  }
}
