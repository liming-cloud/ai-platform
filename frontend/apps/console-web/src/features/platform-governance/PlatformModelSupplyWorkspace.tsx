import { Badge, Button, Dialog, DialogBody, DialogSurface, DialogTitle } from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError, type ControlPlaneClient, type PlatformProviderConnectionSummary, type PlatformProviderPresets } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { PlatformProviderConnectionForm } from "./PlatformProviderConnectionForm";
import { PlatformConnectionPagination, PlatformConnectionSearch } from "./PlatformConnectionPagination";
import "./platform-model-connections.css";

type ConnectionRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "connections" }>;

/** 平台连接目录只消费安全投影；创建表单拥有本次输入，关闭后立即卸载。 */
export function PlatformModelSupplyWorkspace({ client, route, sessionRevision, onRouteChange }: {
  client: ControlPlaneClient; route: ConnectionRoute; sessionRevision: number;
  onRouteChange: (route: ConsoleRoute, replace?: boolean) => void;
}) {
  const [state, setState] = useState<"loading" | "ready" | "error" | "stale">("loading");
  const [connections, setConnections] = useState<PlatformProviderConnectionSummary[]>([]);
  const [directory, setDirectory] = useState<PlatformProviderPresets>();
  const [total, setTotal] = useState(0);
  const [published, setPublished] = useState<number>();
  const [busy, setBusy] = useState(true);
  const [pagination, setPagination] = useState({ page: route.page, size: route.pageSize ?? 20 });
  const [failure, setFailure] = useState("");
  const [creating, setCreating] = useState(false);
  const owner = useRef(0);
  const createConnectionTriggerRef = useRef<HTMLButtonElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const focused = useRef(false);
  const load = useCallback(async () => {
    const requestOwner = ++owner.current;
    if (!client.hasSession()) { setConnections([]); setDirectory(undefined); setState("stale"); return; }
    setState(current => current === "ready" ? "ready" : "loading"); setBusy(true); setFailure("");
    try {
      const size = route.pageSize ?? 20;
      const [page, presets, summary] = await Promise.all([client.listPlatformProviderConnections(route.page, size, route.query ?? ""),
        client.getPlatformProviderPresets(), client.getPlatformProviderConnectionDirectorySummary()]);
      if (requestOwner !== owner.current) return;
      if (!presets.items.length || presets.items.some(item => !item.capabilities.length || !item.variants.length)) {
        throw new Error("invalid-directory");
      }
      if (!Number.isSafeInteger(summary.publishedConnections) || summary.publishedConnections < 0) throw new Error("invalid-summary");
      const last = Math.max(1, Math.ceil(page.totalElements / size));
      // 越界入口必须替换历史，避免后退再次落入同一无效页。
      if (route.page > last) { onRouteChange({ ...route, page: last }, true); return; }
      setConnections(page.items); setDirectory(presets); setTotal(page.totalElements);
      setPublished(summary.publishedConnections);
      setPagination({ page: route.page, size }); setState("ready");
    } catch (error) {
      if (requestOwner !== owner.current) return;
      setConnections([]); setDirectory(undefined);
      setFailure(error instanceof ApiRequestError ? error.message : "请求未完成，请稍后重试。");
      setState(client.hasSession() ? "error" : "stale");
    } finally { if (requestOwner === owner.current) setBusy(false); }
  }, [client, route, onRouteChange]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => { window.clearTimeout(timer); owner.current += 1; };
  }, [load, sessionRevision]);

  useEffect(() => {
    // 新会话不能恢复到已移除的旧弹窗；把焦点交给新页面，解除旧模态的隐藏范围。
    if (state === "ready" && !focused.current) { focused.current = true; headingRef.current?.focus(); }
  }, [state]);

  function closeCreateDialog() {
    setCreating(false);
    window.requestAnimationFrame(() => createConnectionTriggerRef.current?.focus());
  }

  if (state === "stale") return <StatePanel kind="stale" title="浏览器会话已经失效" description="请重新登录后查看平台连接。" />;
  if (state === "loading") return <StatePanel kind="loading" title="正在加载平台连接" description="正在读取连接目录。" />;
  if (state === "error") return <StatePanel kind="error" title="平台连接暂时不可用" description={failure} actionLabel="重新加载" onAction={() => void load()} />;

  return <div className="platform-connection-page">
    <header className="platform-connection-page__heading">
      <div><p className="platform-connection-page__context">模型治理</p><h1 ref={headingRef} tabIndex={-1}>平台模型连接</h1>
        <div className="platform-connection-object-line">{published} 个已发布连接</div></div>
      <Button ref={createConnectionTriggerRef} appearance="primary" onClick={() => setCreating(true)}>新增供应商连接</Button>
    </header>
    <div className="platform-connection-note">先添加供应商连接，再探测模型与发布。租户只看已授权模型，不显示平台地址或访问密钥。</div>
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
    <Dialog open={creating} onOpenChange={(_, data) => { if (!data.open) closeCreateDialog(); }}>
      <DialogSurface className="foundation-task-dialog platform-connection-dialog"><DialogBody>
        <span className="platform-connection-kicker">模型治理 · 连接草稿</span><DialogTitle>新增供应商连接</DialogTitle>
        {creating && directory ? <PlatformProviderConnectionForm client={client} directory={directory} onCancel={closeCreateDialog}
          onCreated={id => { setCreating(false); onRouteChange({ navigationId: "platform-model-supply", task: "connection-detail", connectionId: id, candidatePage: 1,
            directory: { page: route.page, pageSize: route.pageSize, query: route.query } }); }} /> : null}
      </DialogBody></DialogSurface>
    </Dialog>
  </div>;
}

/** 状态来自当前版本的验证与发布事实；失败或停用不会沿用旧的通过标签。 */
export function ConnectionStatus({ connection }: { connection: PlatformProviderConnectionSummary }) {
  const failed = connection.lastProbeSucceeded === false;
  const label = connectionStatusLabel(connection);
  return <Badge appearance="tint" color={failed ? "danger" : connection.published || connection.status === "VERIFIED" ? "success" : "warning"}>{label}</Badge>;
}

/** 页头和表格复用同一当前对象状态，不把验证状态换算成发布事实。 */
export function connectionStatusLabel(connection: PlatformProviderConnectionSummary): string {
  return connection.status === "DISABLED" ? "已停用" : connection.lastProbeSucceeded === false ? "失败" : connection.published ? "已发布"
    : connection.status === "VERIFIED" ? "验证通过" : "草稿";
}
