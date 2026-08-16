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
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type ModelDeployment,
  type ProviderModelCandidate,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type DeploymentRoute = Extract<ConsoleRoute, { task: "deployments" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";
const MODEL_DEPLOYMENT_PROBE_TIMEOUT_MILLIS = 10_000;

interface Failure {
  message: string;
}

/**
 * 模型部署是独立的可恢复任务：候选、草稿、真实能力探测和发布不会与连接列表混在同一页面。
 */
export function ModelDeploymentWorkspace({
  client,
  route,
  sessionRevision,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: DeploymentRoute;
  sessionRevision: number;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [deployments, setDeployments] = useState<ModelDeployment[]>([]);
  const [deploymentTotal, setDeploymentTotal] = useState(0);
  const [deploymentHasPrevious, setDeploymentHasPrevious] = useState(false);
  const [deploymentHasNext, setDeploymentHasNext] = useState(false);
  const [candidates, setCandidates] = useState<ProviderModelCandidate[]>([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [candidateHasPrevious, setCandidateHasPrevious] = useState(false);
  const [candidateHasNext, setCandidateHasNext] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [creationCandidate, setCreationCandidate] = useState<ProviderModelCandidate>();
  const [probeTarget, setProbeTarget] = useState<ModelDeployment>();
  const [publishTarget, setPublishTarget] = useState<ModelDeployment>();
  const [disableTarget, setDisableTarget] = useState<ModelDeployment>();
  const [submitting, setSubmitting] = useState(false);

  const loadDeployments = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const page = await client.listModelDeployments(route.deploymentPage);
      setDeployments(page.items);
      setDeploymentTotal(page.totalElements);
      setDeploymentHasPrevious(page.hasPrevious);
      setDeploymentHasNext(page.hasNext);
      setViewState("ready");
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client, route.deploymentPage]);

  const loadCandidates = useCallback(async () => {
    if (route.connectionId === undefined || !client.hasSession()) {
      setCandidates([]);
      setCandidateTotal(0);
      setCandidateHasPrevious(false);
      setCandidateHasNext(false);
      return;
    }
    try {
      const page = await client.listCurrentProviderModelCandidates(
        route.connectionId,
        route.candidatePage,
      );
      setCandidates(page.items);
      setCandidateTotal(page.totalElements);
      setCandidateHasPrevious(page.hasPrevious);
      setCandidateHasNext(page.hasNext);
    } catch (error) {
      setFailure(toFailure(error));
    }
  }, [client, route.candidatePage, route.connectionId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadDeployments(), 0);
    return () => window.clearTimeout(timer);
  }, [loadDeployments, sessionRevision]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadCandidates(), 0);
    return () => window.clearTimeout(timer);
  }, [loadCandidates, sessionRevision]);

  async function createDeployment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creationCandidate === undefined || route.connectionId === undefined) return;
    const values = new FormData(event.currentTarget);
    const displayName = String(values.get("displayName") ?? "").trim();
    const contextWindow = Number(values.get("contextWindow"));
    const outputLimit = creationCandidate.modelType === "CHAT"
      ? Number(values.get("outputLimit"))
      : null;
    const embeddingDimension = creationCandidate.modelType === "EMBEDDING"
      ? Number(values.get("embeddingDimension"))
      : null;
    if (!displayName || !Number.isInteger(contextWindow) || contextWindow < 1
      || (outputLimit !== null && (!Number.isInteger(outputLimit) || outputLimit < 1))
      || (embeddingDimension !== null && (!Number.isInteger(embeddingDimension) || embeddingDimension < 1))) {
      setFailure({ message: "请填写有效的部署名称、上下文窗口及当前模型类型所需参数。" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.createModelDeployment({
        providerConnectionId: route.connectionId,
        modelCode: creationCandidate.modelCode,
        displayName,
        modelType: creationCandidate.modelType,
        capabilities: creationCandidate.capabilities,
        contextWindow,
        outputLimit,
        embeddingDimension,
      });
      setCreationCandidate(undefined);
      await loadDeployments();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function probeDeployment() {
    if (probeTarget === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.probeModelDeployment(
        probeTarget.id,
        probeTarget.version,
        MODEL_DEPLOYMENT_PROBE_TIMEOUT_MILLIS,
      );
      setProbeTarget(undefined);
      await loadDeployments();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function publishDeployment() {
    if (publishTarget === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.publishModelDeployment(publishTarget.id, publishTarget.version);
      setPublishTarget(undefined);
      await loadDeployments();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function disableDeployment() {
    if (disableTarget === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.disableModelDeployment(disableTarget.id, disableTarget.version);
      setDisableTarget(undefined);
      await loadDeployments();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (viewState === "unauthenticated") {
    return <StatePanel kind="stale" title="浏览器会话已经失效" description="部署和候选状态已清除，请返回统一登录入口。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  if (viewState === "loading") {
    return <StatePanel kind="loading" title="正在加载模型部署" description="正在准备部署状态和可用模型候选。" />;
  }
  if (viewState === "error") {
    return <StatePanel kind="error" title="模型部署暂时不可用" description={failure?.message ?? "请稍后重试。"} actionLabel="重新加载" onAction={() => void loadDeployments()} />;
  }

  return (
    <div className="model-configuration-workspace">
      <div className="governance-toolbar">
        <Text>能力探测会调用真实受控模型；候选、部署和验证状态均会刷新，候选列表不包含历史诊断。</Text>
        <div className="task-actions">
          <Button appearance="secondary" onClick={() => onRouteChange({ navigationId: "models", task: "providers", providerPage: 1, connectionId: route.connectionId, discoveryPage: 1 })}>返回厂商连接</Button>
          <Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => { void loadDeployments(); void loadCandidates(); }}>刷新</Button>
        </div>
      </div>
      {failure === undefined ? null : <p role="alert">{failure.message}</p>}

      <section className="model-configuration-section" aria-label="当前发现候选">
        <header className="task-heading"><Title3>当前发现候选</Title3><Text>{route.connectionId === undefined ? "请先从厂商连接页面选择已验证连接。" : "仅显示当前连接最新且仍可用的模型候选。"}</Text></header>
        {route.connectionId === undefined ? null : <>
          <div className="member-directory-table" role="region" aria-label="当前模型候选分页表格"><table><thead><tr><th>模型名称</th><th>类型</th><th>候选能力</th><th>操作</th></tr></thead><tbody>{candidates.length === 0 ? <tr><td colSpan={4}>当前页没有可用于创建的候选；请重新执行模型发现。</td></tr> : candidates.map((candidate) => <tr key={`${candidate.modelCode}:${candidate.modelType}`}><td>{candidate.modelCode}</td><td>{candidate.modelType === "CHAT" ? "对话" : "嵌入"}</td><td>{candidate.capabilities.join("、")}</td><td><Button appearance="primary" disabled={submitting} onClick={() => setCreationCandidate(candidate)}>新建草稿部署</Button></td></tr>)}</tbody></table></div>
          <Pagination label="当前发现候选" page={route.candidatePage} total={candidateTotal} hasPrevious={candidateHasPrevious} hasNext={candidateHasNext} onPrevious={() => onRouteChange({ ...route, candidatePage: route.candidatePage - 1 })} onNext={() => onRouteChange({ ...route, candidatePage: route.candidatePage + 1 })} />
        </>}
      </section>

      <section className="model-configuration-section" aria-label="模型部署目录">
        <header className="task-heading"><Title3>模型部署</Title3><Text>草稿必须先完成最小真实能力探测；验证有效时才能发布，停用会阻断新调用。</Text></header>
        <div className="member-directory-table" role="region" aria-label="模型部署分页表格"><table><thead><tr><th>部署</th><th>模型</th><th>状态</th><th>可用性</th><th>验证有效期</th><th>操作</th></tr></thead><tbody>{deployments.length === 0 ? <tr><td colSpan={6}>当前页没有模型部署。</td></tr> : deployments.map((deployment) => <tr key={deployment.id}><td>{deployment.displayName}</td><td>{deployment.modelCode} · {deployment.modelType === "CHAT" ? "对话" : "嵌入"}</td><td><Badge appearance="tint" color={deployment.status === "PUBLISHED" ? "success" : deployment.status === "VERIFIED" ? "brand" : deployment.status === "DISABLED" ? "danger" : "warning"}>{deploymentStatusLabel(deployment.status)}</Badge></td><td><Badge appearance="tint" color={deployment.availability === "HEALTHY" ? "success" : deployment.availability === "DEGRADED" ? "warning" : "danger"}>{availabilityLabel(deployment.availability)}</Badge><br /><Text size={200}>建议：{nextStepLabel(deployment.nextStep)}</Text></td><td>{deployment.verificationExpiresAt === null ? "待真实能力验证" : new Date(deployment.verificationExpiresAt).toLocaleString("zh-CN")}</td><td><div className="task-actions"><Button appearance="secondary" disabled={submitting || deployment.status !== "DRAFT"} onClick={() => setProbeTarget(deployment)}>探测能力</Button><Button appearance="primary" disabled={submitting || deployment.status !== "VERIFIED"} onClick={() => setPublishTarget(deployment)}>发布部署</Button><Button appearance="secondary" disabled={submitting || deployment.status === "DISABLED"} onClick={() => setDisableTarget(deployment)}>停用部署</Button></div></td></tr>)}</tbody></table></div>
        <Pagination label="模型部署" page={route.deploymentPage} total={deploymentTotal} hasPrevious={deploymentHasPrevious} hasNext={deploymentHasNext} onPrevious={() => onRouteChange({ ...route, deploymentPage: route.deploymentPage - 1 })} onNext={() => onRouteChange({ ...route, deploymentPage: route.deploymentPage + 1 })} />
      </section>

      <Dialog open={creationCandidate !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setCreationCandidate(undefined); }}><DialogSurface><DialogBody><DialogTitle>新建草稿部署</DialogTitle><form onSubmit={(event) => void createDeployment(event)}><DialogContent>{creationCandidate === undefined ? null : <><Text>选择的模型为 {creationCandidate.modelCode}。系统会在写入时再次确认该候选仍然可用。</Text><Field label="部署名称" required><Input name="displayName" autoFocus disabled={submitting} /></Field><Field label="上下文窗口" required><Input name="contextWindow" type="number" min={1} defaultValue="8192" disabled={submitting} /></Field>{creationCandidate.modelType === "CHAT" ? <Field label="输出上限" required><Input name="outputLimit" type="number" min={1} defaultValue="1024" disabled={submitting} /></Field> : <Field label="嵌入维度" required><Input name="embeddingDimension" type="number" min={1} defaultValue="1024" disabled={submitting} /></Field>}</>}</DialogContent><DialogActions><Button appearance="secondary" type="button" disabled={submitting} onClick={() => setCreationCandidate(undefined)}>取消</Button><Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在创建" : "创建草稿"}</Button></DialogActions></form></DialogBody></DialogSurface></Dialog>

      <Dialog open={probeTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setProbeTarget(undefined); }}><DialogSurface><DialogBody><DialogTitle>确认执行真实能力探测</DialogTitle><DialogContent><Text>系统将对 {probeTarget?.displayName} 发起最小真实模型调用。失败不会发布部署，成功结果仅在固定时间内有效。</Text></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setProbeTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void probeDeployment()}>{submitting ? "正在探测" : "执行探测"}</Button></DialogActions></DialogBody></DialogSurface></Dialog>

      <Dialog open={publishTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setPublishTarget(undefined); }}><DialogSurface><DialogBody><DialogTitle>确认发布模型部署</DialogTitle><DialogContent><Text>发布 {publishTarget?.displayName} 后将对租户任务可用；系统会再次确认连接和能力验证仍然有效。</Text></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setPublishTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void publishDeployment()}>{submitting ? "正在发布" : "确认发布"}</Button></DialogActions></DialogBody></DialogSurface></Dialog>

      <Dialog open={disableTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setDisableTarget(undefined); }}><DialogSurface><DialogBody><DialogTitle>确认停用模型部署</DialogTitle><DialogContent><Text>停用 {disableTarget?.displayName} 会立即阻断新的模型调用，且不能直接恢复；如需重新供应，请新建并验证部署。</Text></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setDisableTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void disableDeployment()}>{submitting ? "正在停用" : "确认停用"}</Button></DialogActions></DialogBody></DialogSurface></Dialog>
    </div>
  );
}

