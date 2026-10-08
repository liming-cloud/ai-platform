import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button, Checkbox, Field, Input } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import { presentApiError, type ControlPlaneClient, type MemberAssistantChoice, type MemberCreateOptions, type MemberConversationFeatures } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { clearMemberDraft, clearMemberPasswords, defaultMemberDraft, loadMemberDraft, memberDraftOwner, memberFeatureLabels, saveMemberDraft, type MemberDraft } from "./member-account-draft";

/** 三步开户只在最后提交；异步终态必须仍属于当前登录世代和当前页面。 */
export function MemberCreateWorkspace({ client, route, onRouteChange }: {
  client: ControlPlaneClient; route: Extract<ConsoleRoute, { task: "member-create" }>; onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [draft, setDraft] = useState(defaultMemberDraft);
  const [options, setOptions] = useState<MemberCreateOptions>();
  const [failure, setFailure] = useState("");
  const [busy, setBusy] = useState(false);
  const [passwordRevision, setPasswordRevision] = useState(0);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [choices, setChoices] = useState<{ key: string; items: MemberAssistantChoice[]; total: number; next: boolean }>();
  const [choiceFailure, setChoiceFailure] = useState("");
  const form = useRef<HTMLFormElement>(null);
  const title = useRef<HTMLHeadingElement>(null);
  const owner = useRef<string | undefined>(undefined);
  const active = useRef(false);
  const inFlight = useRef(false);
  const epoch = client.currentSessionRevision();
  const commandKey = useRef<{ command: string; key: string } | undefined>(undefined);
  const validLogin = /^[a-z][a-z0-9._-]{2,63}$/.test(draft.loginName);
  const step = validLogin ? route.step : 1;
  const ready = Boolean(options);
  const visibleStep = useRef(step);
  const choiceKey = `${epoch}:${query}:${page}`;
  const currentChoices = choices?.key === choiceKey ? choices : undefined;
  function currentSession() { return active.current && client.hasSession() && epoch === client.currentSessionRevision(); }
  function current() { return currentSession() && visibleStep.current === step; }

  useEffect(() => {
    let mounted = true; active.current = true;
    const currentForm = form.current;
    const unsubscribe = client.subscribeSessionChange(isActive => {
      if (!isActive) { active.current = false; owner.current = undefined; clearMemberDraft(); clearMemberPasswords(form.current); }
    });
    Promise.all([client.memberCreateOptions(), memberDraftOwner(client)]).then(([value, scope]) => {
      if (!mounted || !active.current || epoch !== client.currentSessionRevision() || !client.hasSession()) return;
      owner.current = scope;
      const saved = scope ? loadMemberDraft(scope) : undefined;
      const initial = saved ?? defaultMemberDraft();
      for (const code of Object.keys(memberFeatureLabels) as (keyof MemberConversationFeatures)[]) {
        if (!value.availableFeatures[code]) initial.conversationFeatures[code] = false;
      }
      setDraft(initial); setOptions(value);
    }).catch(error => { if (mounted && active.current && epoch === client.currentSessionRevision()) setFailure(presentApiError(error, "读取开户选项").description); });
    return () => { mounted = false; active.current = false; unsubscribe(); clearMemberPasswords(currentForm); };
  }, [client, epoch]);

  useEffect(() => {
    visibleStep.current = step;
    title.current?.focus();
    const currentForm = form.current;
    return () => clearMemberPasswords(currentForm);
  }, [step, ready]);

  useEffect(() => {
    if (ready && route.step > 1 && !validLogin) onRouteChange({ navigationId: "access", task: "member-create", step: 1 });
  }, [onRouteChange, ready, route.step, validLogin]);

  useEffect(() => {
    if (failure && step === 3) form.current?.querySelector<HTMLInputElement>('[name="password"]')?.focus();
  }, [failure, passwordRevision, step]);

  useEffect(() => {
    if (step !== 2 || !options) return;
    let mounted = true;
    const timer = window.setTimeout(() => {
      client.listMemberAssistants(query.trim(), page, 20).then(result => {
        if (mounted && active.current && epoch === client.currentSessionRevision() && client.hasSession()) {
          setChoices({ key: choiceKey, items: result.items, total: result.totalElements, next: result.hasNext }); setChoiceFailure("");
        }
      }).catch(error => { if (mounted && active.current && epoch === client.currentSessionRevision()) setChoiceFailure(presentApiError(error, "读取助手").description); });
    }, 200);
    return () => { mounted = false; window.clearTimeout(timer); };
  }, [choiceKey, client, epoch, options, page, query, step]);

  function change(next: MemberDraft) { if (!current()) return; setDraft(next); saveMemberDraft(owner.current, next); }
  function navigate(next: 1 | 2 | 3) { clearMemberPasswords(form.current); setFailure(""); onRouteChange({ navigationId: "access", task: "member-create", step: next }); }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || !current() || !options) return;
    if (!validLogin) { setFailure("请填写 3–64 位小写字母、数字、点、下划线或连字符。"); form.current?.querySelector<HTMLInputElement>('[name="loginName"]')?.focus(); return; }
    if (step < 3) { navigate(step === 1 ? 2 : 3); return; }
    const submitted = event.currentTarget;
    const fields = new FormData(submitted);
    const password = String(fields.get("password") ?? "");
    clearMemberPasswords(submitted);
    // Fluent 的非受控输入仍保存内部状态；更换实例，防止错误重绘恢复已清空的密码。
    setPasswordRevision(value => value + 1);
    if (!password || password !== fields.get("passwordConfirmation")) { setFailure("请完整填写两次相同密码。"); return; }
    const command = { ...draft, expectedTenantFeatureVersion: options.tenantFeatureVersion, expectedAuthorizationRevision: options.authorizationRevision };
    const fingerprint = JSON.stringify(command);
    if (commandKey.current?.command !== fingerprint) commandKey.current = { command: fingerprint, key: crypto.randomUUID() };
    inFlight.current = true; setBusy(true); setFailure("");
    try {
      const created = await client.createTenantMember({ ...command, password }, commandKey.current!.key);
      if (!current()) return;
      clearMemberDraft(); onRouteChange({ navigationId: "access", task: "member-account", membershipId: created.membershipId });
    } catch (error) {
      if (!current()) return;
      const issue = presentApiError(error, "添加成员"); setFailure(`${issue.title}：${issue.description}`);
      try { const latest = await client.memberCreateOptions(); if (current()) {
        setOptions(latest);
        const corrected = { ...draft, conversationFeatures: { ...draft.conversationFeatures } };
        for (const code of Object.keys(memberFeatureLabels) as (keyof MemberConversationFeatures)[]) {
          if (!latest.availableFeatures[code]) corrected.conversationFeatures[code] = false;
        }
        setDraft(corrected); saveMemberDraft(owner.current, corrected);
      } } catch { /* 原失败保留，不能凭缺失选项重试。 */ }
    } finally { clearMemberPasswords(submitted); inFlight.current = false; if (currentSession()) setBusy(false); }
  }

  return <>
    <PageHeading title="添加成员" context="租户访问与知识" summary="当前租户" status={{ label: "待填写", tone: "success" }} />
    <section className="member-workspace" aria-busy={busy}>
      <ol className="member-create-steps" aria-label="添加成员步骤">{["成员信息", "使用权限", "密码并创建"].map((label, index) => <li key={label} aria-current={step === index + 1 ? "step" : undefined}>{index + 1} · {label}</li>)}</ol>
      {!options ? <p role={failure ? "alert" : "status"}>{failure || "正在读取开户选项…"}</p> : <form ref={form} key={step} className="member-section member-create-form" noValidate onSubmit={event => void submit(event)}>
        <section className="member-create-step">
        <h2 ref={title} tabIndex={-1}>{["成员信息", "使用权限", "密码并创建"][step - 1]}</h2>
        <fieldset disabled={busy} className="member-form-fields">
          {step === 1 ? <Field label="登录名" hint="3–64 位小写字母、数字、点、下划线或连字符。">
            <Input name="loginName" required maxLength={64} autoComplete="off" value={draft.loginName} onChange={(_, data) => change({ ...draft, loginName: data.value })} /></Field> : step === 2 ? <>
            <fieldset className="member-conversation-features"><legend>对话功能</legend>{(Object.keys(memberFeatureLabels) as (keyof MemberConversationFeatures)[]).map(code =>
              <Checkbox key={code} label={`${memberFeatureLabels[code]}${options.availableFeatures[code] ? "" : " · 租户未开放"}`} checked={draft.conversationFeatures[code]} disabled={!options.availableFeatures[code]}
                onChange={(_, data) => change({ ...draft, conversationFeatures: { ...draft.conversationFeatures, [code]: Boolean(data.checked) } })} />)}</fieldset>
            <Field label="搜索已发布助手"><Input type="search" placeholder="输入助手名称" value={query} onChange={(_, data) => { setQuery(data.value); setPage(1); }} /></Field>
            <div className="member-assistant-choices">{currentChoices?.items.map(item => <Checkbox key={item.assistantId} label={item.displayName} checked={draft.assistantIds.includes(item.assistantId)}
              onChange={(_, data) => change({ ...draft, assistantIds: data.checked ? [...new Set([...draft.assistantIds, item.assistantId])] : draft.assistantIds.filter(id => id !== item.assistantId) })} />)}</div>
            <p role="status">{choiceFailure || (!currentChoices ? "正在加载已发布助手…" : `匹配 ${currentChoices.total} 个，已选 ${draft.assistantIds.length} 个`)}</p>
            {currentChoices && (currentChoices.total > 20 || page > 1) ? <div className="member-page-actions"><Button type="button" disabled={page === 1} onClick={() => setPage(page - 1)}>上一页</Button><span>第 {page} 页</span><Button type="button" disabled={!currentChoices.next} onClick={() => setPage(page + 1)}>下一页</Button></div> : null}
          </> : <><Field label="初始密码"><Input key={passwordRevision} name="password" type="password" required maxLength={1024} autoComplete="new-password" /></Field>
            <Field label="再次输入密码"><Input key={passwordRevision} name="passwordConfirmation" type="password" required maxLength={1024} autoComplete="new-password" /></Field>
            <p>刷新后请重新输入密码；请通过安全渠道单独告知本人。</p></>}
        </fieldset>
        </section>
        <p role="alert">{failure}</p>
        <div className="member-form-actions"><Button type="button" disabled={busy} onClick={() => { clearMemberPasswords(form.current); clearMemberDraft(); onRouteChange({ navigationId: "access", task: "members", page: 1 }); }}>取消</Button>
          {step > 1 ? <Button type="button" disabled={busy} onClick={() => navigate(step === 3 ? 2 : 1)}>上一步</Button> : null}
          <Button appearance="primary" type="submit" disabled={busy || (step === 2 && (!currentChoices || Boolean(choiceFailure)))}>{busy ? "正在创建…" : step === 3 ? "创建成员" : "下一步"}</Button></div>
      </form>}
    </section>
  </>;
}
