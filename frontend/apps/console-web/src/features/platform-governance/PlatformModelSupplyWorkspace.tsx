import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Select,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type PlatformProviderConnectionSummary,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type PlatformSupplyRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "connections" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";

interface Failure {
  message: string;
  traceId: string;
}

/**
 * 平台供应链只管理平台范围的连接摘要；租户连接、端点和凭据不进入此工作区。
 */
export function PlatformModelSupplyWorkspace({
  client,
  route,
  sessionRevision,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: PlatformSupplyRoute;
  sessionRevision: number;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [connections, setConnections] = useState<PlatformProviderConnectionSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [apiKey, setApiKey] = useState("");
  const [discoveryTarget, setDiscoveryTarget] = useState<PlatformProviderConnectionSummary>();
  const [discoverySummary, setDiscoverySummary] = useState<string>();

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const page = await client.listPlatformProviderConnections(route.page);
      setConnections(page.items);
      setTotal(page.totalElements);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
      setViewState("ready");
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client, route.page]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, sessionRevision]);

  async function createConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const submittedApiKey = apiKey;
    form.reset();
    setApiKey("");
    const displayName = String(values.get("displayName") ?? "").trim();
    const endpoint = String(values.get("endpoint") ?? "").trim();
    const region = String(values.get("region") ?? "").trim();
    const providerKind = String(values.get("providerKind") ?? "DETERMINISTIC") as "DETERMINISTIC" | "OPENAI_COMPATIBLE" | "OLLAMA";
    if (!displayName || !endpoint || !region || !submittedApiKey.trim()) {
      setFailure({ message: "请完整填写连接名称、厂商、受控 Endpoint、区域和 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.createPlatformProviderConnection({
        displayName,
        endpoint,
        region,
        providerKind,
        authenticationType: "API_KEY",
        apiKey: submittedApiKey,
      });
      setCreating(false);
      await load();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setApiKey("");
      setSubmitting(false);
    }
  }

  async function discoverModels() {
    if (discoveryTarget === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    setDiscoverySummary(undefined);
    try {
      const result = await client.discoverPlatformProviderModels(
        discoveryTarget.id,
        discoveryTarget.version,
      );
      setDiscoveryTarget(undefined);
      setDiscoverySummary(result.succeeded
        ? `模型发现完成：已找到 ${result.models.length} 个可用候选。`
        : "模型发现未完成，请确认连接状态后重试。");
      await load();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (viewState === "unauthenticated") {
    return <StatePanel kind="stale" title="浏览器会话已经失效" description="平台模型供应链摘要已清除，请重新登录。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  if (viewState === "loading") {
    return <StatePanel kind="loading" title="正在加载平台连接" description="正在读取平台范围的安全摘要，不会读取 Endpoint 或凭据。" />;
  }
  if (viewState === "error") {
    return <StatePanel kind="error" title="平台连接暂时不可用" description={failureDescription(failure)} actionLabel="重新加载" onAction={() => void load()} />;
  }

  return (
    <div className="model-configuration-workspace">
      <div className="governance-toolbar">
        <Text>这是平台范围连接目录，独立于租户连接；记录按页显示，Endpoint 和凭据不会在列表中展示。</Text>
        <div className="task-actions"><Button appearance="primary" onClick={() => setCreating(true)}>新建平台连接</Button><Button appearance="subtle" onClick={() => void load()}>刷新连接</Button></div>
      </div>
      {failure === undefined ? null : <FailureMessage failure={failure} />}
      {discoverySummary === undefined ? null : <MessageBar intent="success"><MessageBarBody><MessageBarTitle>模型发现已记录</MessageBarTitle>{discoverySummary}</MessageBarBody></MessageBar>}
      <section className="model-configuration-section">
        <header className="task-heading"><Title3>平台厂商连接</Title3><Text>仅平台管理员可创建；连接创建后通过一次受控请求完成验证和模型发现。</Text></header>
        <div className="member-directory-table" role="region" aria-label="平台厂商连接分页表格">
          <table><thead><tr><th>连接</th><th>厂商与区域</th><th>凭据与状态</th><th>最近探测</th><th>操作</th></tr></thead><tbody>
            {connections.length === 0 ? <tr><td colSpan={5}>当前页没有平台厂商连接。</td></tr> : connections.map((connection) => <tr key={connection.id}><td>{connection.displayName}</td><td>{connection.providerKind} · {connection.region}</td><td><Badge appearance="tint" color={connection.status === "VERIFIED" ? "success" : "warning"}>{connection.status === "VERIFIED" ? "已验证" : "待验证"}</Badge> {connection.credentialConfigured ? "凭据已配置" : "未配置凭据"}</td><td>{connection.lastProbeSucceeded === true ? "通过" : connection.lastProbeSucceeded === false ? "未通过" : "尚未验证"}</td><td><div className="task-actions"><Button appearance="secondary" disabled={submitting || connection.status === "DISABLED" || !connection.credentialConfigured} onClick={() => setDiscoveryTarget(connection)}>{connection.status === "VERIFIED" ? "刷新模型候选" : "验证并发现模型"}</Button><Button appearance="primary" disabled={connection.status !== "VERIFIED"} onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "deployments", connectionId: connection.id, connectionVersion: connection.version, candidatePage: 1, deploymentPage: 1 })}>管理部署</Button></div></td></tr>)}
          </tbody></table>
        </div>
        <div className="task-actions"><Text>共 {total} 条</Text><Button disabled={!hasPrevious} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button><Text>第 {route.page} 页</Text><Button disabled={!hasNext} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button></div>
      </section>
      <Dialog open={creating} onOpenChange={(_, data) => { if (!data.open && !submitting) setCreating(false); }}>
        <DialogSurface><DialogBody><DialogTitle>新建平台厂商连接</DialogTitle><form className="foundation-dialog-form" onSubmit={(event) => void createConnection(event)}><DialogContent>
          <Text>Endpoint 和 API Key 仅用于本次平台范围受控写入；创建后页面不会回显二者，也不会写入租户范围。</Text>
          <Field label="连接名称" required><Input name="displayName" autoFocus disabled={submitting} /></Field>
          <Field label="厂商类型" required><Select name="providerKind" defaultValue="DETERMINISTIC" disabled={submitting}><option value="DETERMINISTIC">确定性开发连接</option><option value="OPENAI_COMPATIBLE">OpenAI 兼容接口</option><option value="OLLAMA">Ollama</option></Select></Field>
          <Field label="受控 Endpoint" required><Input name="endpoint" type="url" placeholder="https://…/v1" disabled={submitting} /></Field>
          <Field label="部署区域" required><Input name="region" placeholder="例如：local" disabled={submitting} /></Field>
          <Field label="API Key" required><Input type="password" value={apiKey} onChange={(_, data) => setApiKey(data.value)} autoComplete="new-password" disabled={submitting} /></Field>
        </DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setCreating(false)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在创建" : "创建平台连接"}</Button></DialogActions></form></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={discoveryTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setDiscoveryTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>发现平台模型</DialogTitle><DialogContent>
          <Text>系统验证 Endpoint 和凭据后会列出可用模型名称，直接从候选列表中选择即可。</Text>
        </DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setDiscoveryTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void discoverModels()}>{submitting ? "正在发现" : "开始发现"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
    </div>
  );
}

function FailureMessage({ failure }: { failure: Failure }) {
  return <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failureDescription(failure)}</MessageBarBody></MessageBar>;
}

function failureDescription(failure: Failure | undefined): string {
  if (failure === undefined) return "请求未完成。";
  return failure.message;
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) return { message: error.message, traceId: error.traceId };
  return { message: "请求未完成，请稍后重试。", traceId: "" };
}
