import { useEffect, useRef, useState } from "react";
import { Button, Dialog, DialogSurface, DialogTitle } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type ControlPlaneClient, type MemberFeatureSettings, type MemberFeatureSettingsInput } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { memberDraftOwner, memberFeatureLabels } from "./member-account-draft";
import { clearMemberFeatureDraft, loadMemberFeatureDraft, observeMemberFeatureDrafts, saveMemberFeatureDraft } from "./member-feature-draft";
import { MemberObjectNavigation } from "./MemberObjectNavigation";

function savedInput(value: MemberFeatureSettings): MemberFeatureSettingsInput {
  return { conversationFeatures: { ...value.conversationFeatures }, expectedFeatureVersion: value.featureVersion, expectedTenantFeatureVersion: value.tenantFeatureVersion };
}

/** 功能页只保存本页完整草稿；异步结果必须仍属于当前登录世代及目标成员。 */
export function MemberFeatureWorkspace({ client, membershipId, onRouteChange, registerDeparture }: {
  client: ControlPlaneClient; membershipId: string; onRouteChange: (route: ConsoleRoute) => void;
  registerDeparture: (guard: (proceed: () => void) => void) => () => void;
}) {
  const [facts, setFacts] = useState<MemberFeatureSettings>();
  const [draft, setDraft] = useState<MemberFeatureSettingsInput>();
  const [failure, setFailure] = useState("");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [departure, setDeparture] = useState<(() => void) | undefined>(undefined);
  const [departureFailure, setDepartureFailure] = useState("");
  const trigger = useRef<HTMLElement | null>(null);
  const owner = useRef<string | undefined>(undefined);
  const active = useRef(false);
  const writing = useRef(false);
  const epoch = client.currentSessionRevision();
  function current() { return active.current && client.hasSession() && epoch === client.currentSessionRevision(); }

  useEffect(() => {
    let mounted = true; active.current = true;
    observeMemberFeatureDrafts(client);
    const unsubscribe = client.subscribeSessionChange(isActive => {
      if (!isActive) { active.current = false; owner.current = undefined; setFacts(undefined); setDraft(undefined); }
    });
    Promise.all([client.getMemberFeatures(membershipId), memberDraftOwner(client)]).then(([value, scope]) => {
      if (!mounted || !active.current || !client.hasSession() || epoch !== client.currentSessionRevision()) return;
      owner.current = scope; setFacts(value); setDraft(savedInput(value)); setFailure(""); setFeedback("");
      if (!value.readOnly && scope) {
        try {
          const restored = loadMemberFeatureDraft(scope, membershipId);
          if (restored) { setDraft(restored); setDirty(true); setFeedback("已恢复本页未保存修改。"); }
        } catch { setFeedback("无法恢复草稿，当前显示已保存配置。"); }
      }
    }).catch(error => {
      if (mounted && active.current && epoch === client.currentSessionRevision()) setFailure(presentApiError(error, "读取对话功能").description);
    });
    return () => { mounted = false; active.current = false; unsubscribe(); };
  }, [client, epoch, membershipId, reload]);

  useEffect(() => {
    if (!dirty && !busy) return;
    return registerDeparture(proceed => {
      if (!active.current || !client.hasSession() || epoch !== client.currentSessionRevision()) return;
      if (writing.current) { setFeedback("正在保存，请稍后离开。"); return; }
      trigger.current = document.activeElement as HTMLElement;
      setDepartureFailure(""); setDeparture(() => proceed);
    });
  }, [busy, client, dirty, epoch, registerDeparture]);

  function change(code: keyof typeof memberFeatureLabels, checked: boolean) {
    if (!current() || writing.current || !draft || facts?.readOnly || !facts?.tenantAvailableFeatures[code]) return;
    const next = { ...draft, conversationFeatures: { ...draft.conversationFeatures, [code]: checked } };
    setDraft(next); setDirty(true); setFeedback(""); setFailure("");
    try {
      if (!owner.current) { setFeedback("修改尚未保存到草稿，刷新会丢失。"); return; }
      saveMemberFeatureDraft(owner.current, membershipId, next);
    }
    catch { setFeedback("修改尚未保存到草稿，刷新会丢失。"); }
  }

  async function save() {
    if (!current() || writing.current || !draft || facts?.readOnly) return;
    writing.current = true; setBusy(true); setFailure(""); setFeedback("");
    try {
      const value = await client.updateMemberFeatures(membershipId, draft);
      if (!current()) return;
      setFacts(value); setDraft(savedInput(value)); setDirty(false);
      try { clearMemberFeatureDraft(membershipId); setFeedback("对话功能已保存。"); }
      catch { setFeedback("配置已保存，但草稿清除失败。"); }
    } catch (error) {
      if (current()) setFailure(presentApiError(error, "保存对话功能").description);
    } finally { writing.current = false; if (current()) setBusy(false); }
  }

  function refreshFacts() {
    if (!current() || writing.current) return;
    try { clearMemberFeatureDraft(membershipId); }
    catch { setFailure("无法清除草稿，请重试。"); return; }
    setFacts(undefined); setDraft(undefined); setDirty(false); setFailure(""); setFeedback(""); setReload(value => value + 1);
  }

  function closeDeparture() {
    setDeparture(undefined); setDepartureFailure("");
    window.requestAnimationFrame(() => { if (current() && trigger.current?.isConnected) trigger.current.focus(); });
  }
  function discardAndLeave() {
    if (!current() || !departure || writing.current) return;
    try { clearMemberFeatureDraft(membershipId); }
    catch { setDepartureFailure("无法清除草稿，仍保留当前修改。"); return; }
    setDirty(false); setDeparture(undefined); departure();
  }

  return <>
    <PageHeading title="对话功能" context="租户访问与知识" summary="当前成员" status={{ label: "独立保存", tone: "success" }} />
    <MemberObjectNavigation membershipId={membershipId} current="member-features" onRouteChange={onRouteChange} />
    <section className="member-workspace member-section member-feature-workspace" aria-busy={busy}>
      {!facts || !draft ? <><p role={failure ? "alert" : "status"}>{failure || "正在读取对话功能…"}</p>
        {failure ? <Button onClick={refreshFacts}>重新读取配置</Button> : null}</> : facts.readOnly ? <>
        <h2>管理员账号只读</h2><p>管理员由平台维护。</p></> : <>
        <h2>{facts.loginName}</h2>
        <div className="member-feature-stack">{(Object.keys(memberFeatureLabels) as (keyof typeof memberFeatureLabels)[]).map(code =>
          <label className="member-feature-control" key={code}>
            <input type="checkbox" checked={draft.conversationFeatures[code]} disabled={busy || !facts.tenantAvailableFeatures[code]}
              onChange={event => change(code, event.target.checked)} />
            <span>{memberFeatureLabels[code]}{facts.tenantAvailableFeatures[code] ? "" : " · 租户未开放"}</span>
          </label>)}</div>
        <div className="member-form-actions"><Button disabled={busy} onClick={refreshFacts}>取消修改</Button>
          <Button appearance="primary" disabled={busy} onClick={() => void save()}>{busy ? "正在保存…" : "保存对话功能"}</Button></div>
        <p role="status" aria-live="polite">{feedback}</p>
        {failure ? <><p role="alert">{failure}</p><Button disabled={busy} onClick={refreshFacts}>重新读取配置</Button></> : null}
      </>}
    </section>
    <Dialog open={Boolean(departure)} onOpenChange={(_, data) => { if (!data.open) closeDeparture(); }}>
      <DialogSurface className="member-account-dialog member-unsaved-dialog" aria-labelledby="member-unsaved-title"
        backdrop={{ className: "member-account-backdrop" }}>
        <DialogTitle id="member-unsaved-title" className="member-dialog-title" action={null}>修改尚未保存</DialogTitle>
        <div className="member-dialog-content"><p>留在当前页继续编辑，或放弃本页修改后离开。</p><p role="alert">{departureFailure}</p></div>
        <div className="member-dialog-actions"><Button onClick={closeDeparture}>保留当前</Button>
          <Button appearance="primary" onClick={discardAndLeave}>放弃修改并离开</Button></div>
      </DialogSurface>
    </Dialog>
  </>;
}
