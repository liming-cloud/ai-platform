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
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type PlatformModelDeploymentSummary,
  type PlatformProviderModelCandidate,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type DeploymentRoute = Extract<ConsoleRoute, { navigationId: "platform-model-supply"; task: "deployments" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";
const PROBE_TIMEOUT_MILLIS = 10_000;

interface Failure { message: string; traceId: string; }

/** 平台管理员在独立任务中把当前发现候选转化为可验证、发布或停用的共享部署。 */
export function PlatformModelDeploymentWorkspace({
  client, route, sessionRevision, onRouteChange,
}: {
  client: ControlPlaneClient;
  route: DeploymentRoute;
  sessionRevision: number;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [deployments, setDeployments] = useState<PlatformModelDeploymentSummary[]>([]);
  const [deploymentTotal, setDeploymentTotal] = useState(0);
  const [deploymentPrevious, setDeploymentPrevious] = useState(false);
  const [deploymentNext, setDeploymentNext] = useState(false);
  const [candidates, setCandidates] = useState<PlatformProviderModelCandidate[]>([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [candidatePrevious, setCandidatePrevious] = useState(false);
  const [candidateNext, setCandidateNext] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [creationCandidate, setCreationCandidate] = useState<PlatformProviderModelCandidate>();
  const [probeTarget, setProbeTarget] = useState<PlatformModelDeploymentSummary>();
  const [publishTarget, setPublishTarget] = useState<PlatformModelDeploymentSummary>();
  const [disableTarget, setDisableTarget] = useState<PlatformModelDeploymentSummary>();
  const [submitting, setSubmitting] = useState(false);

  const loadDeployments = useCallback(async () => {
    if (!client.hasSession()) { setViewState("unauthenticated"); return; }
    setViewState("loading"); setFailure(undefined);
    try {
      const page = await client.listPlatformModelDeployments(route.deploymentPage);
      setDeployments(page.items); setDeploymentTotal(page.totalElements);
      setDeploymentPrevious(page.hasPrevious); setDeploymentNext(page.hasNext); setViewState("ready");
    } catch (error) { setFailure(toFailure(error)); setViewState("error"); }
  }, [client, route.deploymentPage]);

  const loadCandidates = useCallback(async () => {
    if (route.connectionId === undefined || route.connectionVersion === undefined || !client.hasSession()) {
      setCandidates([]); setCandidateTotal(0); setCandidatePrevious(false); setCandidateNext(false); return;
    }
    try {
      const page = await client.listPlatformProviderModelCandidates(
        route.connectionId, route.connectionVersion, route.candidatePage,
      );
      setCandidates(page.items); setCandidateTotal(page.totalElements);
      setCandidatePrevious(page.hasPrevious); setCandidateNext(page.hasNext);
    } catch (error) { setFailure(toFailure(error)); }
  }, [client, route.candidatePage, route.connectionId, route.connectionVersion]);

  useEffect(() => { const timer = window.setTimeout(() => void loadDeployments(), 0); return () => window.clearTimeout(timer); }, [loadDeployments, sessionRevision]);
  useEffect(() => { const timer = window.setTimeout(() => void loadCandidates(), 0); return () => window.clearTimeout(timer); }, [loadCandidates, sessionRevision]);

  async function createDeployment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creationCandidate === undefined || route.connectionId === undefined || route.connectionVersion === undefined) return;
    const values = new FormData(event.currentTarget);
    const displayName = String(values.get("displayName") ?? "").trim();
    const contextWindow = Number(values.get("contextWindow"));
    const outputLimit = creationCandidate.modelType === "CHAT" ? Number(values.get("outputLimit")) : null;
    const embeddingDimension = creationCandidate.modelType === "EMBEDDING" ? Number(values.get("embeddingDimension")) : null;
    if (!displayName || !Number.isInteger(contextWindow) || contextWindow < 1
      || (outputLimit !== null && (!Number.isInteger(outputLimit) || outputLimit < 1))
      || (embeddingDimension !== null && (!Number.isInteger(embeddingDimension) || embeddingDimension < 1))) {
      setFailure({ message: "请填写有效的部署名称、上下文窗口及当前模型类型所需参数。", traceId: "" }); return;
    }
    setSubmitting(true); setFailure(undefined);
    try {
      await client.createPlatformModelDeployment({
        providerConnectionId: route.connectionId, expectedConnectionVersion: route.connectionVersion,
        modelCode: creationCandidate.modelCode, displayName, modelType: creationCandidate.modelType,
        capabilities: creationCandidate.capabilities, contextWindow, outputLimit, embeddingDimension,
      });
      setCreationCandidate(undefined); await loadDeployments();
    } catch (error) { setFailure(toFailure(error)); } finally { setSubmitting(false); }
  }

  async function probeDeployment() {
    if (probeTarget === undefined) return;
    setSubmitting(true); setFailure(undefined);
    try { await client.probePlatformModelDeployment(probeTarget.id, probeTarget.version, PROBE_TIMEOUT_MILLIS); setProbeTarget(undefined); await loadDeployments(); }
    catch (error) { setFailure(toFailure(error)); } finally { setSubmitting(false); }
  }
  async function publishDeployment() {
    if (publishTarget === undefined) return;
    setSubmitting(true); setFailure(undefined);
    try { await client.publishPlatformModelDeployment(publishTarget.id, publishTarget.version); setPublishTarget(undefined); await loadDeployments(); }
    catch (error) { setFailure(toFailure(error)); } finally { setSubmitting(false); }
  }
  async function disableDeployment() {
    if (disableTarget === undefined) return;
    setSubmitting(true); setFailure(undefined);
    try { await client.disablePlatformModelDeployment(disableTarget.id, disableTarget.version); setDisableTarget(undefined); await loadDeployments(); }
    catch (error) { setFailure(toFailure(error)); } finally { setSubmitting(false); }
  }

  if (viewState === "unauthenticated") return <StatePanel kind="stale" title="浏览器会话已经失效" description="平台模型部署摘要已清除，请重新登录。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  if (viewState === "loading") return <StatePanel kind="loading" title="正在加载平台模型部署" description="正在从服务端读取部署与当前发现候选。" />;
  if (viewState === "error") return <StatePanel kind="error" title="平台模型部署暂时不可用" description={failureDescription(failure)} actionLabel="重新加载" onAction={() => void loadDeployments()} />;

  return <div className="model-configuration-workspace">
    <div className="governance-toolbar"><Text>候选、部署和验证状态均由服务端分页读取；Endpoint、凭据及厂商原始响应不会进入浏览器。</Text><div className="task-actions"><Button appearance="secondary" onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "connections", page: 1 })}>返回平台连接</Button><Button appearance="subtle" onClick={() => { void loadDeployments(); void loadCandidates(); }}>刷新</Button></div></div>
    {failure === undefined ? null : <p role="alert">{failureDescription(failure)}</p>}
    <section className="model-configuration-section" aria-label="平台当前发现候选"><header className="task-heading"><Title3>当前发现候选</Title3><Text>{route.connectionId === undefined ? "请从平台连接页面选择已验证连接。" : "只显示指定连接当前修订的未过期候选。"}</Text></header>
      {route.connectionId === undefined ? null : <><div className="member-directory-table" role="region" aria-label="平台模型候选分页表格"><table><thead><tr><th>模型代码</th><th>类型</th><th>能力</th><th>操作</th></tr></thead><tbody>{candidates.length === 0 ? <tr><td colSpan={4}>当前页没有可创建部署的候选；请重新执行模型发现。</td></tr> : candidates.map((candidate) => <tr key={`${candidate.modelCode}:${candidate.modelType}`}><td>{candidate.modelCode}</td><td>{candidate.modelType}</td><td>{candidate.capabilities.join("、")}</td><td><Button appearance="primary" disabled={submitting} onClick={() => setCreationCandidate(candidate)}>新建草稿部署</Button></td></tr>)}</tbody></table></div><Pagination label="当前发现候选" page={route.candidatePage} total={candidateTotal} hasPrevious={candidatePrevious} hasNext={candidateNext} onPrevious={() => onRouteChange({ ...route, candidatePage: route.candidatePage - 1 })} onNext={() => onRouteChange({ ...route, candidatePage: route.candidatePage + 1 })} /></>}
    </section>
    <section className="model-configuration-section" aria-label="平台模型部署目录"><header className="task-heading"><Title3>平台模型部署</Title3><Text>草稿必须完成最小真实能力探测；仅未过期验证可发布，停用会阻断新目录发布和调用。</Text></header><div className="member-directory-table" role="region" aria-label="平台模型部署分页表格"><table><thead><tr><th>部署</th><th>模型与连接</th><th>状态</th><th>可用性</th><th>验证有效期</th><th>操作</th></tr></thead><tbody>{deployments.length === 0 ? <tr><td colSpan={6}>当前页没有平台模型部署。</td></tr> : deployments.map((deployment) => <tr key={deployment.id}><td>{deployment.displayName}</td><td>{deployment.modelCode} · {deployment.modelType}<br /><Text size={200}>连接 {deployment.providerConnectionId}，修订 {deployment.providerConnectionRevision}</Text></td><td><Badge appearance="tint" color={deployment.status === "PUBLISHED" ? "success" : deployment.status === "VERIFIED" ? "brand" : deployment.status === "DISABLED" ? "danger" : "warning"}>{deployment.status}</Badge></td><td><Badge appearance="tint" color={deployment.availability === "HEALTHY" ? "success" : deployment.availability === "DEGRADED" ? "warning" : "danger"}>{deployment.availability}</Badge><br /><Text size={200}>建议：{nextStepLabel(deployment.nextStep)}</Text></td><td>{deployment.verificationExpiresAt === null ? "待真实能力验证" : new Date(deployment.verificationExpiresAt).toLocaleString("zh-CN")}</td><td><div className="task-actions"><Button appearance="secondary" disabled={submitting || deployment.status !== "DRAFT"} onClick={() => setProbeTarget(deployment)}>探测能力</Button><Button appearance="primary" disabled={submitting || deployment.status !== "VERIFIED"} onClick={() => setPublishTarget(deployment)}>发布部署</Button><Button appearance="secondary" disabled={submitting || deployment.status === "DISABLED"} onClick={() => setDisableTarget(deployment)}>停用部署</Button></div></td></tr>)}</tbody></table></div><Pagination label="平台模型部署" page={route.deploymentPage} total={deploymentTotal} hasPrevious={deploymentPrevious} hasNext={deploymentNext} onPrevious={() => onRouteChange({ ...route, deploymentPage: route.deploymentPage - 1 })} onNext={() => onRouteChange({ ...route, deploymentPage: route.deploymentPage + 1 })} /></section>
    <Dialog open={creationCandidate !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setCreationCandidate(undefined); }}><DialogSurface><DialogBody><DialogTitle>新建平台草稿部署</DialogTitle><form onSubmit={(event) => void createDeployment(event)}><DialogContent>{creationCandidate === undefined ? null : <><Text>选择的候选为 {creationCandidate.modelCode}（{creationCandidate.modelType}）。系统会再次确认候选仍属于此连接修订。</Text><Field label="部署名称" required><Input name="displayName" autoFocus disabled={submitting} /></Field><Field label="上下文窗口" required><Input name="contextWindow" type="number" min={1} defaultValue="8192" disabled={submitting} /></Field>{creationCandidate.modelType === "CHAT" ? <Field label="输出上限" required><Input name="outputLimit" type="number" min={1} defaultValue="1024" disabled={submitting} /></Field> : <Field label="嵌入维度" required><Input name="embeddingDimension" type="number" min={1} defaultValue="1024" disabled={submitting} /></Field>}</>}</DialogContent><DialogActions><Button appearance="secondary" type="button" disabled={submitting} onClick={() => setCreationCandidate(undefined)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在创建" : "创建草稿"}</Button></DialogActions></form></DialogBody></DialogSurface></Dialog>
    <ConfirmationDialog open={probeTarget !== undefined} title="确认执行真实能力探测" description={`系统将对 ${probeTarget?.displayName ?? "该部署"} 发起最小真实模型调用。成功验证只在固定有效期内允许发布。`} busy={submitting} action="执行探测" onClose={() => setProbeTarget(undefined)} onConfirm={() => void probeDeployment()} />
    <ConfirmationDialog open={publishTarget !== undefined} title="确认发布平台模型部署" description={`发布 ${publishTarget?.displayName ?? "该部署"} 前会重新核验连接修订与验证有效期。`} busy={submitting} action="确认发布" onClose={() => setPublishTarget(undefined)} onConfirm={() => void publishDeployment()} />
    <ConfirmationDialog open={disableTarget !== undefined} title="确认停用平台模型部署" description={`停用 ${disableTarget?.displayName ?? "该部署"} 会阻断新目录发布和调用；当前修订不能恢复。`} busy={submitting} action="确认停用" onClose={() => setDisableTarget(undefined)} onConfirm={() => void disableDeployment()} />
  </div>;
}

