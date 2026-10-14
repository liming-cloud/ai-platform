import { Badge, Button } from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError, type ControlPlaneClient, type PlatformProviderConnectionSummary,
  type PlatformProviderModelCandidate, type PlatformProviderPresets } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { ConnectionStatus, connectionStatusLabel, PlatformConnectionEmptyState } from "./PlatformModelSupplyWorkspace";
import { PlatformConnectionPagination, PlatformConnectionSearch } from "./PlatformConnectionPagination";
import "./platform-model-connections.css";

type DetailRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "connection-detail" }>;
type Candidates = { items: PlatformProviderModelCandidate[]; totalElements: number; hasPrevious: boolean; hasNext: boolean };
const EMPTY_CANDIDATES: Candidates = { items: [], totalElements: 0, hasPrevious: false, hasNext: false };
const PROBE_VALIDITY_MILLIS = 24 * 60 * 60 * 1000;

/** 精确恢复连接及当前版本的发现与发布事实；网络操作不得乐观伪造验证状态。 */
export function PlatformProviderConnectionDetail({ client, route, onRouteChange }: {
  client: ControlPlaneClient; route: DetailRoute; onRouteChange: (route: ConsoleRoute, replace?: boolean) => void;
}) {
  const [state, setState] = useState<"loading" | "ready" | "empty" | "error" | "stale">("loading");
  const [connection, setConnection] = useState<PlatformProviderConnectionSummary>();
  const [directory, setDirectory] = useState<PlatformProviderPresets>();
  const [candidates, setCandidates] = useState<Candidates>(EMPTY_CANDIDATES);
  const [discoveredTotal, setDiscoveredTotal] = useState(0);
  const [failure, setFailure] = useState("");
  const [feedback, setFeedback] = useState<{ failed: boolean; text: string }>();
  const [operation, setOperation] = useState<"probe" | "publish">();
  const [busy, setBusy] = useState(true);
  const [pagination, setPagination] = useState({ page: route.candidatePage, size: route.pageSize ?? 20 });
  const [observedAt, setObservedAt] = useState(() => Date.now());
  const owner = useRef(0);
  const pending = useRef(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const focused = useRef(false);
  const load = useCallback(async () => {
    const requestOwner = ++owner.current;
    let missingConnection: ApiRequestError | undefined;
    pending.current = false;
    setOperation(undefined); setState(current => current === "ready" ? "ready" : "loading"); setBusy(true); setFailure("");
    try {
      const [current, presets] = await Promise.all([client.getPlatformProviderConnection(route.connectionId).catch(error => {
        // 只有精确连接读取的既有资源错误代表连接不存在；预设或候选失败不改变对象存在事实。
        if (error instanceof ApiRequestError && error.status === 404 && error.code === "MODEL_RUNTIME.RESOURCE.0001") missingConnection = error;
        throw error;
      }), client.getPlatformProviderPresets()]);
      if (requestOwner !== owner.current) return;
      const [publication, models] = await Promise.all([
        client.getPlatformProviderConnectionPublication(current.id, current.version),
        current.status === "VERIFIED"
          ? client.listPlatformProviderModelCandidates(current.id, current.version, route.candidatePage, route.pageSize ?? 20, route.query ?? "")
          : Promise.resolve(EMPTY_CANDIDATES),
      ]);
      if (requestOwner !== owner.current) return;
      // 原型的发现总数独立于搜索结果；仅多读一个有界窗口，不能拉全量模型再过滤。
      const full = route.query && current.status === "VERIFIED"
        ? await client.listPlatformProviderModelCandidates(current.id, current.version, 1, 1)
        : models;
      if (requestOwner !== owner.current) return;
      if (current.id !== route.connectionId || publication.connectionId !== current.id || publication.connectionRevision !== current.version) {
        throw new Error("connection-binding-mismatch");
      }
      const last = Math.max(1, Math.ceil(models.totalElements / (route.pageSize ?? 20)));
      // 越界入口必须替换历史，避免后退再次落入同一无效页。
      if (route.candidatePage > last) { onRouteChange({ ...route, candidatePage: last }, true); return; }
      setConnection({ ...current, published: publication.published }); setObservedAt(Date.now());
      setPagination({ page: route.candidatePage, size: route.pageSize ?? 20 });
      setDirectory(presets); setCandidates(models); setState("ready");
      setDiscoveredTotal(full.totalElements);
    } catch (error) {
      if (requestOwner !== owner.current) return;
      setConnection(undefined); setDirectory(undefined); setCandidates(EMPTY_CANDIDATES);
      setFailure(error instanceof ApiRequestError ? error.message : "请求未完成，请稍后重试。");
      // 并发读取可能先收到其他失败；必须匹配该错误本身，不能让连接的 404 标记覆盖权限拒绝。
      setState(client.hasSession() ? missingConnection !== undefined && error === missingConnection ? "empty" : "error" : "stale");
    } finally { if (requestOwner === owner.current) setBusy(false); }
  }, [client, route, onRouteChange]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => { window.clearTimeout(timer); owner.current += 1; };
  }, [load]);

  useEffect(() => {
    // 保存后和身份替换后，焦点归属新页面，不能留在已卸载的供应商凭据弹窗。
    if ((state === "ready" || state === "empty" || state === "error") && !focused.current) { focused.current = true; headingRef.current?.focus(); }
  }, [state]);

  useEffect(() => {
    if (!connection || !hasCurrentConnectionProbe(connection, observedAt)) return;
    // 页面停留跨过有效期时撤下发布提示，不能等下一次刷新才反映到期。
    const remaining = Date.parse(connection.lastProbedAt!) + PROBE_VALIDITY_MILLIS - Date.now();
    const timer = window.setTimeout(() => setObservedAt(Date.now()), Math.max(0, remaining));
    return () => window.clearTimeout(timer);
  }, [connection, observedAt]);

  async function perform(action: "probe" | "publish") {
    if (!connection || pending.current) return;
    const requestOwner = owner.current;
    pending.current = true; setOperation(action); setFailure(""); setFeedback(undefined);
    try {
      if (action === "probe") {
        const result = await client.discoverPlatformProviderModels(connection.id, connection.version);
        if (requestOwner !== owner.current) return;
        setFeedback({ failed: !result.succeeded, text: result.succeeded ? "连接探测通过，可以发布本版本。"
          : "连接探测失败，未发布任何能力。连接未通过验证，不能发布连接。" });
      } else {
        const result = await client.publishPlatformProviderConnection(connection.id, connection.version);
        if (requestOwner !== owner.current) return;
        if (!result.published || result.connectionId !== connection.id || result.connectionRevision !== connection.version) throw new Error("publication-binding-mismatch");
        setFeedback({ failed: false, text: "连接版本已发布；尚未自动建立部署或授权租户。" });
      }
      setOperation(undefined);
      await load();
    } catch (error) {
      if (requestOwner !== owner.current) return;
      setOperation(undefined); setFailure(error instanceof ApiRequestError ? error.message : "请求未完成，请稍后重试。");
    } finally { if (requestOwner === owner.current) pending.current = false; }
  }

  if (state === "stale") return <StatePanel kind="stale" title="浏览器会话已经失效" description="请重新登录后查看平台连接。" />;
  if (state === "loading") return <StatePanel kind="loading" title="正在加载供应商连接" description="正在读取连接及探测结果。" />;
  if (state === "empty" || state === "error" || !connection) return <div className={"platform-connection-page" + (state === "empty" ? " platform-connection-page--empty" : "")}>
    <header className="platform-connection-page__heading"><p className="platform-connection-page__context">模型治理</p>
      <div><h1 ref={headingRef} tabIndex={-1}>供应商连接探测</h1>
      {state === "empty" ? <div className="platform-connection-object-line">暂无记录</div> : null}</div>
      <Button appearance="secondary" onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "connections", page: 1, ...route.directory })}>返回连接目录</Button></header>
    {state === "empty" ? <PlatformConnectionEmptyState detail />
      : <StatePanel kind="error" title="供应商连接暂时不可用" description={failure} actionLabel="重新加载" onAction={() => { focused.current = false; void load(); }} />}
  </div>;
  const preset = directory?.items.find(item => item.id === connection.preset?.id);
  const capability = connection.preset?.capability;
  const verified = hasCurrentConnectionProbe(connection, observedAt);
  const publish = !connection.published && verified;
  const disabled = busy || !!operation || connection.status === "DISABLED" || !connection.credentialConfigured
    || (connection.discoveryProfile === "VOLCENGINE_ARK_CONTROL_PLANE" && !connection.discoveryCredentialsConfigured);
  const failed = connection.lastProbeSucceeded === false;
  return <div className="platform-connection-page">
    <header className="platform-connection-page__heading"><p className="platform-connection-page__context">模型治理</p>
      <div><h1 ref={headingRef} tabIndex={-1}>供应商连接探测</h1>
      <div className="platform-connection-object-line">{connectionStatusLabel(connection)}</div></div>
      <Button appearance="secondary" onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "connections", page: 1, ...route.directory })}>返回连接目录</Button></header>
    <div className={"platform-connection-note" + (failed ? " platform-connection-note--danger" : "")}>
      {failed ? "连接探测失败，未发布任何能力。" : connection.published ? "连接已发布，可重新探测查看详细结果。"
        : verified ? "连接探测已通过，可以发布连接。"
          : connection.status === "VERIFIED" ? "连接探测已失效，请重新执行连接探测。" : "尚未执行本版本的连接探测。"}</div>
    <div className="platform-connection-detail">
      <div><section className="platform-connection-section"><div className="platform-connection-section__heading">
        <div><span className="platform-connection-kicker">连接版本 v{connection.version}</span><h2>{connection.displayName}</h2></div><ConnectionStatus connection={connection} /></div>
        <dl className="platform-connection-descriptions">
          <div><dt>供应商类型</dt><dd>{preset?.name ?? "已有连接"}</dd></div>
          <div><dt>运行协议</dt><dd>{capability === "RERANK" ? "独立重排序适配"
            : connection.providerKind === "OPENAI_COMPATIBLE" ? "OpenAI 兼容接口" : "待确认协议"}</dd></div>
          <div><dt>能力范围</dt><dd>{capability ? capabilityLabel(capability) : "待确认"}</dd></div>
          <div><dt>访问认证</dt><dd>{connection.credentialConfigured ? "已配置（不回显）" : "待配置"}</dd></div>
          {connection.discoveryProfile === "VOLCENGINE_ARK_CONTROL_PLANE" ? <div><dt>模型发现认证</dt>
            <dd>{connection.discoveryCredentialsConfigured ? "独立 AK/SK 已配置（不回显）" : "待配置独立 AK/SK"}</dd></div> : null}
          <div><dt>最近探测</dt><dd>{connection.lastProbedAt ? new Date(connection.lastProbedAt).toLocaleString("zh-CN") : "尚未探测"}</dd></div>
        </dl></section>
        <section className="platform-connection-section"><div className="platform-connection-section__heading"><h2>发现模型</h2><Badge appearance="tint">{discoveredTotal} 个</Badge></div>
          <PlatformConnectionSearch query={route.query ?? ""} disabled={!!operation} onChange={query => onRouteChange({ ...route, query, candidatePage: 1 })} />
          <div className="platform-connection-table" role="region" aria-label="发现模型" aria-busy={busy}><table><thead><tr><th scope="col">发现模型</th><th scope="col">能力</th></tr></thead>
            <tbody>{candidates.items.length === 0 ? <tr><td colSpan={2}>{route.query ? "没有匹配记录，请调整筛选条件或返回目录。" : "当前没有可用的模型发现结果。"}</td></tr> : candidates.items.map(item => <tr key={item.modelCode + ":" + item.modelType}>
              <td>{item.modelCode}</td><td>{item.classification === "REQUIRES_ADMIN_CONFIRMATION" ? "待管理员确认" : capabilityLabel(item.modelType ?? "")}</td></tr>)}</tbody></table>
            <PlatformConnectionPagination total={candidates.totalElements} page={pagination.page} size={pagination.size}
              disabled={busy || !!operation} label="发现模型分页" onChange={(candidatePage, pageSize) => onRouteChange({ ...route, candidatePage, pageSize })} />
          </div></section></div>
      <aside><section className="platform-connection-release"><span className="platform-connection-kicker">当前动作</span>
        <h2>{connection.published ? "连接已发布" : "探测后再发布"}</h2>
        <p>验证公网 HTTPS、认证、能力和响应边界；发布后仍需建立平台部署并显式授权租户。</p>
        <div><Button appearance="primary" disabled={disabled} onClick={() => void perform(publish ? "publish" : "probe")}>
          {operation ? operation === "probe" ? "正在探测" : "正在发布" : publish ? "发布连接" : "执行连接探测"}</Button></div>
        {feedback ? <p className={"form-feedback" + (feedback.failed ? " platform-connection-feedback--danger" : "")} role={feedback.failed ? "alert" : "status"}>{feedback.text}</p> : null}
        {failure ? <p className="form-feedback platform-connection-feedback--danger" role="alert">{failure}</p> : null}
      </section></aside>
    </div>
  </div>;
}

/** 页面提示承接 ADR-0095 的 24 小时边界；缺失、未来或到期探测失败关闭，最终发布仍由服务端复核。 */
export function hasCurrentConnectionProbe(connection: PlatformProviderConnectionSummary, now: number): boolean {
  const probedAt = connection.lastProbedAt ? Date.parse(connection.lastProbedAt) : Number.NaN;
  return connection.status === "VERIFIED" && connection.lastProbeSucceeded === true
    && probedAt <= now && now < probedAt + PROBE_VALIDITY_MILLIS;
}

function capabilityLabel(value: string): string {
  return ({ CHAT: "对话模型", EMBEDDING: "向量模型", RERANK: "重排序模型" } as Record<string, string>)[value] ?? "待确认";
}
