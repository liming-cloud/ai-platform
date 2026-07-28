import {
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { ProductShell, StatePanel } from "@foundation/design-system";
import { lazy, Suspense, useMemo, useState } from "react";
import { ControlPlaneClient } from "./api/control-plane-client";
import { KnowledgePublicationFlow } from "./features/knowledge-publication/KnowledgePublicationFlow";

const QueryGovernanceWorkspace = lazy(async () => {
  const module = await import(
    "./features/query-governance/QueryGovernanceWorkspace"
  );
  return { default: module.QueryGovernanceWorkspace };
});

const ModelConfigurationWorkspace = lazy(async () => {
  const module = await import(
    "./features/model-configuration/ModelConfigurationWorkspace"
  );
  return { default: module.ModelConfigurationWorkspace };
});

const NAVIGATION = [
  { id: "data", label: "数据" },
  { id: "models", label: "模型" },
  { id: "governance", label: "治理" },
] as const;

export function ConsoleApp() {
  const client = useMemo(() => new ControlPlaneClient(), []);
  const [activeNavigationId, setActiveNavigationId] = useState<
    typeof NAVIGATION[number]["id"]
  >("data");
  const [sessionRevision, setSessionRevision] = useState(0);
  const governanceActive = activeNavigationId === "governance";
  const modelsActive = activeNavigationId === "models";

  function selectNavigation(navigationId: string) {
    if (
      navigationId === "data" || navigationId === "models"
      || navigationId === "governance"
    ) {
      setActiveNavigationId(navigationId);
    }
  }

  function sessionChanged() {
    setSessionRevision((current) => current + 1);
  }

  return (
    <ProductShell
      productName="可信知识平台"
      pageTitle={
        governanceActive ? "查询用量与审计" : modelsActive ? "受控模型配置" : "发布首份知识数据"
      }
      pageSummary={
        governanceActive
          ? "核对当前租户的查询配额、计量结果和脱敏运行记录。"
          : modelsActive
          ? "从平台发布目录创建、轮换和启用模型配置；API Key 不会再次显示。"
          : "用一个受控任务完成租户验证、知识空间创建、文本上传和发布核验。"
      }
      navigation={NAVIGATION}
      activeNavigationId={activeNavigationId}
      onNavigationChange={selectNavigation}
      accountLabel="受限演示环境"
    >
      <MessageBar intent="info" className="usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点使用合成数据，并限制模型调用次数、文件大小和并发会话。
        </MessageBarBody>
      </MessageBar>

      <section hidden={governanceActive || modelsActive}>
        <KnowledgePublicationFlow
          client={client}
          sessionRevision={sessionRevision}
          onSessionChanged={sessionChanged}
        />
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
          <Suspense
            fallback={(
              <StatePanel
                kind="loading"
                title="正在加载治理工作区"
                description="正在准备查询用量和脱敏审计视图。"
              />
            )}
          >
            <QueryGovernanceWorkspace
              client={client}
              sessionRevision={sessionRevision}
              onSessionChanged={sessionChanged}
            />
          </Suspense>
        ) : null}
      </section>
    </ProductShell>
  );
}
