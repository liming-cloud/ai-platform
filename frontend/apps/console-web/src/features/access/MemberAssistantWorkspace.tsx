import { useEffect, useRef, useState } from "react";
import { Button } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type ControlPlaneClient, type MemberAssistantSettings, type MemberAssistantSettingsInput } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { memberDraftOwner } from "./member-account-draft";
import { clearAuthorizationDraft, loadAuthorizationDraft, observeAuthorizationDrafts, saveAuthorizationDraft } from "./authorization-draft";
import { useAuthorizationDeparture, type RegisterAuthorizationDeparture } from "./AuthorizationDeparture";
import { MemberObjectNavigation } from "./MemberObjectNavigation";
import { PublishedAssistantPicker } from "./PublishedAssistantPicker";
import "./access-policy.css";

const savedInput = (facts: MemberAssistantSettings): MemberAssistantSettingsInput => ({ assistantIds: [...facts.assistantIds], expectedAuthorizationRevision: facts.authorizationRevision });

/** 独立助手页保留原始授权修订号，不能借刷新草稿覆盖其他页面的新授权。 */
export function MemberAssistantWorkspace({ client, membershipId, onRouteChange, registerDeparture }: {
  client: ControlPlaneClient; membershipId: string; onRouteChange: (route: ConsoleRoute) => void; registerDeparture: RegisterAuthorizationDeparture;
}) {
  const [facts, setFacts] = useState<MemberAssistantSettings>();
  const [draft, setDraft] = useState<MemberAssistantSettingsInput>();
  const [failure, setFailure] = useState(""); const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false); const [dirty, setDirty] = useState(false); const [reload, setReload] = useState(0);
  const active = useRef(false); const writing = useRef(false); const owner = useRef<string | undefined>(undefined);
  const epoch = client.currentSessionRevision(); const target = `member:${membershipId}`;
  const current = () => active.current && client.hasSession() && client.currentSessionRevision() === epoch;
  const departureDialog = useAuthorizationDeparture(client, dirty, busy, registerDeparture, () => { clearAuthorizationDraft(target); setDirty(false); });
  useEffect(() => {
    let mounted = true; active.current = true; observeAuthorizationDrafts(client);
    const unsubscribe = client.subscribeSessionChange(isActive => { if (!isActive) { active.current = false; owner.current = undefined; setFacts(undefined); setDraft(undefined); } });
    Promise.all([client.getMemberAssistants(membershipId), memberDraftOwner(client)]).then(([value, scope]) => {
      if (!mounted || !active.current || !client.hasSession() || client.currentSessionRevision() !== epoch) return;
      owner.current = scope; setFacts(value); setDraft(savedInput(value)); setFailure(""); setFeedback("");
      if (!value.readOnly && scope) {
        try { const restored = loadAuthorizationDraft(scope, target);
          if (restored?.kind === "assistants") { setDraft({ assistantIds: restored.assistantIds, expectedAuthorizationRevision: restored.expectedAuthorizationRevision }); setDirty(true); setFeedback("已恢复本页未保存修改。"); }
        } catch { setFeedback("无法恢复草稿，当前显示已保存配置。"); }
      }
    }).catch(error => { if (mounted && active.current && client.currentSessionRevision() === epoch) setFailure(presentApiError(error, "读取可用助手").description); });
    return () => { mounted = false; active.current = false; unsubscribe(); };
  }, [client, epoch, membershipId, target, reload]);
  function change(assistantIds: string[]) {
    if (!current() || writing.current || !draft || facts?.readOnly) return;
    const next = { ...draft, assistantIds }; setDraft(next); setDirty(true); setFeedback(""); setFailure("");
    try { if (!owner.current) throw new Error(); saveAuthorizationDraft(owner.current, target, { kind: "assistants", ...next }); }
    catch { setFeedback("修改尚未保存到草稿，刷新会丢失。"); }
  }
  async function save() {
    if (!current() || writing.current || !draft || facts?.readOnly) return;
    writing.current = true; setBusy(true); setFeedback(""); setFailure("");
    try {
      const value = await client.updateMemberAssistants(membershipId, draft); if (!current()) return;
      setFacts(value); setDraft(savedInput(value)); setDirty(false);
      try { clearAuthorizationDraft(target); setFeedback("可用助手已保存。"); } catch { setFeedback("配置已保存，但草稿清除失败。"); }
    } catch (error) { if (current()) setFailure(presentApiError(error, "保存可用助手").description); }
    finally { writing.current = false; if (current()) setBusy(false); }
  }
  function refresh() {
    if (!current() || writing.current) return;
    try { clearAuthorizationDraft(target); } catch { setFailure("无法清除草稿，请重试。"); return; }
    setDirty(false); setFacts(undefined); setDraft(undefined); setFailure(""); setFeedback(""); setReload(value => value + 1);
  }
  return <>
    <PageHeading title="可用助手" context="租户访问与知识" summary="当前成员" status={{ label: "独立保存", tone: "success" }} />
    <MemberObjectNavigation membershipId={membershipId} current="member-assistants" onRouteChange={onRouteChange} />
    <section className="member-workspace member-section member-assistants-workspace" aria-busy={busy}>
      {!facts || !draft ? <><p role={failure ? "alert" : "status"}>{failure || "正在读取可用助手…"}</p>{failure ? <Button onClick={refresh}>重新读取配置</Button> : null}</>
        : facts.readOnly ? <><h2>管理员账号只读</h2><p>管理员由平台维护。</p></> : <>
          <h2>{facts.loginName}</h2><PublishedAssistantPicker client={client} selected={draft.assistantIds} disabled={busy} onChange={change} />
          <div className="member-form-actions"><Button disabled={busy} onClick={refresh}>取消修改</Button><Button appearance="primary" disabled={busy} onClick={() => void save()}>{busy ? "正在保存…" : "保存可用助手"}</Button></div>
          <p role="status" aria-live="polite">{feedback}</p>{failure ? <><p role="alert">{failure}</p><Button disabled={busy} onClick={refresh}>重新读取配置</Button></> : null}
        </>}
    </section>{departureDialog}
  </>;
}
