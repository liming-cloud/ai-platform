import {
  Badge, Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Field, Input, MessageBar, MessageBarBody, MessageBarTitle, Radio, RadioGroup, Text, Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  presentApiError,
  type ControlPlaneClient,
  type PlatformAccountCandidate,
  type TenantLifecycleSummary,
  type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type TenantRoute = Extract<ConsoleRoute, { navigationId: "platform" }>;
type TenantAction = "activate" | "suspend" | "restore";
type TransitionTarget = { lifecycle: TenantLifecycleSummary; action: TenantAction };

/** 平台管理员从账户目录选择初始管理员，并在独立三步页面建立租户。 */
export function PlatformTenantLifecycleWorkspace({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: TenantRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [lifecycles, setLifecycles] = useState<TenantLifecycleSummary[]>([]);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<UserFacingError>();
  const [success, setSuccess] = useState("");
  const [transitionTarget, setTransitionTarget] = useState<TransitionTarget>();
  const [tenantCode, setTenantCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [candidateQuery, setCandidateQuery] = useState("");
  const [appliedCandidateQuery, setAppliedCandidateQuery] = useState("");
  const [candidatePage, setCandidatePage] = useState(1);
  const [candidates, setCandidates] = useState<PlatformAccountCandidate[]>([]);
  const [candidateHasPrevious, setCandidateHasPrevious] = useState(false);
  const [candidateHasNext, setCandidateHasNext] = useState(false);
  const [selectedAdministrator, setSelectedAdministrator] = useState<PlatformAccountCandidate>();
  const listPage = route.task === "tenants" ? route.page : 1;
  const creationStep = route.task === "create" ? route.step : 1;

  const reload = useCallback(async () => {
    if (route.task !== "tenants") return;
    setBusy(true);
    setFailure(undefined);
    try {
      const page = await client.listTenantLifecycles(listPage);
      setLifecycles(page.items);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
      setTotal(page.totalElements);
    } catch (error) {
      setFailure(presentApiError(error, "加载租户目录"));
      setLifecycles([]);
      setHasPrevious(false);
      setHasNext(false);
      setTotal(0);
    } finally {
      setBusy(false);
    }
  }, [client, listPage, route.task]);

  const loadCandidates = useCallback(async () => {
    if (route.task !== "create" || creationStep !== 2) return;
    setBusy(true);
    setFailure(undefined);
    try {
      const page = await client.listPlatformAccountCandidates(appliedCandidateQuery, candidatePage, 20);
      setCandidates(page.items);
      setCandidateHasPrevious(page.hasPrevious);
      setCandidateHasNext(page.hasNext);
    } catch (error) {
      setFailure(presentApiError(error, "加载账户候选"));
      setCandidates([]);
    } finally {
      setBusy(false);
    }
  }, [appliedCandidateQuery, candidatePage, client, creationStep, route.task]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void (route.task === "tenants" ? reload() : loadCandidates()), 0);
    return () => window.clearTimeout(timeout);
  }, [loadCandidates, reload, route.task]);

  async function provision() {
    if (selectedAdministrator === undefined) return;
    setBusy(true);
    setFailure(undefined);
    try {
      await client.provisionTenant({
        tenantCode: tenantCode.trim(),
        displayName: displayName.trim(),
        initialAdministratorSubjectId: selectedAdministrator.accountReference,
      });
      setSuccess(`租户“${displayName.trim()}”已建立，等待激活。`);
      resetProvisioning();
      onRouteChange({ navigationId: "platform", task: "tenants", page: 1 });
    } catch (error) {
      setFailure(presentApiError(error, "建立租户"));
    } finally {
      setBusy(false);
    }
  }

  async function transition(lifecycle: TenantLifecycleSummary, action: TenantAction) {
    setBusy(true);
    setFailure(undefined);
    try {
      await (action === "activate"
        ? client.activateTenant(lifecycle.tenantId)
        : action === "suspend"
          ? client.suspendTenant(lifecycle.tenantId)
          : client.restoreTenant(lifecycle.tenantId));
      setTransitionTarget(undefined);
      setSuccess(`租户“${lifecycle.displayName}”已${action === "activate" ? "激活" : action === "suspend" ? "暂停" : "恢复"}。`);
      await reload();
    } catch (error) {
      setFailure(presentApiError(error, transitionLabel(action)));
    } finally {
      setBusy(false);
    }
  }

  function resetProvisioning() {
    setTenantCode("");
    setDisplayName("");
    setCandidateQuery("");
    setAppliedCandidateQuery("");
    setCandidatePage(1);
    setCandidates([]);
    setSelectedAdministrator(undefined);
  }

  if (route.task === "create") {
    return <TenantProvisioningFlow
      route={route} busy={busy} failure={failure} tenantCode={tenantCode} displayName={displayName}
      candidateQuery={candidateQuery} candidates={candidates} selectedAdministrator={selectedAdministrator}
      candidatePage={candidatePage} candidateHasPrevious={candidateHasPrevious} candidateHasNext={candidateHasNext}
      onTenantCodeChange={setTenantCode} onDisplayNameChange={setDisplayName}
      onCandidateQueryChange={setCandidateQuery}
      onCandidateSearch={() => { setCandidatePage(1); setAppliedCandidateQuery(candidateQuery.trim()); }} onCandidatePageChange={setCandidatePage}
      onAdministratorChange={(reference) => setSelectedAdministrator(candidates.find((candidate) => candidate.accountReference === reference))}
      onStepChange={(step) => onRouteChange({ navigationId: "platform", task: "create", step })}
      onCancel={() => { resetProvisioning(); onRouteChange({ navigationId: "platform", task: "tenants", page: 1 }); }}
      onSubmit={() => void provision()}
    />;
  }

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading task-heading--split">
        <div><Title3>平台租户</Title3><Text>建立、激活和暂停租户；目录仅展示业务名称、编码与生命周期状态。</Text></div>
        <Button appearance="primary" onClick={() => onRouteChange({ navigationId: "platform", task: "create", step: 1 })} disabled={busy}>建立租户</Button>
      </header>
      {success ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>操作成功</MessageBarTitle>{success}</MessageBarBody></MessageBar> : null}
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
      {busy && lifecycles.length === 0 ? <StatePanel kind="loading" title="正在加载平台租户" description="正在读取当前服务器页的生命周期摘要。" /> : null}
      {!busy && lifecycles.length === 0 && failure === undefined ? <StatePanel kind="empty" title="还没有租户" description="从建立租户开始，选择一个已创建的平台账户作为初始租户管理员。" /> : null}
      <div className="tenant-lifecycle-list">
        {lifecycles.map((lifecycle) => {
          const action = transitionAction(lifecycle);
          return <article key={lifecycle.tenantId}>
            <div><Title3>{lifecycle.displayName}</Title3><Text>{lifecycle.tenantCode} · 最后变更 {new Date(lifecycle.lastChangedAt).toLocaleString("zh-CN")}</Text></div>
            <Badge appearance="tint" color={lifecycle.status === "ACTIVE" ? "success" : lifecycle.status === "SUSPENDED" ? "danger" : "warning"}>{tenantStatusLabel(lifecycle.status)}</Badge>
            <Button appearance="secondary" disabled={busy} onClick={() => setTransitionTarget({ lifecycle, action })}>{transitionLabel(action)}</Button>
          </article>;
        })}
      </div>
      <div className="task-actions" aria-label="平台租户目录分页">
        <Text>第 {route.page} 页，共 {total} 个租户</Text>
        <Button appearance="subtle" disabled={!hasPrevious || busy} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button>
        <Button appearance="subtle" disabled={!hasNext || busy} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button>
      </div>
      <Dialog open={transitionTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !busy) setTransitionTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>确认{transitionTarget === undefined ? "状态变更" : transitionLabel(transitionTarget.action)}</DialogTitle><DialogContent>{transitionTarget === undefined ? "" : `租户“${transitionTarget.lifecycle.displayName}”将${transitionDescription(transitionTarget.action)}。`}</DialogContent><DialogActions><Button appearance="secondary" disabled={busy} onClick={() => setTransitionTarget(undefined)}>取消</Button><Button appearance="primary" disabled={busy} onClick={() => transitionTarget !== undefined && void transition(transitionTarget.lifecycle, transitionTarget.action)}>{busy ? "正在提交" : "确认提交"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
    </section>
  );
}

function TenantProvisioningFlow(props: {
  route: Extract<TenantRoute, { task: "create" }>;
  busy: boolean; failure?: UserFacingError; tenantCode: string; displayName: string;
  candidateQuery: string; candidates: PlatformAccountCandidate[]; selectedAdministrator?: PlatformAccountCandidate;
  candidatePage: number; candidateHasPrevious: boolean; candidateHasNext: boolean;
  onTenantCodeChange: (value: string) => void; onDisplayNameChange: (value: string) => void;
  onCandidateQueryChange: (value: string) => void; onCandidateSearch: () => void; onCandidatePageChange: (page: number) => void;
  onAdministratorChange: (reference: string) => void; onStepChange: (step: 1 | 2 | 3) => void;
  onCancel: () => void; onSubmit: () => void;
}) {
  const step = props.route.step;
  const infoReady = /^[a-z][a-z0-9-]{2,62}$/.test(props.tenantCode.trim()) && props.displayName.trim().length > 0;
  return (
    <section className="tenant-provisioning" aria-live="polite">
      <header><Button appearance="subtle" onClick={props.onCancel}>← 返回租户目录</Button><div><Title3>建立租户</Title3><Text>租户信息 — 租户管理员 — 确认建立</Text></div></header>
      <ol className="tenant-provisioning__steps" aria-label="建立租户步骤">{["租户信息", "租户管理员", "确认建立"].map((label, index) => <li key={label} className={step === index + 1 ? "is-current" : step > index + 1 ? "is-complete" : undefined}><span>{index + 1}</span>{label}</li>)}</ol>
      {props.failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{props.failure.title}</MessageBarTitle>{props.failure.description}</MessageBarBody></MessageBar> : null}
      {step === 1 ? <form className="tenant-provisioning__panel" onSubmit={(event: FormEvent) => { event.preventDefault(); if (infoReady) props.onStepChange(2); }}>
        <div><Title3>租户信息</Title3><Text>使用业务可识别的名称和稳定编码。编码建立后不可修改。</Text></div>
        <Field label="租户名称" required><Input autoFocus value={props.displayName} placeholder="例如：演示门店" onChange={(_, data) => props.onDisplayNameChange(data.value)} /></Field>
        <Field label="租户编码" required validationMessage={props.tenantCode && !/^[a-z][a-z0-9-]{2,62}$/.test(props.tenantCode.trim()) ? "使用小写字母开头，并只包含小写字母、数字和连字符。" : undefined}><Input value={props.tenantCode} placeholder="例如：demo-store" onChange={(_, data) => props.onTenantCodeChange(data.value)} /></Field>
        <div className="task-actions"><Button appearance="secondary" type="button" onClick={props.onCancel}>取消</Button><Button appearance="primary" type="submit" disabled={!infoReady}>下一步</Button></div>
      </form> : step === 2 ? <div className="tenant-provisioning__panel">
        <div><Title3>选择租户管理员</Title3><Text>从平台账户目录选择一个已启用账户作为首位租户管理员。</Text></div>
        <form className="tenant-provisioning__search" onSubmit={(event) => { event.preventDefault(); props.onCandidateSearch(); }}><Field label="搜索登录名"><Input value={props.candidateQuery} placeholder="输入登录名关键词" onChange={(_, data) => props.onCandidateQueryChange(data.value)} /></Field><Button type="submit" appearance="secondary" disabled={props.busy}>搜索</Button></form>
        <RadioGroup value={props.selectedAdministrator?.accountReference} onChange={(_, data) => props.onAdministratorChange(data.value)} aria-label="可选租户管理员">
          {props.candidates.length === 0 ? <Text>{props.busy ? "正在加载账户…" : "没有符合条件的账户，请先在平台账户页面创建并启用账户。"}</Text> : props.candidates.map((candidate) => <Radio key={candidate.accountReference} value={candidate.accountReference} label={<span>{candidate.loginName} <Badge appearance="tint" color={candidate.status === "ACTIVE" ? "success" : "danger"}>{candidate.status === "ACTIVE" ? "已启用" : "已停用"}</Badge></span>} disabled={candidate.status !== "ACTIVE"} />)}
        </RadioGroup>
        <div className="tenant-provisioning__pagination"><Button appearance="subtle" disabled={!props.candidateHasPrevious || props.busy} onClick={() => props.onCandidatePageChange(props.candidatePage - 1)}>上一页</Button><Text>第 {props.candidatePage} 页</Text><Button appearance="subtle" disabled={!props.candidateHasNext || props.busy} onClick={() => props.onCandidatePageChange(props.candidatePage + 1)}>下一页</Button></div>
        <div className="task-actions"><Button appearance="secondary" onClick={() => props.onStepChange(1)}>上一步</Button><Button appearance="primary" disabled={props.selectedAdministrator === undefined} onClick={() => props.onStepChange(3)}>下一步</Button></div>
      </div> : props.selectedAdministrator === undefined || !infoReady ? <StatePanel kind="stale" title="建立信息尚未准备完整" description="请返回第一步填写租户信息，并在第二步选择一个已启用账户。" actionLabel="返回第一步" onAction={() => props.onStepChange(1)} /> : <div className="tenant-provisioning__panel">
        <div><Title3>确认建立</Title3><Text>建立后租户处于准备中，仍需在租户目录明确激活。</Text></div>
        <dl className="tenant-provisioning__review"><div><dt>租户名称</dt><dd>{props.displayName.trim()}</dd></div><div><dt>租户编码</dt><dd>{props.tenantCode.trim()}</dd></div><div><dt>初始租户管理员</dt><dd>{props.selectedAdministrator.loginName}</dd></div><div><dt>初始状态</dt><dd>准备中</dd></div></dl>
        <div className="task-actions"><Button appearance="secondary" disabled={props.busy} onClick={() => props.onStepChange(2)}>上一步</Button><Button appearance="primary" disabled={props.busy} onClick={props.onSubmit}>{props.busy ? "正在建立" : "确认建立"}</Button></div>
      </div>}
    </section>
  );
}

function transitionAction(lifecycle: TenantLifecycleSummary): TenantAction {
  return lifecycle.status === "PROVISIONING" ? "activate" : lifecycle.status === "ACTIVE" ? "suspend" : "restore";
}
function transitionLabel(action: TenantAction) {
  return action === "activate" ? "激活租户" : action === "suspend" ? "暂停租户" : "恢复租户";
}
function transitionDescription(action: TenantAction) {
  return action === "activate" ? "开始提供租户工作区" : action === "suspend" ? "暂停新的租户业务操作，既有数据不会删除" : "恢复提供租户工作区";
}
function tenantStatusLabel(status: TenantLifecycleSummary["status"]) {
  return status === "ACTIVE" ? "活动" : status === "SUSPENDED" ? "暂停" : "准备中";
}
