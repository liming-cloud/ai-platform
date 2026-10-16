import { Badge, Button, Dialog, DialogBody, DialogSurface, DialogTitle, DialogTrigger } from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError, type ControlPlaneClient, type PlatformProviderConnectionSummary, type PlatformProviderPresets } from "../../api/control-plane-client";
import { defaultConsoleRoute, type ConsoleRoute } from "../../session/console-route";
import { PlatformProviderConnectionForm } from "./PlatformProviderConnectionForm";
import { PlatformConnectionPagination, PlatformConnectionSearch } from "./PlatformConnectionPagination";
import { readPlatformConnectionDirectory, type ConnectionProjection } from "./platform-connection-read";
import "./platform-model-connections.css";

type ConnectionRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "connections" }>;

/** 平台连接目录只消费安全投影；创建表单拥有本次输入，关闭后立即卸载。 */
export function PlatformModelSupplyWorkspace({ client, route, sessionRevision, onRouteChange }: {
  client: ControlPlaneClient; route: ConnectionRoute; sessionRevision: number;
  onRouteChange: (route: ConsoleRoute, replace?: boolean) => void;
}) {
  const [state, setState] = useState<"loading" | "ready" | "partial" | "error" | "denied" | "stale">("loading");
  const [connections, setConnections] = useState<PlatformProviderConnectionSummary[]>([]);
  const [directory, setDirectory] = useState<PlatformProviderPresets>();
  const [total, setTotal] = useState(0);
  const [published, setPublished] = useState<number>();
  const [busy, setBusy] = useState(true);
  const [pagination, setPagination] = useState({ page: route.page, size: route.pageSize ?? 20 });
  const [creating, setCreating] = useState(false);
  const owner = useRef(0);
  const createConnectionTriggerRef = useRef<HTMLButtonElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const focused = useRef(false);
  const load = useCallback(async () => {
    const requestOwner = ++owner.current;
    // 每次重读撤下旧表单；辅助事实未确认期间不能提交旧草稿或保留秘密输入。
    setCreating(false);
    if (!client.hasSession()) { setConnections([]); setDirectory(undefined); setState("stale"); return; }
    setState(current => current === "ready" || current === "partial" ? current : "loading"); setBusy(true);
    try {
      const size = route.pageSize ?? 20;
      const { connections: page, presets, publishedConnections, partial } = await readPlatformConnectionDirectory(client, route.page, size, route.query ?? "");
      if (requestOwner !== owner.current) return;
      const last = Math.max(1, Math.ceil(page.totalElements / size));
      // 越界入口必须替换历史，避免后退再次落入同一无效页。
      if (route.page > last) { onRouteChange({ ...route, page: last }, true); return; }
      setConnections(page.items); setDirectory(presets); setTotal(page.totalElements);
      setPublished(publishedConnections);
      setPagination({ page: route.page, size }); setState(partial ? "partial" : "ready");
    } catch (error) {
      if (requestOwner !== owner.current) return;
      setConnections([]); setDirectory(undefined); setPublished(undefined); setTotal(0);
      // 拒绝读取不确认目录内容，也不能借重试恢复旧数据；会话失效仍优先返回登录边界。
      setState(!client.hasSession() ? "stale" : error instanceof ApiRequestError && error.status === 403 ? "denied" : "error");
    } finally { if (requestOwner === owner.current) setBusy(false); }
  }, [client, route, onRouteChange]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => { window.clearTimeout(timer); owner.current += 1; };
  }, [load, sessionRevision]);

  useEffect(() => {
    if ((state !== "ready" && state !== "partial") || focused.current) return;
    // 旧模态先完成本帧释放，再把焦点交给新会话页面，避免旧隐藏范围覆盖新触发器。
    const frame = window.requestAnimationFrame(() => { focused.current = true; headingRef.current?.focus(); });
    return () => window.cancelAnimationFrame(frame);
  }, [state]);

  function closeCreateDialog() {
    setCreating(false);
    window.requestAnimationFrame(() => createConnectionTriggerRef.current?.focus());
  }

  if (state === "stale") return <StatePanel kind="stale" title="浏览器会话已经失效" description="请重新登录后查看平台连接。" />;
  if (state === "loading" || state === "error" || state === "denied") return <PlatformConnectionReadState kind={state}
    onRetry={() => void load()} onReturn={() => onRouteChange(defaultConsoleRoute("platform"))} />;

  const empty = total === 0 && !route.query;
  return <div className={"platform-connection-page" + (empty ? " platform-connection-page--empty" : "")} data-read-state={state}>
    <header className="platform-connection-page__heading">
      <p className="platform-connection-page__context">模型治理</p>
      <div className={state === "partial" ? "platform-connection-page__copy" : undefined}><h1 ref={headingRef} tabIndex={-1}>平台模型连接</h1>
        <div className="platform-connection-object-line">{state === "partial" ? <span>部分完成</span> : empty ? "暂无记录" : `${published} 个已发布连接`}</div></div>
      <Dialog open={creating && state === "ready" && !busy} onOpenChange={(_, data) => { if (data.open && state === "ready" && !busy) setCreating(true); else closeCreateDialog(); }}>
        {/* 正式触发器登记焦点恢复目标，身份替换卸载模态时也能清理原隐藏范围。 */}
        <DialogTrigger disableButtonEnhancement><Button ref={createConnectionTriggerRef} className="platform-connection-create" appearance="primary" disabled={busy || state === "partial"}>新增供应商连接</Button></DialogTrigger>
        <DialogSurface className="foundation-task-dialog platform-connection-dialog" backdrop={{ className: "platform-connection-backdrop" }}><DialogBody>
          <header className="platform-connection-dialog__header"><span className="platform-connection-kicker">模型治理 · 连接草稿</span><DialogTitle>新增供应商连接</DialogTitle></header>
          {creating && directory && state === "ready" && !busy ? <PlatformProviderConnectionForm client={client} directory={directory} onCancel={closeCreateDialog}
            onCreated={id => { setCreating(false); onRouteChange({ navigationId: "platform-model-supply", task: "connection-detail", connectionId: id, candidatePage: 1,
              directory: { page: route.page, pageSize: route.pageSize, query: route.query } }); }} /> : null}
        </DialogBody></DialogSurface>
      </Dialog>
    </header>
    {state === "partial" ? <div className="platform-connection-note" role="status"><p>发布摘要暂未取得，暂不能新增连接。连接目录仍可查询；重新载入后再试。</p>
      <Button appearance="secondary" disabled={busy} onClick={() => void load()}>重新载入</Button></div> : null}
    {empty ? <div role="region" aria-label="平台模型连接目录" aria-busy={busy}><PlatformConnectionEmptyState /></div> : <>
    {state !== "partial" ? <div className="platform-connection-note">先添加供应商连接，再探测模型与发布。租户只看已授权模型，不显示平台地址或访问密钥。</div> : null}
    <PlatformConnectionSearch query={route.query ?? ""} onChange={query => onRouteChange({ ...route, query, page: 1 })} />
    <div className="platform-connection-table" role="region" aria-label="平台模型连接目录" aria-busy={busy}>
      <table><thead><tr><th scope="col">连接</th><th scope="col">运行能力</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
        <tbody>{connections.length === 0 ? <tr><td colSpan={4}>{route.query ? "没有匹配记录，请调整筛选条件。" : "当前还没有供应商连接。"}</td></tr> : connections.map(connection => {
          const provider = directory?.items.find(item => item.id === connection.preset?.id);
          const capability = connection.preset?.capability;
          return <tr key={connection.id}><td><strong>{connection.displayName}</strong>
            <small>{provider?.name ?? "已有连接"} · {capability === "RERANK" ? "厂商重排适配"
              : connection.providerKind === "OPENAI_COMPATIBLE" ? "OpenAI 兼容协议" : "待确认协议"}</small></td>
            <td>{capability ? ({ CHAT: "对话模型", EMBEDDING: "向量模型", RERANK: "重排序模型" })[capability] : "待确认"}</td>
            <td><ConnectionStatus connection={connection} /></td><td><Button appearance="subtle" aria-label={"查看" + connection.displayName}
              onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "connection-detail", connectionId: connection.id, candidatePage: 1,
                directory: { page: route.page, pageSize: route.pageSize, query: route.query } })}>查看</Button></td></tr>;
        })}</tbody></table>
      <PlatformConnectionPagination total={total} page={pagination.page} size={pagination.size} disabled={busy} label="目录分页"
        onChange={(page, pageSize) => onRouteChange({ ...route, page, pageSize })} />
    </div>
    </>}
  </div>;
}

