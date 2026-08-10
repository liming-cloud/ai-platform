import {
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { ProductShell, StatePanel } from "@foundation/design-system";
import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ControlPlaneClient } from "./api/control-plane-client";
import { KnowledgePublicationFlow } from "./features/knowledge-publication/KnowledgePublicationFlow";
import { BrowserSessionGate } from "./session/BrowserSessionGate";
import { visibleNavigation } from "./session/navigation-capability";
import { resolvePostAuthenticationDestination } from "./session/post-authentication-navigation";
import {
  defaultConsoleRoute,
  formatConsoleRoute,
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

const TenantMemberWorkspace = lazy(async () => {
  const module = await import("./features/access/TenantMemberWorkspace");
  return { default: module.TenantMemberWorkspace };
});

const TenantCustomRoleWorkspace = lazy(async () => {
  const module = await import("./features/access/TenantCustomRoleWorkspace");
  return { default: module.TenantCustomRoleWorkspace };
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
  onLogout,
}: {
  client: ControlPlaneClient;
  canUseTenantWorkspace: boolean;
  canUsePlatformGovernance: boolean;
  capabilityCodes: string[];
  onLogout: () => Promise<void>;
}) {
  const navigation = visibleNavigation({
    canUseTenantWorkspace,
    canUsePlatformGovernance,
    capabilityCodes,
  });
  const [sessionRevision, setSessionRevision] = useState(0);
  const [location, setLocation] = useState(readBrowserLocation);
  const parsedRoute = parseConsoleRoute(location.pathname, location.search);
  const requestedRoute = parsedRoute ?? (location.pathname === "/" && navigation[0] !== undefined
    ? defaultConsoleRoute(navigation[0].id)
    : undefined);
  const resolvedRoute = resolveAuthorizedConsoleRoute(
    requestedRoute,
    navigation.map((item) => item.id),
  );

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
    if (location.pathname === "/" && resolvedRoute.kind === "route") {
      window.history.replaceState(null, "", formatConsoleRoute(resolvedRoute.route));
    }
  }, [location.pathname, resolvedRoute]);

  function selectNavigation(navigationId: string) {
    const item = navigation.find((candidate) => candidate.id === navigationId);
    if (item === undefined) {
      return;
    }
    navigate(defaultConsoleRoute(item.id));
  }

  function sessionChanged() {
    setSessionRevision((current) => current + 1);
  }

  if (navigation.length === 0) {
    return (
      <ProductShell
        productName="可信知识平台"
        pageTitle="当前没有可用工作区"
        pageSummary="系统只显示与当前会话能力相符的入口，不会因此展示租户正文或管理入口。"
        navigation={[]}
        activeNavigationId=""
        accountLabel="已验证浏览器会话"
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
        productName="可信知识平台"
        pageTitle={denied ? "当前会话无权访问此工作区" : "未找到工作区"}
        pageSummary={denied
          ? "该 URL 不会被静默替换为其他工作区，请使用当前会话被授权的入口。"
          : "请从已授权的导航入口继续操作。"}
        navigation={navigation}
        activeNavigationId=""
        onNavigationChange={selectNavigation}
        accountLabel="已验证浏览器会话"
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
  const dataActive = route.navigationId === "data";
  const accessActive = route.navigationId === "access";
  const modelsActive = route.navigationId === "models";
  const governanceActive = route.navigationId === "governance";
  const queryActive = route.navigationId === "query";
  const platformActive = route.navigationId === "platform";
  const dataPage = dataActive ? dataPageContent(route) : undefined;

  return (
    <ProductShell
      productName="可信知识平台"
      pageTitle={
        accessActive ? "成员与权限" : platformActive ? "平台租户生命周期" : queryActive ? "从已授权资料中获得答案" : governanceActive ? route.task === "query" ? "查询用量与审计" : "受限评测运行" : modelsActive ? route.task === "configuration" ? "受控模型配置" : route.task === "providers" ? "供应商连接与模型发现" : "模型部署与能力验证" : dataPage?.title ?? "可信知识平台"
      }
      pageSummary={
        queryActive
          ? "答案必须附带可核验来源。没有足够证据或没有访问权限时，系统会明确拒绝回答。"
          : accessActive
          ? "成员操作由当前浏览器会话恢复租户范围；目录使用服务端分页，停用必须二次确认。"
          : platformActive
          ? "建立与迁移租户状态，不展示租户正文、成员详情或平台操作者信息。"
          : governanceActive && route.task === "query"
          ? "核对当前租户的查询配额、计量结果和脱敏运行记录。"
          : governanceActive
          ? "选择已有数据集和助手版本运行服务端评测，只展示指标和质量门结论。"
          : modelsActive && route.task === "configuration"
          ? "从平台发布目录创建、轮换和启用模型配置；API Key 不会再次显示。"
          : modelsActive && route.task === "providers"
          ? "核对供应商连接状态和可用的模型发现事实；Endpoint、凭据和原始响应用于后端执行，不会进入浏览器。"
          : modelsActive
          ? "选择当前有效发现候选建立草稿部署，在独立任务中执行真实能力探测和高风险发布确认。"
          : dataPage?.summary ?? ""
      }
      navigation={navigation}
      activeNavigationId={route.navigationId}
      onNavigationChange={selectNavigation}
      accountLabel="已验证浏览器会话"
    >
      <MessageBar intent="info" className="usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点使用合成数据，并限制模型调用次数、文件大小和并发会话。
        </MessageBarBody>
      </MessageBar>
      <Button appearance="subtle" onClick={() => void onLogout()}>
        退出登录
      </Button>

      <section hidden={!dataActive}>
        <div className="data-task-switcher" aria-label="数据任务切换">
          <Button
            appearance={route.navigationId === "data" && route.task === "governed-file" ? "primary" : "subtle"}
            onClick={() => navigate({ navigationId: "data", task: "governed-file" })}
          >
            文件来源登记
          </Button>
          <Button
            appearance={route.navigationId === "data" && route.task === "structured-dataset" ? "primary" : "subtle"}
            onClick={() => navigate({ navigationId: "data", task: "structured-dataset" })}
          >
            结构化数据集登记
          </Button>
          <Button
            appearance={route.navigationId === "data" && route.task === "object-storage" ? "primary" : "subtle"}
            onClick={() => navigate({ navigationId: "data", task: "object-storage" })}
          >
            对象存储范围登记
          </Button>
          <Button
            appearance={route.navigationId === "data" && route.task === "first-publication" ? "primary" : "subtle"}
            onClick={() => navigate({ navigationId: "data", task: "first-publication" })}
          >
            首份文本发布
          </Button>
        </div>
        {route.navigationId === "data" && route.task === "governed-file" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据任务" description="正在准备受治理文件来源流程。" />}>
            <GovernedFileDataWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "structured-dataset" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据集登记任务" description="正在准备私有 CSV 登记流程。" />}>
            <StructuredDatasetRegistrationWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : route.navigationId === "data" && route.task === "object-storage" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载对象存储任务" description="正在准备平台签发范围登记流程。" />}>
            <ObjectStorageDataWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : (
          <KnowledgePublicationFlow
            client={client}
            sessionRevision={sessionRevision}
            onSessionChanged={sessionChanged}
          />
        )}
      </section>
      <section hidden={!accessActive}>
        {accessActive ? <>
          <div className="data-task-switcher" aria-label="成员与权限任务切换">
            <Button appearance={route.navigationId === "access" && route.task === "members" ? "primary" : "subtle"} onClick={() => navigate({ navigationId: "access", task: "members", page: 1 })}>成员目录</Button>
            <Button appearance={route.navigationId === "access" && route.task === "roles" ? "primary" : "subtle"} onClick={() => navigate({ navigationId: "access", task: "roles", page: 1 })}>自定义角色</Button>
          </div>
          <Suspense fallback={<StatePanel kind="loading" title="正在加载成员与权限" description="正在准备受限访问治理入口。" />}>
            {route.navigationId === "access" && route.task === "members" ? <TenantMemberWorkspace client={client} route={route} sessionRevision={sessionRevision} onRouteChange={navigate} /> : null}
            {route.navigationId === "access" && route.task === "roles" ? <TenantCustomRoleWorkspace client={client} route={route} sessionRevision={sessionRevision} onRouteChange={navigate} /> : null}
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
                sessionRevision={sessionRevision}
                onSessionChanged={sessionChanged}
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
            <ConsoleQueryWorkspace onSessionExpired={() => void onLogout()} />
          </Suspense>
        ) : null}
      </section>
      <section hidden={!governanceActive}>
        {governanceActive ? (
          <>
            <div className="data-task-switcher" aria-label="治理任务切换">
              <Button appearance={route.navigationId === "governance" && route.task === "query" ? "primary" : "subtle"} onClick={() => navigate({ navigationId: "governance", task: "query" })}>
                查询用量与审计
              </Button>
              <Button appearance={route.navigationId === "governance" && route.task === "evaluation" ? "primary" : "subtle"} onClick={() => navigate({ navigationId: "governance", task: "evaluation" })}>
                受限评测运行
              </Button>
            </div>
            {route.navigationId === "governance" && route.task === "query" ? (
              <Suspense fallback={<StatePanel kind="loading" title="正在加载治理工作区" description="正在准备查询用量和脱敏审计视图。" />}>
                <QueryGovernanceWorkspace client={client} sessionRevision={sessionRevision} onSessionChanged={sessionChanged} />
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
            <PlatformTenantLifecycleWorkspace client={client} />
          </Suspense>
        ) : null}
      </section>
    </ProductShell>
  );
}

function readBrowserLocation() {
  return {
    pathname: window.location.pathname,
    search: window.location.search,
  };
}

function dataPageContent(route: Extract<ConsoleRoute, { navigationId: "data" }>) {
  switch (route.task) {
    case "governed-file":
      return {
        title: "受治理文件来源",
        summary: "在租户知识空间中登记并探测受控 UTF-8 文件，不展示对象位置或正文。",
      };
    case "structured-dataset":
      return {
        title: "结构化数据集登记",
        summary: "登记平台私有 UTF-8 CSV 与冻结字段映射，不展示对象位置、正文或敏感字段。",
      };
    case "object-storage":
      return {
        title: "对象存储范围登记",
        summary: "使用平台签发范围标识与选择修订登记受治理对象存储来源，不展示内部对象位置。",
      };
    case "first-publication":
      return {
        title: "发布首份知识数据",
        summary: "用一个受控任务完成租户验证、知识空间创建、文本上传和发布核验。",
      };
  }
}
