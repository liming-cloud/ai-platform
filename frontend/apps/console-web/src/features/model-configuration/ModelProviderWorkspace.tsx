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
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
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
const PROVIDER_PROBE_TIMEOUT_MILLIS = 10_000;

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
  const [creatingConnection, setCreatingConnection] = useState(false);
  const [rotationTarget, setRotationTarget] = useState<ProviderConnectionSummary>();
  const [probeTarget, setProbeTarget] = useState<ProviderConnectionSummary>();
  const [submitting, setSubmitting] = useState(false);
  const [creationApiKey, setCreationApiKey] = useState("");
  const [rotationApiKey, setRotationApiKey] = useState("");
  const [probeModelCode, setProbeModelCode] = useState("");

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

  async function createConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const apiKey = creationApiKey;
    form.reset();
    setCreationApiKey("");
    const displayName = String(values.get("displayName") ?? "").trim();
    const endpoint = String(values.get("endpoint") ?? "").trim();
    const region = String(values.get("region") ?? "").trim();
    const providerKind = String(values.get("providerKind") ?? "OPENAI_COMPATIBLE") as "DETERMINISTIC" | "OPENAI_COMPATIBLE" | "OLLAMA";
    if (displayName.length === 0 || endpoint.length === 0 || region.length === 0 || apiKey.trim().length === 0) {
      setFailure({ message: "请完整填写连接名称、厂商、受控 Endpoint、区域和 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.createProviderConnection({
        providerKind,
        displayName,
        endpoint,
        region,
        authenticationType: "API_KEY",
        apiKey,
      });
      setCreatingConnection(false);
      await loadConnections();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setCreationApiKey("");
      setSubmitting(false);
    }
  }

  async function rotateCredential(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (rotationTarget === undefined) return;
    const apiKey = rotationApiKey;
    setRotationApiKey("");
    if (apiKey.trim().length === 0) {
      setFailure({ message: "请输入新的 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.rotateProviderConnectionCredential(rotationTarget.id, rotationTarget.version, apiKey);
      setRotationTarget(undefined);
      await loadConnections();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setRotationApiKey("");
      setSubmitting(false);
    }
  }

  async function probeConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (probeTarget === undefined) return;
    const modelCode = probeModelCode.trim();
    if (modelCode.length === 0) {
      setFailure({ message: "请输入用于最小验证的模型代码。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.probeProviderConnection(
        probeTarget.id,
        probeTarget.version,
        modelCode,
        PROVIDER_PROBE_TIMEOUT_MILLIS,
      );
      setProbeTarget(undefined);
      setProbeModelCode("");
      await loadConnections();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
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
        <div className="task-actions">
          <Button appearance="primary" onClick={() => setCreatingConnection(true)}>新建连接</Button>
          <Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => void loadConnections()}>
            刷新连接
          </Button>
        </div>
      </div>

      {failure === undefined ? null : <FailureMessage failure={failure} />}

      <section className="model-configuration-section">
        <header className="task-heading">
          <Title3>供应商连接</Title3>
          <Text>查看配置状态、最近探测结论和下一步操作；选择连接后再按需读取模型发现历史。</Text>
        </header>
        <div className="member-directory-table" role="region" aria-label="供应商连接分页表格">
          <table>
            <thead><tr><th>连接</th><th>厂商与区域</th><th>凭据与状态</th><th>最近探测</th><th>修订</th><th>操作</th></tr></thead>
            <tbody>{connections.length === 0 ? <tr><td colSpan={6}>当前页没有供应商连接。</td></tr> : connections.map((connection) => (
              <tr key={connection.id}>
                <td>{connection.displayName}</td>
                <td>{connection.providerKind} · {connection.region}</td>
                <td><Badge appearance="tint" color={connection.status === "VERIFIED" ? "success" : "warning"}>{connection.status === "VERIFIED" ? "已验证" : "待验证"}</Badge> {connection.credentialConfigured ? "凭据已配置" : "未配置凭据"}</td>
                <td>{connection.lastProbeSucceeded === true ? "通过" : connection.lastProbeSucceeded === false ? "未通过" : "尚未探测"}</td>
                <td>{connection.version}</td>
                <td><div className="task-actions">
                  <Button appearance="secondary" disabled={submitting || !connection.credentialConfigured} onClick={() => setProbeTarget(connection)}>探测连接</Button>
                  <Button appearance="secondary" disabled={submitting} onClick={() => setRotationTarget(connection)}>轮换 API Key</Button>
                  <Button appearance="primary" disabled={connection.status !== "VERIFIED" || !connection.credentialConfigured || discoveringConnectionId !== undefined} onClick={() => void discoverModels(connection)}>{discoveringConnectionId === connection.id ? "正在发现模型" : "发现可用模型"}</Button>
                  <Button appearance="secondary" onClick={() => onRouteChange({ ...route, connectionId: connection.id, discoveryPage: 1 })}>查看发现记录</Button>
                </div></td>
              </tr>
            ))}</tbody>
          </table>
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
          <div className="member-directory-table" role="region" aria-label="模型发现历史分页表格">
            <table>
              <thead><tr><th>结果</th><th>连接修订</th><th>候选数量</th><th>下一步</th><th>有效至</th></tr></thead>
              <tbody>{discoveries.length === 0 ? <tr><td colSpan={5}>当前页没有模型发现记录。</td></tr> : discoveries.map((discovery) => (
                <tr key={discovery.discoveryId}>
                  <td><Badge appearance="tint" color={discovery.succeeded ? "success" : "danger"}>{discovery.succeeded ? "发现成功" : "发现未完成"}</Badge> {discovery.resultCode}</td>
                  <td>{discovery.providerConnectionRevision}</td>
                  <td>{discovery.candidateCount}</td>
                  <td>{discovery.nextStep === "CREATE_DEPLOYMENT" ? "可创建部署" : "需要重新发现"}</td>
                  <td>{formatDate(discovery.expiresAt)}</td>
                </tr>
              ))}</tbody>
            </table>
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

      <Dialog open={creatingConnection} onOpenChange={(_, data) => { if (!data.open && !submitting) setCreatingConnection(false); }}>
        <DialogSurface><DialogBody><DialogTitle>新建租户厂商连接</DialogTitle><form onSubmit={(event) => void createConnection(event)}><DialogContent>
          <Text>Endpoint 和 API Key 仅用于本次受控写入；创建后页面不会回显二者。新连接需先完成探测。</Text>
          <Field label="连接名称" required><Input name="displayName" autoFocus disabled={submitting} /></Field>
          <Field label="厂商类型" required><Select name="providerKind" defaultValue="OPENAI_COMPATIBLE" disabled={submitting}><option value="OPENAI_COMPATIBLE">OpenAI 兼容接口</option><option value="OLLAMA">Ollama</option><option value="DETERMINISTIC">确定性开发连接</option></Select></Field>
          <Field label="受控 Endpoint" required><Input name="endpoint" type="url" placeholder="https://…/v1" disabled={submitting} /></Field>
          <Field label="部署区域" required><Input name="region" placeholder="例如：cn-beijing" disabled={submitting} /></Field>
          <Field label="API Key" required><Input type="password" value={creationApiKey} onChange={(_, data) => setCreationApiKey(data.value)} autoComplete="new-password" disabled={submitting} /></Field>
        </DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setCreatingConnection(false)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在创建" : "创建连接"}</Button></DialogActions></form></DialogBody></DialogSurface>
      </Dialog>

      <Dialog open={rotationTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setRotationTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>轮换连接 API Key</DialogTitle><form onSubmit={(event) => void rotateCredential(event)}><DialogContent>
          <Text>轮换后连接修订会改变，既有模型发现和能力验证将失效；请重新探测连接并发现模型。</Text>
          <Field label="新的 API Key" required><Input type="password" value={rotationApiKey} onChange={(_, data) => setRotationApiKey(data.value)} autoComplete="new-password" disabled={submitting} /></Field>
        </DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setRotationTarget(undefined)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在轮换" : "确认轮换"}</Button></DialogActions></form></DialogBody></DialogSurface>
      </Dialog>

      <Dialog open={probeTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setProbeTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>探测厂商连接</DialogTitle><form onSubmit={(event) => void probeConnection(event)}><DialogContent>
          <Text>输入一个用于最小真实调用的模型代码。探测只保存安全结果摘要，不向浏览器返回厂商原始响应。</Text>
          <Field label="模型代码" required><Input value={probeModelCode} onChange={(_, data) => setProbeModelCode(data.value)} disabled={submitting} /></Field>
        </DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setProbeTarget(undefined)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在探测" : "开始探测"}</Button></DialogActions></form></DialogBody></DialogSurface>
      </Dialog>
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