/** 两页读取失败关闭且保留原型页头；拒绝只返回既有平台入口，不确认目标资源是否存在。 */
export function PlatformConnectionReadState({ kind, detail = false, onRetry, onReturn }: {
  kind: "loading" | "error" | "denied"; detail?: boolean; onRetry: () => void; onReturn: () => void;
}) {
  const title = detail ? "供应商连接探测" : "平台模型连接";
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (kind === "denied") headingRef.current?.focus(); }, [kind]);
  return <div className="platform-connection-page">
    <header className="platform-connection-page__heading"><p className="platform-connection-page__context">模型治理</p>
      <div className="platform-connection-page__copy"><h1 ref={headingRef} tabIndex={-1}>{title}</h1>
        <div className="platform-connection-object-line"><span>{kind === "denied" ? "访问受限" : kind === "loading" ? "正在载入" : "载入失败"}</span></div></div></header>
    <section className="platform-connection-empty platform-connection-read-state" data-kind={kind}
      aria-live="polite" aria-busy={kind === "loading"} role={kind === "error" ? "alert" : undefined}>
      {kind === "loading" ? <div className="platform-connection-skeleton" aria-label="正在加载"><span /><span /><span /></div>
        : kind === "denied" ? <><p className="platform-connection-empty__label">无权访问</p><h2>当前会话不能访问此任务</h2>
          <p>页面不会确认目标资源是否存在，也不会加载正文、引用或历史消息。</p>
          <Button appearance="secondary" onClick={onReturn}>返回已授权入口</Button></>
        : <><p className="platform-connection-empty__label">任务失败</p><h2>暂时无法加载{title}</h2>
          <p>当前任务没有完成，也没有使用过期结果。请检查连接后安全重试。</p>
          <Button appearance="secondary" onClick={onRetry}>重新加载</Button></>}
    </section>
  </div>;
}

