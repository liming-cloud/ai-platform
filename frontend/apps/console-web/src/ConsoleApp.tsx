import {
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { ProductShell, StatePanel } from "@foundation/design-system";
import { lazy, Suspense, useMemo, useState } from "react";
import { ControlPlaneClient } from "./api/control-plane-client";
import { KnowledgePublicationFlow } from "./features/knowledge-publication/KnowledgePublicationFlow";
import { BrowserSessionGate } from "./session/BrowserSessionGate";
import { visibleNavigation } from "./session/navigation-capability";
import { resolvePostAuthenticationDestination } from "./session/post-authentication-navigation";

type NavigationId = ReturnType<typeof visibleNavigation>[number]["id"];

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

const PlatformTenantLifecycleWorkspace = lazy(async () => {
  const module = await import("./features/platform-governance/PlatformTenantLifecycleWorkspace");
  return { default: module.PlatformTenantLifecycleWorkspace };
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
  const [activeNavigationId, setActiveNavigationId] = useState<
    NavigationId
  >(navigation[0]?.id ?? "data");
  const [sessionRevision, setSessionRevision] = useState(0);
  const [dataTask, setDataTask] = useState<
    "governed-file" | "structured-dataset" | "object-storage" | "first-publication"
  >("governed-file");
  const [governanceTask, setGovernanceTask] = useState<"query" | "evaluation">("query");
  const governanceActive = activeNavigationId === "governance";
  const modelsActive = activeNavigationId === "models";
  const platformActive = activeNavigationId === "platform";
  const dataPage = dataTask === "governed-file"
    ? {
      title: "受治理文件来源",
      summary: "在租户知识空间中登记并探测受控 UTF-8 文件，不展示对象位置或正文。",
    }
    : dataTask === "structured-dataset"
    ? {
      title: "结构化数据集登记",
      summary: "登记平台私有 UTF-8 CSV 与冻结字段映射，不展示对象位置、正文或敏感字段。",
    }
    : dataTask === "object-storage"
    ? {
      title: "对象存储范围登记",
      summary: "使用平台签发范围标识与选择修订登记受治理对象存储来源，不展示内部对象位置。",
    }
    : {
      title: "发布首份知识数据",
      summary: "用一个受控任务完成租户验证、知识空间创建、文本上传和发布核验。",
    };

  function selectNavigation(navigationId: string) {
    if (!navigation.some((item) => item.id === navigationId)) {
      return;
    }
    if (navigationId === "query") {
      window.location.assign("/query/");
      return;
    }
    if (
      navigationId === "data" || navigationId === "models"
      || navigationId === "governance" || navigationId === "platform"
    ) {
      setActiveNavigationId(navigationId);
    }
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

  return (
    <ProductShell
      productName="可信知识平台"
      pageTitle={
        platformActive ? "平台租户生命周期" : governanceActive ? governanceTask === "query" ? "查询用量与审计" : "受限评测运行" : modelsActive ? "受控模型配置" : dataPage.title
      }
      pageSummary={
        platformActive
          ? "建立与迁移租户状态，不展示租户正文、成员详情或平台操作者信息。"
          : governanceActive && governanceTask === "query"
          ? "核对当前租户的查询配额、计量结果和脱敏运行记录。"
          : governanceActive
          ? "选择已有数据集和助手版本运行服务端评测，只展示指标和质量门结论。"
          : modelsActive
          ? "从平台发布目录创建、轮换和启用模型配置；API Key 不会再次显示。"
          : dataPage.summary
      }
      navigation={navigation}
      activeNavigationId={activeNavigationId}
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

      <section hidden={governanceActive || modelsActive || platformActive}>
        <div className="data-task-switcher" aria-label="数据任务切换">
          <Button
            appearance={dataTask === "governed-file" ? "primary" : "subtle"}
            onClick={() => setDataTask("governed-file")}
          >
            文件来源登记
          </Button>
          <Button
            appearance={dataTask === "structured-dataset" ? "primary" : "subtle"}
            onClick={() => setDataTask("structured-dataset")}
          >
            结构化数据集登记
          </Button>
          <Button
            appearance={dataTask === "object-storage" ? "primary" : "subtle"}
            onClick={() => setDataTask("object-storage")}
          >
            对象存储范围登记
          </Button>
          <Button
            appearance={dataTask === "first-publication" ? "primary" : "subtle"}
            onClick={() => setDataTask("first-publication")}
          >
            首份文本发布
          </Button>
        </div>
        {dataTask === "governed-file" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据任务" description="正在准备受治理文件来源流程。" />}>
            <GovernedFileDataWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : dataTask === "structured-dataset" ? (
          <Suspense fallback={<StatePanel kind="loading" title="正在加载数据集登记任务" description="正在准备私有 CSV 登记流程。" />}>
            <StructuredDatasetRegistrationWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : dataTask === "object-storage" ? (
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
            <ModelConfigurationWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : null}
      </section>
      <section hidden={!governanceActive}>
        {governanceActive ? (
          <>
            <div className="data-task-switcher" aria-label="治理任务切换">
              <Button appearance={governanceTask === "query" ? "primary" : "subtle"} onClick={() => setGovernanceTask("query")}>
                查询用量与审计
              </Button>
              <Button appearance={governanceTask === "evaluation" ? "primary" : "subtle"} onClick={() => setGovernanceTask("evaluation")}>
                受限评测运行
              </Button>
            </div>
            {governanceTask === "query" ? (
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
