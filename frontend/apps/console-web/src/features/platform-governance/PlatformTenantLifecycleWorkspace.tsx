import {
  Badge, Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle,
  Field, Input, MessageBar, MessageBarBody, MessageBarTitle, Radio, RadioGroup, Text, Title2, Title3,
} from "@fluentui/react-components";
import { Add20Regular, ArrowLeft20Regular, Checkmark16Regular, PersonAdd20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  presentApiError,
  type ControlPlaneClient,
  type PlatformAccountCandidate,
  type TenantAdministratorSummary,
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
  const [administratorTenant, setAdministratorTenant] = useState<TenantLifecycleSummary>();
  const administratorTrigger = useRef<HTMLButtonElement | null>(null);
  const [tenantCode, setTenantCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [candidateQuery, setCandidateQuery] = useState("");
  const [appliedCandidateQuery, setAppliedCandidateQuery] = useState("");
  const [candidatePage, setCandidatePage] = useState(1);
  const [candidates, setCandidates] = useState<PlatformAccountCandidate[]>([]);
  const [candidateHasPrevious, setCandidateHasPrevious] = useState(false);
  const [candidateHasNext, setCandidateHasNext] = useState(false);
  const [selectedAdministrator, setSelectedAdministrator] = useState<PlatformAccountCandidate>();
  const [administratorCreationOpen, setAdministratorCreationOpen] = useState(false);
  const [administratorLoginName, setAdministratorLoginName] = useState("");
  const [administratorPassword, setAdministratorPassword] = useState("");
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

  async function createTenantAdministrator() {
    const loginName = administratorLoginName.trim();
    if (loginName === "" || administratorPassword === "") return;
    setBusy(true);
    setFailure(undefined);
    try {
      const account = await client.createPlatformAccount({ loginName, password: administratorPassword });
      const candidate: PlatformAccountCandidate = {
        accountReference: account.subjectId,
        loginName: account.loginName,
        status: account.status,
      };
      setCandidates([candidate]);
      setSelectedAdministrator(candidate);
      setCandidateQuery(account.loginName);
      setAppliedCandidateQuery(account.loginName);
      setCandidatePage(1);
      setAdministratorCreationOpen(false);
      setAdministratorLoginName("");
      setSuccess(`租户管理员账户“${account.loginName}”已建立并选中。`);
    } catch (error) {
      setFailure(presentApiError(error, "建立租户管理员账户"));
    } finally {
      setAdministratorPassword("");
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
    setAdministratorCreationOpen(false);
    setAdministratorLoginName("");
    setAdministratorPassword("");
  }

  if (route.task === "create") {
    return <TenantProvisioningFlow
      route={route} busy={busy} failure={failure} tenantCode={tenantCode} displayName={displayName}
      success={success}
      candidateQuery={candidateQuery} candidates={candidates} selectedAdministrator={selectedAdministrator}
      administratorCreationOpen={administratorCreationOpen}
      administratorLoginName={administratorLoginName} administratorPassword={administratorPassword}
      candidatePage={candidatePage} candidateHasPrevious={candidateHasPrevious} candidateHasNext={candidateHasNext}
      onTenantCodeChange={setTenantCode} onDisplayNameChange={setDisplayName}
      onCandidateQueryChange={setCandidateQuery}
      onCandidateSearch={() => { setCandidatePage(1); setAppliedCandidateQuery(candidateQuery.trim()); }} onCandidatePageChange={setCandidatePage}
      onAdministratorChange={(reference) => setSelectedAdministrator(candidates.find((candidate) => candidate.accountReference === reference))}
      onAdministratorCreationOpenChange={setAdministratorCreationOpen}
      onAdministratorLoginNameChange={setAdministratorLoginName}
      onAdministratorPasswordChange={setAdministratorPassword}
      onAdministratorCreate={() => void createTenantAdministrator()}
      onStepChange={(step) => onRouteChange({ navigationId: "platform", task: "create", step })}
      onCancel={() => { resetProvisioning(); setSuccess(""); onRouteChange({ navigationId: "platform", task: "tenants", page: 1 }); }}
      onSubmit={() => void provision()}
    />;
  }

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading task-heading--split">
        <div><Title3>平台租户</Title3><Text>建立、激活和暂停租户；目录仅展示业务名称、编码与生命周期状态。</Text></div>
        <Button appearance="primary" onClick={() => { setSuccess(""); onRouteChange({ navigationId: "platform", task: "create", step: 1 }); }} disabled={busy}>建立租户</Button>
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
            <div className="tenant-lifecycle-list__actions">
              <Button appearance="subtle" disabled={busy} onClick={(event) => {
                administratorTrigger.current = event.currentTarget;
                setAdministratorTenant(lifecycle);
              }}>管理租户管理员</Button>
              <Button appearance="secondary" disabled={busy} onClick={() => setTransitionTarget({ lifecycle, action })}>{transitionLabel(action)}</Button>
            </div>
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
      <TenantAdministratorDialog
        key={administratorTenant?.tenantId ?? "closed"}
        client={client}
        tenant={administratorTenant}
        onClose={() => {
          setAdministratorTenant(undefined);
          window.requestAnimationFrame(() => administratorTrigger.current?.focus());
        }}
        onChanged={(message) => setSuccess(message)}
      />
    </section>
  );
}

function TenantAdministratorDialog({ client, tenant, onClose, onChanged }: {
  client: ControlPlaneClient;
  tenant?: TenantLifecycleSummary;
  onClose: () => void;
  onChanged: (message: string) => void;
}) {
  const [administrators, setAdministrators] = useState<TenantAdministratorSummary[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<UserFacingError>();
  const [notice, setNotice] = useState("");
  const [editorTarget, setEditorTarget] = useState<TenantAdministratorSummary | null>();
  const [candidateQuery, setCandidateQuery] = useState("");
  const [appliedCandidateQuery, setAppliedCandidateQuery] = useState("");
  const [candidatePage, setCandidatePage] = useState(1);
  const [candidates, setCandidates] = useState<PlatformAccountCandidate[]>([]);
  const [candidateHasPrevious, setCandidateHasPrevious] = useState(false);
  const [candidateHasNext, setCandidateHasNext] = useState(false);
  const [selectedCandidate, setSelectedCandidate] = useState<PlatformAccountCandidate>();
  const [pendingAction, setPendingAction] = useState<{
    administrator: TenantAdministratorSummary;
    action: "disable" | "restore";
  }>();

  const loadAdministrators = useCallback(async () => {
    if (tenant === undefined) return;
    setLoading(true);
    setFailure(undefined);
    try {
      const result = await client.listTenantAdministrators(tenant.tenantId, page, 10);
      setAdministrators(result.items);
      setTotal(result.totalElements);
      setHasPrevious(result.hasPrevious);
      setHasNext(result.hasNext);
    } catch (error) {
      setFailure(presentApiError(error, "加载租户管理员"));
      setAdministrators([]);
      setTotal(0);
      setHasPrevious(false);
      setHasNext(false);
    } finally {
      setLoading(false);
    }
  }, [client, page, tenant]);

  const loadAdministratorCandidates = useCallback(async () => {
    if (tenant === undefined || editorTarget === undefined) return;
    setLoading(true);
    setFailure(undefined);
    try {
      const result = await client.listTenantAdministratorCandidates(
        tenant.tenantId, appliedCandidateQuery, candidatePage, 10,
      );
      setCandidates(result.items);
      setCandidateHasPrevious(result.hasPrevious);
      setCandidateHasNext(result.hasNext);
    } catch (error) {
      setFailure(presentApiError(error, "加载管理员候选"));
      setCandidates([]);
      setCandidateHasPrevious(false);
      setCandidateHasNext(false);
    } finally {
      setLoading(false);
    }
  }, [appliedCandidateQuery, candidatePage, client, editorTarget, tenant]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadAdministrators(), 0);
    return () => window.clearTimeout(timeout);
  }, [loadAdministrators]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadAdministratorCandidates(), 0);
    return () => window.clearTimeout(timeout);
  }, [loadAdministratorCandidates]);

  function openCandidateEditor(target: TenantAdministratorSummary | null) {
    setEditorTarget(target);
    setCandidateQuery("");
    setAppliedCandidateQuery("");
    setCandidatePage(1);
    setCandidates([]);
    setSelectedCandidate(undefined);
    setPendingAction(undefined);
    setFailure(undefined);
  }

  async function commitCandidate() {
    if (tenant === undefined || editorTarget === undefined || selectedCandidate === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      if (editorTarget === null) {
        await client.assignTenantAdministrator(tenant.tenantId, selectedCandidate.accountReference);
        setNotice(`“${selectedCandidate.loginName}”已成为租户管理员。`);
        onChanged(`租户“${tenant.displayName}”已分配管理员“${selectedCandidate.loginName}”。`);
      } else {
        await client.replaceTenantAdministrator(
          tenant.tenantId,
          editorTarget.membershipReference,
          editorTarget.version,
          selectedCandidate.accountReference,
        );
        setNotice(`已由“${selectedCandidate.loginName}”接替“${editorTarget.loginName}”。`);
        onChanged(`租户“${tenant.displayName}”已完成管理员替换。`);
      }
      setEditorTarget(undefined);
      setSelectedCandidate(undefined);
      await loadAdministrators();
    } catch (error) {
      setFailure(presentApiError(error, editorTarget === null ? "分配租户管理员" : "替换租户管理员"));
    } finally {
      setSubmitting(false);
    }
  }

  async function commitStatusChange() {
    if (tenant === undefined || pendingAction === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    const { administrator, action } = pendingAction;
    try {
      await (action === "disable"
        ? client.disableTenantAdministrator(
          tenant.tenantId, administrator.membershipReference, administrator.version,
        )
        : client.restoreTenantAdministrator(
          tenant.tenantId, administrator.membershipReference, administrator.version,
        ));
      setPendingAction(undefined);
      setNotice(`“${administrator.loginName}”已${action === "disable" ? "停用" : "恢复"}。`);
      onChanged(`租户“${tenant.displayName}”的管理员状态已更新。`);
      await loadAdministrators();
    } catch (error) {
      setPendingAction(undefined);
      setFailure(presentApiError(error, action === "disable" ? "停用租户管理员" : "恢复租户管理员"));
    } finally {
      setSubmitting(false);
    }
  }

  return <Dialog
    open={tenant !== undefined}
    onOpenChange={(_, data) => { if (!data.open && !submitting) onClose(); }}
  >
    <DialogSurface className="tenant-administrator-dialog">
      <DialogBody>
        <DialogTitle>租户管理员治理</DialogTitle>
        <DialogContent className="tenant-administrator-dialog__content">
          <header className="tenant-administrator-dialog__heading">
            <div>
              <Text className="tenant-administrator-dialog__eyebrow">人员边界 · 平台独占</Text>
              <Title2>{tenant?.displayName ?? "租户"}</Title2>
              <Text>{tenant?.tenantCode} · 租户管理员只能管理本租户普通成员，不能取得平台治理权限。</Text>
            </div>
            <div className="tenant-administrator-dialog__metric" aria-label={`共 ${total} 位租户管理员`}>
              <strong>{total}</strong><span>位管理员</span>
            </div>
          </header>
          {notice ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>变更已生效</MessageBarTitle>{notice}</MessageBarBody></MessageBar> : null}
          {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
          <div className="tenant-administrator-dialog__workspace">
            <section className="tenant-administrator-directory" aria-label="当前租户管理员">
              <div className="tenant-administrator-directory__toolbar">
                <div><Title3>当前管理员</Title3><Text>状态变更会立即撤销该人员的旧平台与租户会话。</Text></div>
                <Button appearance="primary" icon={<PersonAdd20Regular />} disabled={submitting} onClick={() => openCandidateEditor(null)}>分配管理员</Button>
              </div>
              {loading && administrators.length === 0 ? <StatePanel kind="loading" title="正在加载管理员" description="正在读取当前服务器页。" /> : null}
              {!loading && administrators.length === 0 && failure === undefined ? <StatePanel kind="empty" title="没有可显示的管理员" description="租户建立流程应至少保留一位启用管理员，请刷新后重试。" /> : null}
              <div className="tenant-administrator-directory__list">
                {administrators.map((administrator) => <article key={administrator.membershipReference} className="tenant-administrator-card">
                  <div className="tenant-administrator-card__identity">
                    <span aria-hidden="true">{administrator.loginName.slice(0, 1).toUpperCase()}</span>
                    <div><strong>{administrator.loginName}</strong><Text>加入于 {new Date(administrator.createdAt).toLocaleDateString("zh-CN")}</Text></div>
                  </div>
                  <Badge appearance="tint" color={administrator.status === "ENABLED" ? "success" : "warning"}>{administrator.status === "ENABLED" ? "已启用" : "已停用"}</Badge>
                  <div className="tenant-administrator-card__actions">
                    <Button appearance="subtle" disabled={submitting} onClick={() => openCandidateEditor(administrator)}>替换</Button>
                    <Button appearance="secondary" disabled={submitting} onClick={() => setPendingAction({ administrator, action: administrator.status === "ENABLED" ? "disable" : "restore" })}>{administrator.status === "ENABLED" ? "停用" : "恢复"}</Button>
                  </div>
                </article>)}
              </div>
              <div className="tenant-administrator-directory__pagination" aria-label="租户管理员目录分页">
                <Text>第 {page} 页，共 {total} 位</Text>
                <div><Button appearance="subtle" disabled={!hasPrevious || loading || submitting} onClick={() => setPage(page - 1)}>上一页</Button><Button appearance="subtle" disabled={!hasNext || loading || submitting} onClick={() => setPage(page + 1)}>下一页</Button></div>
              </div>
            </section>
            {editorTarget !== undefined ? <aside className="tenant-administrator-candidates" aria-label={editorTarget === null ? "分配租户管理员" : "替换租户管理员"}>
              <div className="tenant-administrator-candidates__heading"><Text>{editorTarget === null ? "分配新管理员" : "原子替换"}</Text><Title3>{editorTarget === null ? "选择人员账户" : `接替“${editorTarget.loginName}”`}</Title3><Text>{editorTarget === null ? "可提升当前普通成员，也可选择尚未加入本租户的活动账户。" : "系统会先分配新管理员，再停用原管理员，不会出现无人管理的中间状态。"}</Text></div>
              <form className="tenant-administrator-candidates__search" onSubmit={(event) => { event.preventDefault(); setCandidatePage(1); setAppliedCandidateQuery(candidateQuery.trim()); }}>
                <Field label="搜索登录名"><Input autoFocus value={candidateQuery} placeholder="输入账户名称" onChange={(_, data) => setCandidateQuery(data.value)} /></Field>
                <Button type="submit" appearance="secondary" disabled={loading || submitting}>搜索</Button>
              </form>
              <RadioGroup className="tenant-administrator-candidates__list" value={selectedCandidate?.accountReference} onChange={(_, data) => setSelectedCandidate(candidates.find((candidate) => candidate.accountReference === data.value))}>
                {candidates.length === 0 ? <Text>{loading ? "正在加载候选…" : "没有符合条件的活动账户。"}</Text> : candidates.map((candidate) => <Radio key={candidate.accountReference} value={candidate.accountReference} label={<span><strong>{candidate.loginName}</strong><small>可分配</small></span>} />)}
              </RadioGroup>
              <div className="tenant-administrator-candidates__pagination"><Button appearance="subtle" disabled={!candidateHasPrevious || loading || submitting} onClick={() => setCandidatePage(candidatePage - 1)}>上一页</Button><Text>第 {candidatePage} 页</Text><Button appearance="subtle" disabled={!candidateHasNext || loading || submitting} onClick={() => setCandidatePage(candidatePage + 1)}>下一页</Button></div>
              <div className="tenant-administrator-candidates__actions"><Button appearance="secondary" disabled={submitting} onClick={() => setEditorTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting || selectedCandidate === undefined} onClick={() => void commitCandidate()}>{submitting ? "正在提交" : editorTarget === null ? "确认分配" : "确认替换"}</Button></div>
            </aside> : <aside className="tenant-administrator-boundary">
              <Text>治理边界</Text><Title3>平台管管理员，租户管普通成员</Title3><ul><li>平台创建、替换、停用和恢复租户管理员。</li><li>租户管理员只管理本租户普通成员与业务授权。</li><li>活动平台管理员不会进入租户管理员候选。</li></ul>
            </aside>}
          </div>
          {pendingAction ? <div className="tenant-administrator-confirmation" role="alert">
            <div><strong>确认{pendingAction.action === "disable" ? "停用" : "恢复"}“{pendingAction.administrator.loginName}”</strong><Text>{pendingAction.action === "disable" ? "旧会话会立即失效；若这是最后一位启用管理员，服务端将拒绝操作。" : "系统会重新检查该账户没有活动平台管理员身份。"}</Text></div>
            <div><Button appearance="secondary" disabled={submitting} onClick={() => setPendingAction(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void commitStatusChange()}>{submitting ? "正在提交" : "确认变更"}</Button></div>
          </div> : null}
        </DialogContent>
        <DialogActions><Button appearance="secondary" disabled={submitting} onClick={onClose}>关闭</Button></DialogActions>
      </DialogBody>
    </DialogSurface>
  </Dialog>;
}

function TenantProvisioningFlow(props: {
  route: Extract<TenantRoute, { task: "create" }>;
  busy: boolean; failure?: UserFacingError; success: string; tenantCode: string; displayName: string;
  candidateQuery: string; candidates: PlatformAccountCandidate[]; selectedAdministrator?: PlatformAccountCandidate;
  administratorCreationOpen: boolean; administratorLoginName: string; administratorPassword: string;
  candidatePage: number; candidateHasPrevious: boolean; candidateHasNext: boolean;
  onTenantCodeChange: (value: string) => void; onDisplayNameChange: (value: string) => void;
  onCandidateQueryChange: (value: string) => void; onCandidateSearch: () => void; onCandidatePageChange: (page: number) => void;
  onAdministratorChange: (reference: string) => void; onStepChange: (step: 1 | 2 | 3) => void;
  onAdministratorCreationOpenChange: (open: boolean) => void;
  onAdministratorLoginNameChange: (value: string) => void; onAdministratorPasswordChange: (value: string) => void;
  onAdministratorCreate: () => void;
  onCancel: () => void; onSubmit: () => void;
}) {
  const step = props.route.step;
  const infoReady = /^[a-z][a-z0-9-]{2,62}$/.test(props.tenantCode.trim()) && props.displayName.trim().length > 0;
  const administratorReady = props.administratorLoginName.trim() !== "" && props.administratorPassword !== "";
  const steps = [
    { label: "租户信息", description: "定义业务名称" },
    { label: "租户管理员", description: "建立独立管理身份" },
    { label: "确认建立", description: "核对并提交" },
  ];
  return (
    <section className="tenant-provisioning" aria-live="polite">
      <Button className="tenant-provisioning__back" appearance="subtle" icon={<ArrowLeft20Regular />} onClick={props.onCancel}>返回租户目录</Button>
      <div className="tenant-provisioning__layout">
        <aside className="tenant-provisioning__rail">
          <Text className="tenant-provisioning__eyebrow">新租户入驻</Text>
          <Title2>建立租户</Title2>
          <Text>完成三项必要信息后，租户将进入准备中状态。</Text>
          <ol className="tenant-provisioning__steps" aria-label="建立租户步骤">
            {steps.map((item, index) => {
              const stepNumber = index + 1;
              return <li key={item.label} className={step === stepNumber ? "is-current" : step > stepNumber ? "is-complete" : undefined} aria-current={step === stepNumber ? "step" : undefined}>
                <span>{step > stepNumber ? <Checkmark16Regular /> : stepNumber}</span>
                <div><strong>{item.label}</strong><small>{item.description}</small></div>
              </li>;
            })}
          </ol>
        </aside>
        <div className="tenant-provisioning__stage">
          {props.success ? <MessageBar intent="success"><MessageBarBody><MessageBarTitle>账户已准备</MessageBarTitle>{props.success}</MessageBarBody></MessageBar> : null}
          {props.failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{props.failure.title}</MessageBarTitle>{props.failure.description}</MessageBarBody></MessageBar> : null}
          {step === 1 ? <form className="tenant-provisioning__panel" onSubmit={(event: FormEvent) => { event.preventDefault(); if (infoReady) props.onStepChange(2); }}>
            <div className="tenant-provisioning__panel-heading"><Text>步骤 1 / 3</Text><Title3>定义租户信息</Title3><Text>名称用于界面识别，编码用于稳定业务地址。建立后编码不可修改。</Text></div>
            <div className="tenant-provisioning__fields">
              <Field label="租户名称" required><Input size="large" autoFocus value={props.displayName} placeholder="例如：演示门店" onChange={(_, data) => props.onDisplayNameChange(data.value)} /></Field>
              <Field label="租户编码" required validationMessage={props.tenantCode && !/^[a-z][a-z0-9-]{2,62}$/.test(props.tenantCode.trim()) ? "使用小写字母开头，并只包含小写字母、数字和连字符。" : undefined}><Input size="large" value={props.tenantCode} placeholder="例如：demo-store" onChange={(_, data) => props.onTenantCodeChange(data.value)} /></Field>
            </div>
            <div className="tenant-provisioning__actions"><Button appearance="secondary" type="button" onClick={props.onCancel}>取消</Button><Button appearance="primary" type="submit" disabled={!infoReady}>继续选择管理员</Button></div>
          </form> : step === 2 ? <div className="tenant-provisioning__panel">
            <div className="tenant-provisioning__panel-heading"><Text>步骤 2 / 3</Text><Title3>选择或建立租户管理员</Title3><Text>平台管理员账户不会出现在这里。租户管理员是独立业务身份，只能管理被分配的租户。</Text></div>
            <form className="tenant-provisioning__search" onSubmit={(event) => { event.preventDefault(); props.onCandidateSearch(); }}>
              <Field label="查找现有账户"><Input size="large" value={props.candidateQuery} placeholder="输入登录名" onChange={(_, data) => props.onCandidateQueryChange(data.value)} /></Field>
              <Button type="submit" appearance="secondary" disabled={props.busy}>搜索</Button>
            </form>
            {props.busy && props.candidates.length === 0 ? <StatePanel kind="loading" title="正在查找可用账户" description="请稍候，当前页面结构会保持可见。" /> : props.candidates.length === 0 ? <div className="tenant-provisioning__empty-candidates">
              <PersonAdd20Regular aria-hidden="true" />
              <div><Title3>还没有可用的租户管理员账户</Title3><Text>建立一个专用账户后，系统会自动选中并继续当前流程。</Text></div>
              {!props.administratorCreationOpen ? <Button appearance="primary" icon={<Add20Regular />} onClick={() => props.onAdministratorCreationOpenChange(true)}>建立专用租户管理员账户</Button> : null}
            </div> : <>
              <RadioGroup className="tenant-provisioning__candidates" value={props.selectedAdministrator?.accountReference} onChange={(_, data) => props.onAdministratorChange(data.value)} aria-label="可选租户管理员">
                {props.candidates.map((candidate) => <Radio className="tenant-provisioning__candidate" key={candidate.accountReference} value={candidate.accountReference} label={<span><strong>{candidate.loginName}</strong><Badge appearance="tint" color="success">可用</Badge></span>} />)}
              </RadioGroup>
              <div className="tenant-provisioning__pagination"><Button appearance="subtle" disabled={!props.candidateHasPrevious || props.busy} onClick={() => props.onCandidatePageChange(props.candidatePage - 1)}>上一页</Button><Text>第 {props.candidatePage} 页</Text><Button appearance="subtle" disabled={!props.candidateHasNext || props.busy} onClick={() => props.onCandidatePageChange(props.candidatePage + 1)}>下一页</Button><Button appearance="subtle" icon={<Add20Regular />} onClick={() => props.onAdministratorCreationOpenChange(true)}>建立另一个账户</Button></div>
            </>}
            {props.administratorCreationOpen ? <form className="tenant-provisioning__account-form" onSubmit={(event) => { event.preventDefault(); if (administratorReady) props.onAdministratorCreate(); }}>
              <div><Title3>建立专用租户管理员账户</Title3><Text>该账户不授予任何平台管理权限；租户建立时只获得当前租户的管理员身份。</Text></div>
              <div className="tenant-provisioning__account-fields">
                <Field label="登录名" required><Input size="large" autoFocus value={props.administratorLoginName} placeholder="例如：demo.admin" autoComplete="username" onChange={(_, data) => props.onAdministratorLoginNameChange(data.value)} /></Field>
                <Field label="初始口令" hint="请通过安全渠道交给租户管理员，登录后可再重置。" required><Input size="large" type="password" value={props.administratorPassword} autoComplete="new-password" onChange={(_, data) => props.onAdministratorPasswordChange(data.value)} /></Field>
              </div>
              <div className="task-actions"><Button appearance="secondary" type="button" disabled={props.busy} onClick={() => props.onAdministratorCreationOpenChange(false)}>取消</Button><Button appearance="primary" type="submit" disabled={props.busy || !administratorReady}>{props.busy ? "正在建立" : "建立并选中"}</Button></div>
            </form> : null}
            <div className="tenant-provisioning__actions"><Button appearance="secondary" disabled={props.busy} onClick={() => props.onStepChange(1)}>上一步</Button><Button appearance="primary" disabled={props.busy || props.selectedAdministrator === undefined} onClick={() => props.onStepChange(3)}>继续确认</Button></div>
          </div> : props.selectedAdministrator === undefined || !infoReady ? <StatePanel kind="stale" title="建立信息尚未准备完整" description="请返回第一步填写租户信息，并在第二步选择一个可用账户。" actionLabel="返回第一步" onAction={() => props.onStepChange(1)} /> : <div className="tenant-provisioning__panel">
            <div className="tenant-provisioning__panel-heading"><Text>步骤 3 / 3</Text><Title3>核对建立信息</Title3><Text>提交后租户进入准备中状态；确认无误后再从租户目录激活。</Text></div>
            <dl className="tenant-provisioning__review"><div><dt>租户名称</dt><dd>{props.displayName.trim()}</dd></div><div><dt>租户编码</dt><dd>{props.tenantCode.trim()}</dd></div><div><dt>首位租户管理员</dt><dd>{props.selectedAdministrator.loginName}</dd></div><div><dt>建立后状态</dt><dd><Badge appearance="tint" color="warning">准备中</Badge></dd></div></dl>
            <div className="tenant-provisioning__notice"><strong>建立后会发生什么</strong><Text>系统创建租户边界，并只把该账户加入当前租户。平台管理员仍不能查看租户内容。</Text></div>
            <div className="tenant-provisioning__actions"><Button appearance="secondary" disabled={props.busy} onClick={() => props.onStepChange(2)}>上一步</Button><Button appearance="primary" disabled={props.busy} onClick={props.onSubmit}>{props.busy ? "正在建立" : "确认建立租户"}</Button></div>
          </div>}
        </div>
      </div>
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