/** 只呈现已接受的两种无记录说明；存在连接但没有发现候选时不得使用详情空态。 */
export function PlatformConnectionEmptyState({ detail = false }: { detail?: boolean }) {
  return <section className="platform-connection-empty" aria-live="polite" data-kind="empty">
    <p className="platform-connection-empty__label">暂无内容</p>
    <h2>{detail ? "供应商连接探测目前为空" : "平台模型连接目前为空"}</h2>
    <p>{detail ? "当前还没有可查看的连接。请先从连接目录选择或建立连接草稿。"
      : "当前还没有供应商连接。请先保存连接草稿，探测通过后再单独发布。"}</p>
  </section>;
}

/** 状态来自当前版本的验证与发布事实；失败或停用不会沿用旧的通过标签。 */
export function ConnectionStatus({ connection }: { connection: Pick<ConnectionProjection, "status" | "published" | "lastProbeSucceeded"> }) {
  const failed = connection.lastProbeSucceeded === false;
  const label = connectionStatusLabel(connection);
  const tone = failed ? "danger" : connection.published || connection.status === "VERIFIED" ? "success" : "warning";
  return <Badge className="platform-connection-status" data-tone={tone} appearance="tint" color={tone}>{label}</Badge>;
}

/** 页头和表格复用同一当前对象状态，不把验证状态换算成发布事实。 */
export function connectionStatusLabel(connection: Pick<ConnectionProjection, "status" | "published" | "lastProbeSucceeded">): string {
  return connection.status === "DISABLED" ? "已停用" : connection.lastProbeSucceeded === false ? "失败" : connection.published ? "已发布"
    : connection.status === "VERIFIED" ? "验证通过" : "草稿";
}