function ConfirmationDialog({ open, title, description, busy, action, onClose, onConfirm }: { open: boolean; title: string; description: string; busy: boolean; action: string; onClose: () => void; onConfirm: () => void; }) {
  return <Dialog open={open} onOpenChange={(_, data) => { if (!data.open && !busy) onClose(); }}><DialogSurface><DialogBody><DialogTitle>{title}</DialogTitle><DialogContent><Text>{description}</Text></DialogContent><DialogActions><Button appearance="secondary" disabled={busy} onClick={onClose}>取消</Button><Button appearance="primary" disabled={busy} onClick={onConfirm}>{busy ? "正在处理" : action}</Button></DialogActions></DialogBody></DialogSurface></Dialog>;
}
function Pagination({ label, page, total, hasPrevious, hasNext, onPrevious, onNext }: { label: string; page: number; total: number; hasPrevious: boolean; hasNext: boolean; onPrevious: () => void; onNext: () => void; }) {
  return <div className="pagination" aria-label={`${label}分页`}><Text>{label}：第 {page} 页，共 {total} 条</Text><Button appearance="secondary" disabled={!hasPrevious} onClick={onPrevious}>上一页</Button><Button appearance="secondary" disabled={!hasNext} onClick={onNext}>下一页</Button></div>;
}
function failureDescription(failure: Failure | undefined): string { return failure?.traceId ? `${failure.message}（追踪号：${failure.traceId}）` : failure?.message ?? "请求未完成。"; }
function toFailure(error: unknown): Failure { return error instanceof ApiRequestError ? { message: error.message, traceId: error.traceId } : { message: "请求未完成，请稍后重试。", traceId: "" }; }
function nextStepLabel(nextStep: PlatformModelDeploymentSummary["nextStep"]): string { return { VERIFY_CONNECTION: "验证厂商连接", PROBE_MODEL: "执行能力探测", REPROBE_MODEL: "重新执行能力探测", PUBLISH: "发布已验证部署", NONE: "无需操作" }[nextStep]; }