function Pagination({ label, page, total, hasPrevious, hasNext, onPrevious, onNext }: { label: string; page: number; total: number; hasPrevious: boolean; hasNext: boolean; onPrevious: () => void; onNext: () => void }) {
  return <div className="pagination" aria-label={`${label}分页`}><Text>{label}：第 {page} 页，共 {total} 条</Text><Button appearance="secondary" disabled={!hasPrevious} onClick={onPrevious}>上一页</Button><Button appearance="secondary" disabled={!hasNext} onClick={onNext}>下一页</Button></div>;
}

function toFailure(error: unknown): Failure {
  return { message: error instanceof ApiRequestError ? error.message : "服务暂时不可用，请稍后重试。" };
}

function nextStepLabel(nextStep: ModelDeployment["nextStep"]): string {
  return {
    VERIFY_CONNECTION: "验证厂商连接",
    PROBE_MODEL: "执行能力探测",
    REPROBE_MODEL: "重新执行能力探测",
    PUBLISH: "发布已验证部署",
    NONE: "无需操作",
  }[nextStep];
}

function deploymentStatusLabel(status: ModelDeployment["status"]): string {
  return { DRAFT: "草稿", VERIFIED: "已验证", PUBLISHED: "已发布", DISABLED: "已停用" }[status];
}

function availabilityLabel(availability: ModelDeployment["availability"]): string {
  return { HEALTHY: "可用", DEGRADED: "需关注", UNAVAILABLE: "不可用" }[availability];
}
