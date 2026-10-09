import { useEffect, useRef, useState } from "react";
import { Button } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type AccessPolicyAssistantOption, type AccessPolicyInput, type AccessPolicyView, type ControlPlaneClient, type PageResult } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { memberDraftOwner } from "./member-account-draft";
import { clearAuthorizationDraft, loadAuthorizationDraft, observeAuthorizationDrafts, saveAuthorizationDraft } from "./authorization-draft";
import { useAuthorizationDeparture, type RegisterAuthorizationDeparture } from "./AuthorizationDeparture";
import { PublishedAssistantPicker } from "./PublishedAssistantPicker";
import { PolicyMemberPicker } from "./PolicyMemberPicker";
import { policyExpected, policyInput, policyReadFailure, policyRoute, policyStatusLabel } from "./access-policy-state";

const previewLabels = ["已选活动普通成员可以看到并使用助手", "未选成员不能通过目录或直接路由使用助手", "引用访问沿用同一助手授权范围", "权限撤销后历史和引用立即失败关闭"];

/** 草稿、预览和发布分别执行；历史版本不能重放成当前授权。 */
export function AccessPolicyWorkspace({ client, route, onRouteChange, registerDeparture }: {
  client: ControlPlaneClient; route: Extract<ConsoleRoute, { task: "policy-detail" }>; onRouteChange: (route: ConsoleRoute) => void; registerDeparture: RegisterAuthorizationDeparture;
}) {
  const [facts, setFacts] = useState<AccessPolicyView>(); const [draft, setDraft] = useState<AccessPolicyInput>();
  const [assistantFact, setAssistant] = useState<{ id: string; value: AccessPolicyAssistantOption }>(); const [history, setVersions] = useState<{ key: string; value: PageResult<AccessPolicyView> }>();
  const [historyPage, setHistoryPage] = useState(1); const [failure, setFailure] = useState(""); const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false); const [dirty, setDirty] = useState(false); const [reload, setReload] = useState(0);
  const [nextVersion, setNextVersion] = useState<AccessPolicyView>();
  const [readFailure, setReadFailure] = useState<ReturnType<typeof policyReadFailure>>();
  const active = useRef(false); const writing = useRef(false); const owner = useRef<string | undefined>(undefined); const assistantRequest = useRef(0);
  const epoch = client.currentSessionRevision(); const target = `policy:${route.versionId}`;
  const current = () => active.current && client.hasSession() && client.currentSessionRevision() === epoch;
  const editable = facts?.status === "DRAFT";
  const assistant = assistantFact?.id === draft?.assistantId ? assistantFact?.value : undefined;
  const historyKey = `${epoch}:${route.policyId}:${route.versionId}:${facts?.version}:${historyPage}:${reload}`;
  const versions = history?.key === historyKey ? history.value : undefined;
  const departureDialog = useAuthorizationDeparture(client, dirty, busy, registerDeparture, () => { clearAuthorizationDraft(target); setDirty(false); });
  useEffect(() => {
    // 写入保护卸载后再导航；旧 URL 不能提前显示新版本正文或接收其保存命令。
    if (nextVersion && !busy && !dirty && client.hasSession() && client.currentSessionRevision() === epoch) onRouteChange(policyRoute(nextVersion));
  }, [client, epoch, nextVersion, busy, dirty, onRouteChange]);
  useEffect(() => {
    let mounted = true; active.current = true; observeAuthorizationDrafts(client);
    const unsubscribe = client.subscribeSessionChange(isActive => { if (!isActive) { active.current = false; owner.current = undefined; setFacts(undefined); setDraft(undefined); setVersions(undefined); setAssistant(undefined); } });
    Promise.all([client.getAccessPolicy(route.policyId, route.versionId), memberDraftOwner(client)]).then(([value, scope]) => {
      if (!mounted || !active.current || !client.hasSession() || client.currentSessionRevision() !== epoch) return;
      owner.current = scope; setFacts(value); setDraft(policyInput(value)); setFailure(""); setReadFailure(undefined); setFeedback("");
      if (value.status === "DRAFT" && scope) {
        try { const restored = loadAuthorizationDraft(scope, target);
          if (restored?.kind === "policy") { setDraft({ assistantId: restored.assistantId, membershipIds: restored.membershipIds, expectedVersion: restored.expectedVersion, expectedAuthorizationRevision: restored.expectedAuthorizationRevision, expectedTenantFeatureVersion: restored.expectedTenantFeatureVersion }); setDirty(true); setFeedback("已恢复本页未保存修改。"); }
        } catch { setFeedback("无法恢复草稿，当前显示已保存策略。"); }
      }
    }).catch(error => { if (mounted && active.current && client.currentSessionRevision() === epoch) setReadFailure(policyReadFailure(error)); });
    return () => { mounted = false; active.current = false; unsubscribe(); };
  }, [client, epoch, route.policyId, route.versionId, target, reload]);
  useEffect(() => {
    let mounted = true;
    if (!facts) return;
    client.listAccessPolicyVersions(route.policyId, historyPage, 20).then(value => {
      if (mounted && client.hasSession() && client.currentSessionRevision() === epoch) setVersions({ key: historyKey, value });
    }).catch(error => { if (mounted && client.currentSessionRevision() === epoch) setFailure(presentApiError(error, "读取策略历史").description); });
    return () => { mounted = false; };
  }, [client, epoch, route.policyId, historyPage, reload, historyKey, facts]);
  useEffect(() => {
    const id = draft?.assistantId; const request = ++assistantRequest.current; let mounted = true;
    if (!id) return;
    client.accessPolicyAssistant(id).then(value => {
      if (mounted && active.current && client.hasSession() && client.currentSessionRevision() === epoch && request === assistantRequest.current) setAssistant({ id, value });
    }).catch(error => {
      if (mounted && active.current && client.currentSessionRevision() === epoch && request === assistantRequest.current && facts?.status === "DRAFT") setFailure(presentApiError(error, "核对助手与知识库").description);
    });
    return () => { mounted = false; };
  }, [client, epoch, draft?.assistantId, facts?.status]);
  function change(next: AccessPolicyInput) {
    if (!current() || writing.current || !editable) return;
    setDraft(next); setDirty(true); setFeedback(""); setFailure("");
    try { if (!owner.current) throw new Error(); saveAuthorizationDraft(owner.current, target, { kind: "policy", ...next }); }
    catch { setFeedback("修改尚未保存到草稿，刷新会丢失。"); }
  }
  async function execute(command: "save" | "preview" | "publish" | "new-version") {
    if (!current() || writing.current || !facts || !draft) return;
    if (command === "save" && (!draft.assistantId || !draft.membershipIds.length)) { setFailure("请选择已发布助手和至少一名普通成员。"); return; }
    if ((command === "preview" || command === "publish") && dirty) { setFailure("请先保存草稿，再生成授权预览或发布。"); return; }
    writing.current = true; setBusy(true); setFeedback(""); setFailure("");
    try {
      const value = command === "new-version" ? await client.newAccessPolicyVersion(facts)
        : command === "save" ? await client.saveAccessPolicy(route.policyId, route.versionId, draft)
        : await client.accessPolicyCommand(route.policyId, route.versionId, command, policyExpected(facts));
      if (!current()) return;
      setDirty(false);
      try { clearAuthorizationDraft(target); } catch { setFeedback("操作已保存，但草稿清除失败。"); }
      if (command === "new-version") setNextVersion(value);
      else {
        setFacts(value); setDraft(policyInput(value));
        setFeedback(command === "save" ? "策略草稿已保存；请重新生成授权预览。" : command === "preview" ? "允许、拒绝、历史和引用样本预览全部通过。" : `策略 v${value.number} 已发布，成员授权范围已更新。`);
      }
    } catch (error) { if (current()) setFailure(presentApiError(error, "处理策略版本").description); }
    finally { writing.current = false; if (current()) setBusy(false); }
  }
  function refresh() {
    if (!current() || writing.current) return;
    try { clearAuthorizationDraft(target); } catch { setFailure("无法清除草稿，请重试。"); return; }
    setDirty(false); setFacts(undefined); setDraft(undefined); setFailure(""); setReadFailure(undefined); setFeedback(""); setReload(value => value + 1);
  }
  const preview = !dirty && facts?.preview?.passed ? facts.preview : undefined;
  const knowledgeName = editable ? assistant?.knowledgeSpaceName ?? (draft?.assistantId === facts?.assistantId ? facts?.knowledgeSpaceName : "正在核对…") : facts?.knowledgeSpaceName;
  return <>
    <PageHeading title={facts ? `${facts.name} v${facts.number}` : "访问策略版本"} context="访问治理" summary={readFailure?.summary ?? "成员助手授权版本"}
      action={!readFailure ? <Button className="policy-page-action" disabled={busy} onClick={() => onRouteChange({ navigationId: "access-policies", task: "policies", page: 1 })}>返回策略目录</Button> : undefined} />
    {!facts || !draft ? readFailure ? <section className="policy-read-state" data-kind={readFailure.kind} role="alert">
      <p className="policy-read-eyebrow">{readFailure.kind === "denied" ? "无权访问" : "任务失败"}</p><h2>{readFailure.title}</h2><p>{readFailure.description}</p>
      {readFailure.kind === "denied" ? <Button as="a" className="policy-page-action" href="/tenant/login">{readFailure.action}</Button>
        : <Button className="policy-page-action" onClick={refresh}>{readFailure.action}</Button>}
    </section> : <section className="member-workspace"><p role="status">正在读取策略版本…</p></section> : <div className="member-workspace access-policy-workspace" aria-busy={busy}>
      <div className="policy-inline-note">策略只配置普通成员可用的助手；知识库由助手的关联关系确定，不能在这里另选。</div>
      {versions && versions.totalElements > 1 ? <div className="policy-version-navigation"><label className="member-native-field"><span>策略版本</span><select aria-label="策略版本" value={route.versionId} disabled={busy} onChange={event => {
        const value = versions.items.find(item => item.versionId === event.target.value); if (value) onRouteChange(policyRoute(value));
      }}>
        {!versions.items.some(item => item.versionId === route.versionId) ? <option value={route.versionId}>v{facts.number} · {policyStatusLabel(facts.status)}</option> : null}
        {versions.items.map(value => <option key={value.versionId} value={value.versionId}>v{value.number} · {policyStatusLabel(value.status)}</option>)}</select></label>
        {versions.totalPages > 1 ? <><Button disabled={busy || !versions.hasPrevious} onClick={() => setHistoryPage(value => value - 1)}>上一页版本</Button><span>第 {historyPage} / {versions.totalPages} 页</span><Button disabled={busy || !versions.hasNext} onClick={() => setHistoryPage(value => value + 1)}>下一页版本</Button></> : null}
      </div> : null}
      <div className="policy-split-detail">
        <section className="policy-editor">
          <header className="policy-section-heading"><div><span className="policy-kicker">{facts.name}</span><h2>策略 v{facts.number}</h2></div><span className="member-status policy-status" data-tone={facts.status === "PUBLISHED" ? "success" : "info"}>{policyStatusLabel(facts.status)}</span></header>
          <div className="policy-form-grid"><PublishedAssistantPicker client={client} single selected={[draft.assistantId]} selectedName={assistant?.assistantName ?? facts.assistantName} disabled={busy || !editable} onChange={ids => change({ ...draft, assistantId: ids[0]! })} />
            <div className="member-native-field"><span>关联知识库（自动确定）</span><div className="policy-readonly-value">{knowledgeName}</div><small>由“{assistant?.assistantName ?? facts.assistantName}”关联，创建后不可更换。</small></div></div>
          {editable ? <PolicyMemberPicker client={client} selected={draft.membershipIds} disabled={busy} onChange={membershipIds => change({ ...draft, membershipIds })} />
            : <fieldset className="policy-member-options"><legend>授权普通成员</legend><div className="policy-member-grid">{facts.memberNames.map((name, index) => <label key={`${name}:${index}`}><input type="checkbox" checked disabled readOnly /><span><strong>{name}</strong><small>普通成员 · 只获得所选助手的对话与授权引用</small></span></label>)}</div></fieldset>}
          <p className="policy-feedback" role={failure ? "alert" : "status"}>{failure || feedback}</p>
          <div className="member-form-actions"><Button disabled={busy || !editable} onClick={() => void execute("save")}>保存草稿</Button><Button appearance={preview ? "secondary" : "primary"} disabled={busy || !editable || dirty} onClick={() => void execute("preview")}>生成授权预览</Button>
            {failure ? <Button disabled={busy} onClick={refresh}>重新读取策略</Button> : null}</div>
        </section>
        <aside className={`policy-release-decision${preview ? "" : " pending"}`}>
          <span className="policy-kicker">发布前授权预览</span><h2>{preview ? `${preview.allowCount + preview.denyCount} 个样本已通过` : "尚未生成有效预览"}</h2>
          {preview ? <><div className="policy-summary-strip">{[["允许样本", preview.allowCount], ["拒绝样本", preview.denyCount], ["越权泄漏", preview.leakCount], ["预览结果", "通过"]].map(([label, value]) => <dl key={label}><dt>{label}</dt><dd>{value}</dd></dl>)}</div>
            <ul className="policy-timeline">{previewLabels.map(label => <li key={label}><strong>{label}</strong><small>通过</small></li>)}</ul></>
            : <div className="policy-inline-note warning">成员或助手变化后预览会失效。请在发布前核对允许、拒绝、历史与引用四类样本。</div>}
          <Button appearance="primary" disabled={busy || (editable ? !preview : facts.status !== "PUBLISHED")} onClick={() => void execute(editable ? "publish" : "new-version")}>{editable ? "发布策略版本" : "创建策略新版本"}</Button>
          <dl className="member-account-facts"><dt>当前成员</dt><dd>{facts.memberNames.join("、")}</dd><dt>生效范围</dt><dd>助手目录、对话、本人历史和授权引用</dd><dt>撤销结果</dt><dd>直接路由、历史正文与引用立即失败关闭</dd></dl>
        </aside>
      </div>
    </div>}{departureDialog}
  </>;
}
