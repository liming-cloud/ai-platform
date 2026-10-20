import { PlatformModelSupplyNavigation } from "./PlatformModelSupplyNavigation";
import { Button, Dialog, DialogSurface } from "@fluentui/react-components";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiRequestError, type ControlPlaneClient, type PageResult, type PlatformModelDeploymentSummary } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { PlatformConnectionPagination } from "./PlatformConnectionPagination";
import { PlatformModelDeploymentForm, typeLabel } from "./PlatformModelDeploymentForm";
import { readPlatformDeployments, settleDeploymentReads } from "./platform-deployment-read";
import "./platform-model-connections.css";
import "./platform-model-deployments.css";

type DeploymentRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "deployments" | "deployment-detail" }>;
type ReadState = "loading" | "ready" | "partial" | "disabled" | "empty" | "error" | "denied" | "stale";

/** 目录与精确详情承接同一草稿快照；身份替换、重读和离开页面均撤下旧表单及迟到响应。 */
export function PlatformModelDeploymentWorkspace({ client, route, sessionRevision, onRouteChange }: {
  client: ControlPlaneClient; route: DeploymentRoute; sessionRevision: number;
  onRouteChange: (route: ConsoleRoute, replace?: boolean) => void;
}) {
  const [state, setState] = useState<ReadState>("loading");
  const [result, setResult] = useState<Awaited<ReturnType<typeof readPlatformDeployments>>>();
  const [creating, setCreating] = useState(false);
  const [creatingBusy, setCreatingBusy] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(true);
  const [tenantNames, setTenantNames] = useState<string[]>();
  const owner = useRef(0), pending = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null), focused = useRef(false);
  const detail = route.task === "deployment-detail";
  const load = useCallback(async () => {
    const token = ++owner.current;
    setCreating(false); setCreatingBusy(false); focused.current = false; setFeedback(""); setResult(undefined); setTenantNames(undefined); setState("loading"); setBusy(true);
    if (!client.hasSession()) { setState("stale"); setBusy(false); return; }
    try {
      const read = await readPlatformDeployments(client, route.task === "deployment-detail" ? { id: route.deploymentId }
        : { page: route.deploymentPage, size: route.pageSize ?? 20 });
      if (token !== owner.current) return;
      let kind = read.kind;
      if (route.task === "deployment-detail") {
        const deployment = read.data as PlatformModelDeploymentSummary;
        if (deployment.inputs) {
          const names: string[] = [];
          // 有界分批读取明确选择的租户名称，不遍历整个租户目录，也不把 ID 作为页面名称。
          const ids = deployment.inputs.plannedTenantIds;
          for (let start = 0; start < ids.length; start += 10) {
            const batch = await settleDeploymentReads(ids.slice(start, start + 10).map(id => client.getTenantLifecycle(id)));
            if (token !== owner.current) return;
            for (const item of batch) if (item.status === "rejected" && item.reason instanceof ApiRequestError
              && (item.reason.status === 401 || item.reason.status === 403)) throw item.reason;
            for (let index = 0; index < batch.length; index++) {
              const item = batch[index]!;
              if (item.status === "fulfilled") {
                if (item.value.tenantId !== ids[start + index] || !item.value.displayName) throw new Error("invalid-tenant-binding");
                names.push(item.value.displayName);
              } else if (item.reason instanceof ApiRequestError && (item.reason.status >= 500
                || (item.reason.status === 0 && item.reason.code === "NETWORK_UNAVAILABLE"))) kind = "partial";
              else throw item.reason;
            }
          }
          if (names.length === ids.length) setTenantNames(names);
        }
      } else {
        const page = read.data as PageResult<PlatformModelDeploymentSummary>;
        const last = Math.max(1, Math.ceil(page.totalElements / (route.pageSize ?? 20)));
        if (route.deploymentPage > last) { onRouteChange({ ...route, deploymentPage: last }, true); return; }
      }
      if (token !== owner.current) return;
      setResult(read); setState(kind);
    } catch (error) {
      if (token !== owner.current) return;
      setResult(undefined); setTenantNames(undefined);
      setState(!client.hasSession() ? "stale" : error instanceof ApiRequestError && error.status === 403 ? "denied"
        : route.task === "deployment-detail" && error instanceof ApiRequestError && error.status === 404 && error.code === "MODEL_RUNTIME.RESOURCE.0002" ? "empty" : "error");
    } finally { if (token === owner.current) setBusy(false); }
  }, [client, route, onRouteChange]);
  useEffect(() => {
    const ownership = owner;
    const timer = window.setTimeout(() => void load(), 0);
    return () => { window.clearTimeout(timer); ownership.current++; };
  }, [load, sessionRevision]);
  useEffect(() => {
    if (state === "loading" || focused.current) return;
    const frame = window.requestAnimationFrame(() => { heading.current?.focus(); focused.current = true; });
    return () => window.cancelAnimationFrame(frame);
  }, [state]);

  const directory = { navigationId: "platform-model-supply", task: "deployments", candidatePage: 1,
    deploymentPage: route.task === "deployment-detail" ? route.directoryPage ?? 1 : route.deploymentPage,
    pageSize: route.pageSize ?? 20 } as const;
  const page = !detail && result ? result.data as PageResult<PlatformModelDeploymentSummary> : undefined;
  const deployment = detail && result ? result.data as PlatformModelDeploymentSummary : undefined;
  const readable = state === "ready" || state === "partial" || state === "disabled";
  const writable = state === "ready" && !busy;

  async function operate() {
    if (!deployment || !writable || pending.current) return;
    pending.current = true; setBusy(true); setFeedback("");
    const token = owner.current;
    try {
      if (deployment.status === "VERIFIED") await client.publishPlatformModelDeployment(deployment.id, deployment.version);
      else await client.probePlatformModelDeployment(deployment.id, deployment.version, 30_000);
      if (token === owner.current) await load();
    } catch (error) {
      if (token === owner.current) setFeedback(error instanceof ApiRequestError ? error.message : "操作未完成，请重新载入后重试。");
    } finally { pending.current = false; if (token === owner.current) setBusy(false); }
  }

  return <div className="platform-connection-page platform-deployment-page" data-read-state={state}>
    <header className="platform-connection-page__heading"><p className="platform-connection-page__context">模型治理</p>
      <div className="platform-connection-page__copy"><h1 ref={heading} tabIndex={-1}>{detail ? "平台部署发布" : "平台模型部署"}</h1>
        {readable ? <div className="platform-connection-object-line">{state === "partial" ? "部分完成" : state === "disabled" ? "暂不可用"
          : deployment ? statusLabel(deployment.status) : `${result?.facts?.counts.published ?? 0} 个可授权部署`}</div> : null}</div>
      {detail ? <Button appearance="secondary" onClick={() => onRouteChange(directory)}>返回部署目录</Button>
        : <Button className="platform-connection-create" appearance="primary" disabled={!writable} onClick={() => setCreating(true)}>建立部署草稿</Button>}
    </header>
    <PlatformModelSupplyNavigation active="deployments" onRouteChange={onRouteChange} />
    {!readable ? <ReadState kind={state} detail={detail} onRetry={() => void load()} /> : <>
      <div className="platform-connection-note" role={state === "partial" ? "status" : undefined}>
        {state === "partial" ? <><p>{detail ? "租户授权事实暂未取得，本页只读；不能发布或扩大授权。" : "授权摘要暂未取得，本页暂为只读；已确认的部署仍可查看。"}</p><Button appearance="secondary" onClick={() => void load()}>重新载入</Button></>
          : detail ? "冻结连接版本、模型能力、拟授权租户和驻留范围后执行验证。" : "平台部署发布后只进入可授权目录，不会自动授予未选择的租户，也不会修改租户配置、索引或助手。"}
      </div>
      {page ? page.items.length === 0 ? <ReadState kind="empty" detail={false} onRetry={() => void load()} /> : <div className="platform-connection-table" role="region" aria-label="平台模型部署目录">
        <table><thead><tr>{["部署", "能力", "授权范围", "状态", "操作"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead><tbody>{page.items.map(item => <tr key={item.id}>
          <td data-label="部署"><strong>{item.displayName}</strong><small className="deployment-residency">{item.inputs?.residency ?? "驻留范围暂未取得"}</small></td>
          <td data-label="能力">{typeLabel(item.modelType)}</td><td data-label="授权范围">{state === "partial" || !item.inputs ? "暂未取得" : `${item.status === "PUBLISHED" ? "" : "拟授权 "}${item.inputs.plannedTenantIds.length} 个租户`}</td>
          <td data-label="状态"><DeploymentStatus deployment={item} /></td><td data-label="操作"><Button appearance="subtle" aria-label={`查看${item.displayName}`} onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "deployment-detail", deploymentId: item.id, directoryPage: page.pageNumber, pageSize: directory.pageSize })}>查看</Button></td>
        </tr>)}</tbody></table><PlatformConnectionPagination label="平台模型部署分页" total={page.totalElements} page={page.pageNumber} size={directory.pageSize} disabled={busy}
          onChange={(deploymentPage, pageSize) => onRouteChange({ ...directory, deploymentPage, pageSize })} /></div> : null}
      {deployment ? <div className="platform-connection-detail"><div><section className="platform-connection-section platform-connection-section--facts"><div className="platform-connection-section__heading"><div><span className="platform-connection-kicker">平台部署</span><h2>{deployment.displayName}</h2></div><DeploymentStatus deployment={deployment} /></div>
        <dl className="platform-connection-descriptions"><Fact label="连接" value={deployment.connectionDisplayName} /><Fact label="能力" value={typeLabel(deployment.modelType)} />
          <Fact label="驻留范围" value={deployment.inputs?.residency ?? "暂未取得"} /><Fact label={deployment.status === "PUBLISHED" ? "已授权租户" : "拟授权租户"} value={state === "partial" ? "暂未取得" : tenantNames?.join("、") ?? "暂未取得"} />
          <Fact label="声明上下文" value={`${deployment.contextWindow} Token`} />{deployment.outputLimit !== null ? <Fact label="声明输出上限" value={`${deployment.outputLimit} Token`} /> : null}
          {deployment.embeddingDimension !== null ? <Fact label="声明向量维度" value={String(deployment.embeddingDimension)} /> : null}
          {deployment.inputs?.rerankCandidateLimit != null ? <Fact label="声明候选上限" value={String(deployment.inputs.rerankCandidateLimit)} /> : null}
          <Fact label="参数来源" value={deployment.inputs?.parameterSource ?? "历史输入未声明；不能作为验证依据"} />
          <Fact label="最近验证" value={deployment.verifiedAt ? new Date(deployment.verifiedAt).toLocaleString("zh-CN") : "尚未验证"} />
        </dl></section></div><aside><section className="platform-connection-release"><span className="platform-connection-kicker">发布决定</span><h2>验证通过后进入授权目录</h2><p>发布只对明确选中的有效租户生效，不会自动生成租户配置、索引或助手变更。</p>
          <p className="deployment-help">本次验证：最多 3 次请求、128 字符合成输入、单次输出上限 64 Token、总超时 30 秒。月度费用未核验。</p>
          <div className="platform-connection-release-actions"><Button appearance="primary" disabled={deployment.status !== "PUBLISHED" && (!writable || deployment.status === "DISABLED")} onClick={() => deployment.status === "PUBLISHED" ? onRouteChange(directory) : void operate()}>
            {deployment.status === "PUBLISHED" ? "返回部署目录" : deployment.status === "VERIFIED" ? "发布部署" : "执行能力验证"}</Button></div>{feedback ? <p className="deployment-feedback" role="alert">{feedback}</p> : null}
        </section></aside></div> : null}
    </>}
    <Dialog open={creating} onOpenChange={(_, data) => { if (!data.open && !creatingBusy) setCreating(false); }}><DialogSurface className="foundation-task-dialog platform-deployment-dialog" backdrop={{ className: "platform-connection-backdrop" }}>
      {creating ? <PlatformModelDeploymentForm client={client} onSavingChange={setCreatingBusy} onClose={() => setCreating(false)} onCreated={id => { setCreating(false); onRouteChange({ navigationId: "platform-model-supply", task: "deployment-detail", deploymentId: id, directoryPage: directory.deploymentPage, pageSize: directory.pageSize }); }} /> : null}
    </DialogSurface></Dialog>
  </div>;
}
function Fact({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
function statusLabel(status: PlatformModelDeploymentSummary["status"]): string { return ({ DRAFT: "草稿", VERIFIED: "验证通过", PUBLISHED: "已发布", DISABLED: "已停用" })[status]; }
function DeploymentStatus({ deployment }: { deployment: PlatformModelDeploymentSummary }) {
  return <span className="platform-connection-status" data-tone={deployment.status === "PUBLISHED" || deployment.status === "VERIFIED" ? "success" : deployment.status === "DISABLED" ? "danger" : "info"}>{statusLabel(deployment.status)}</span>;
}
function ReadState({ kind, detail, onRetry }: { kind: ReadState; detail: boolean; onRetry: () => void }) {
  const failed = kind === "error" || kind === "denied" || kind === "stale";
  return <section className="platform-connection-empty platform-connection-read-state" data-kind={kind} role={kind === "loading" ? "status" : "region"} aria-label={kind === "empty" ? "空状态" : "页面读取状态"}>
    <span className="platform-connection-empty__label">{kind === "loading" ? "加载中" : kind === "denied" ? "权限不足" : kind === "stale" ? "会话失效" : failed ? "加载失败" : "暂无记录"}</span>
    <h2>{kind === "loading" ? "正在准备当前内容" : kind === "denied" ? "当前账号无权查看此页面" : kind === "stale" ? "请重新登录" : failed ? "暂时无法加载内容" : "当前没有可显示的记录"}</h2>
    <p>{kind === "empty" ? detail ? "当前没有可查看的平台部署。请返回部署目录选择或建立草稿。" : "当前还没有平台模型部署。请选择已发布连接的模型，确认参数和拟授权租户后保存草稿。" : failed ? "当前事实未确认；请检查权限或重新载入。" : "正在读取最新状态，请稍候。"}</p>
    {kind === "error" ? <Button appearance="secondary" onClick={onRetry}>重新载入</Button> : null}
  </section>;
}
