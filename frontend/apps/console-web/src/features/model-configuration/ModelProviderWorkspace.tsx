import {
  Badge,
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type ProviderConnectionSummary,
  type ProviderModelDiscoveryHistory,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type ProviderRoute = Extract<ConsoleRoute, { task: "providers" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";
const MODEL_DISCOVERY_TIMEOUT_MILLIS = 10_000;

interface Failure {
  message: string;
  traceId: string;
}

/**
 * 供应商连接和模型发现历史采用独立服务端页，不在浏览器拼接完整记录集。
 */
export function ModelProviderWorkspace({
  client,
  route,
  sessionRevision,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: ProviderRoute;
  sessionRevision: number;
  onRouteChange: (route: ProviderRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [connections, setConnections] = useState<ProviderConnectionSummary[]>([]);
  const [connectionTotal, setConnectionTotal] = useState(0);
  const [connectionHasPrevious, setConnectionHasPrevious] = useState(false);
  const [connectionHasNext, setConnectionHasNext] = useState(false);
  const [discoveries, setDiscoveries] = useState<ProviderModelDiscoveryHistory[]>([]);
  const [discoveryTotal, setDiscoveryTotal] = useState(0);
  const [discoveryHasPrevious, setDiscoveryHasPrevious] = useState(false);
  const [discoveryHasNext, setDiscoveryHasNext] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [discoveringConnectionId, setDiscoveringConnectionId] = useState<string>();

  const loadConnections = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const page = await client.listProviderConnections(route.providerPage);
      setConnections(page.items);
      setConnectionTotal(page.totalElements);
      setConnectionHasPrevious(page.hasPrevious);
      setConnectionHasNext(page.hasNext);
      setViewState("ready");
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client, route.providerPage]);

  const loadDiscoveries = useCallback(async () => {
    if (route.connectionId === undefined || !client.hasSession()) {
      setDiscoveries([]);
      setDiscoveryTotal(0);
      setDiscoveryHasPrevious(false);
      setDiscoveryHasNext(false);
      return;
    }
    try {
      const page = await client.listProviderModelDiscoveries(
        route.connectionId,
        route.discoveryPage,
      );
      setDiscoveries(page.items);
      setDiscoveryTotal(page.totalElements);
      setDiscoveryHasPrevious(page.hasPrevious);
      setDiscoveryHasNext(page.hasNext);
    } catch (error) {
      setFailure(toFailure(error));
    }
  }, [client, route.connectionId, route.discoveryPage]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadConnections(), 0);
    return () => window.clearTimeout(timer);
  }, [loadConnections, sessionRevision]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadDiscoveries(), 0);
    return () => window.clearTimeout(timer);
  }, [loadDiscoveries, sessionRevision]);

  async function discoverModels(connection: ProviderConnectionSummary) {
    setDiscoveringConnectionId(connection.id);
    setFailure(undefined);
    try {
      await client.discoverProviderModels(
        connection.id,
        connection.version,
        MODEL_DISCOVERY_TIMEOUT_MILLIS,
      );
      onRouteChange({
        ...route,
        connectionId: connection.id,
        discoveryPage: 1,
      });
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setDiscoveringConnectionId(undefined);
    }
  }

  if (viewState === "unauthenticated") {
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="供应商连接和模型发现记录已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
    );
  }

  if (viewState === "loading") {
    return (
      <StatePanel
        kind="loading"
        title="正在加载供应商连接"
        description="正在读取安全状态与最新探测摘要，不会读取 Endpoint 或凭据。"
      />
    );
  }

  if (viewState === "error") {
    return (
      <StatePanel
        kind="error"
        title="供应商连接暂时不可用"
        description={failureDescription(failure)}
        actionLabel="重新加载"
        onAction={() => void loadConnections()}
      />
    );
  }

  return (
    <div className="model-configuration-workspace">
      <div className="governance-toolbar">
        <Text>每个列表均由服务端分页；供应商端点、凭据和厂商原始响应用于后端执行，不会进入浏览器。</Text>
        <Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => void loadConnections()}>
          刷新连接
        </Button>
      </div>

      {failure === undefined ? null : <FailureMessage failure={failure} />}

      <section className="model-configuration-section">
        <header className="task-heading">
          <Title3>供应商连接</Title3>
          <Text>查看配置状态、最近探测结论和下一步操作；选择连接后再按需读取模型发现历史。</Text>
        </header>
        <div className="model-configuration-list">
          {connections.length === 0 ? <Text>当前页没有供应商连接。</Text> : connections.map((connection) => (
            <article className="model-configuration-card" key={connection.id}>
              <div>
                <Title3>{connection.displayName}</Title3>
                <Text>{connection.providerKind} · {connection.region} · {connection.authenticationType}</Text>
              </div>
              <div className="model-configuration-status">
                <Badge appearance="tint" color={connection.status === "VERIFIED" ? "success" : "warning"}>
                  {connection.status}
                </Badge>
                <Text>{connection.credentialConfigured ? "凭据已配置" : "未配置凭据"}</Text>
                <Text>{connection.lastProbeSucceeded === true ? "最近探测通过" : connection.lastProbeSucceeded === false ? "最近探测未通过" : "尚未探测"}</Text>
              </div>
              <div className="task-actions">
                <Button
                  appearance="primary"
                  disabled={connection.status !== "VERIFIED" || !connection.credentialConfigured || discoveringConnectionId !== undefined}
                  onClick={() => void discoverModels(connection)}
                >
                  {discoveringConnectionId === connection.id ? "正在发现模型" : "发现可用模型"}
                </Button>
                <Button
                  appearance="secondary"
                  onClick={() => onRouteChange({
                    ...route,
                    connectionId: connection.id,
                    discoveryPage: 1,
                  })}
                >
                  查看模型发现记录
                </Button>
              </div>
            </article>
          ))}
        </div>
        <Pagination
          label="供应商连接"
          page={route.providerPage}
          total={connectionTotal}
          hasPrevious={connectionHasPrevious}
          hasNext={connectionHasNext}
          onPrevious={() => onRouteChange({ ...route, providerPage: route.providerPage - 1 })}
          onNext={() => onRouteChange({ ...route, providerPage: route.providerPage + 1 })}
        />
      </section>

      {route.connectionId === undefined ? null : (
        <section className="model-configuration-section" aria-label="模型发现历史">
          <header className="operations-heading">
            <div>
              <Title3>模型发现历史</Title3>
              <Text>只展示可审计摘要；有效成功记录可继续创建部署，其他记录应重新发现。</Text>
            </div>
            <Button appearance="subtle" onClick={() => onRouteChange({ ...route, connectionId: undefined, discoveryPage: 1 })}>
              关闭历史
            </Button>
          </header>
          <div className="model-configuration-list">
            {discoveries.length === 0 ? <Text>当前页没有模型发现记录。</Text> : discoveries.map((discovery) => (
              <article className="model-configuration-card" key={discovery.discoveryId}>
                <div>
                  <Title3>{discovery.succeeded ? "模型发现成功" : "模型发现未完成"}</Title3>
                  <Text>连接修订 {discovery.providerConnectionRevision} · 候选数量 {discovery.candidateCount} · {discovery.resultCode}</Text>
                </div>
                <div className="model-configuration-status">
                  <Badge appearance="tint" color={discovery.succeeded ? "success" : "danger"}>
                    {discovery.nextStep === "CREATE_DEPLOYMENT" ? "可创建部署" : "需要重新发现"}
                  </Badge>
                  <Text>有效至 {formatDate(discovery.expiresAt)}</Text>
                </div>
              </article>
            ))}
          </div>
          <Pagination
            label="模型发现历史"
            page={route.discoveryPage}
            total={discoveryTotal}
            hasPrevious={discoveryHasPrevious}
            hasNext={discoveryHasNext}
            onPrevious={() => onRouteChange({ ...route, discoveryPage: route.discoveryPage - 1 })}
            onNext={() => onRouteChange({ ...route, discoveryPage: route.discoveryPage + 1 })}
          />
        </section>
      )}
    </div>
  );
}

function Pagination({
  label,
  page,
  total,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: {
  label: string;
  page: number;
  total: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return (
    <div className="task-actions" aria-label={`${label}分页`}>
      <Button appearance="subtle" disabled={!hasPrevious} onClick={onPrevious}>上一页</Button>
      <Text>第 {page} 页，共 {total} 条</Text>
      <Button appearance="subtle" disabled={!hasNext} onClick={onNext}>下一页</Button>
    </div>
  );
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) {
    return { message: error.message, traceId: error.traceId };
  }
  return { message: "无法连接控制平面，请检查网络后重试", traceId: "" };
}

function failureDescription(failure?: Failure): string {
  if (failure === undefined) {
    return "请稍后重试；如果问题持续存在，请联系平台运维人员。";
  }
  return failure.traceId.length === 0
    ? failure.message
    : `${failure.message}，追踪标识：${failure.traceId}`;
}

function FailureMessage({ failure }: { failure: Failure }) {
  return (
    <MessageBar intent="error">
      <MessageBarBody>
        <MessageBarTitle>操作未完成</MessageBarTitle>
        {failureDescription(failure)}
      </MessageBarBody>
    </MessageBar>
  );
}
