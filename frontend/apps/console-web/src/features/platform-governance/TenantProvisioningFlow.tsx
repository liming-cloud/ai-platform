import { Button, Checkbox, Field, Input } from "@fluentui/react-components";
import { useEffect, useRef, useState, type FormEvent } from "react";
import type { ControlPlaneClient } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { defaultTenantDraft, provisioningFailure, provisioningQuotas, tenantInformationValid, type TenantDraft } from "./tenant-provisioning";
import { clearTenantDraft, loadTenantDraft, saveTenantDraft, tenantDraftOwner } from "./tenant-provisioning-draft";
import "./tenant-onboarding.css";

const quotaFields = [
  { key: "knowledgeBases", label: "知识库数量", unit: "个", integer: true },
  { key: "documentsPerKnowledgeBase", label: "每库文档数量", unit: "个", integer: true },
  { key: "storageGb", label: "每库内容容量", unit: "GB", integer: false },
  { key: "fileMb", label: "单文件大小", unit: "MB", integer: false },
  { key: "textMib", label: "粘贴文本大小", unit: "MiB", integer: false },
] as const;

/** 三步只收集草稿，最后一个请求原子开户；密码仅存在于当前步骤的原生表单。 */
export function TenantProvisioningFlow({ client, route, onRouteChange }: {
  client: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "create" }>;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [draft, setDraft] = useState(defaultTenantDraft);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [validation, setValidation] = useState("");
  const [invalidField, setInvalidField] = useState<keyof TenantDraft>();
  const owner = useRef<string | undefined>(undefined);
  const active = useRef(false);
  const submitting = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const form = useRef<HTMLFormElement>(null);

  useEffect(() => {
    active.current = true;
    let current = true;
    const unsubscribe = client.subscribeSessionChange(isActive => {
      if (!isActive) { active.current = false; owner.current = undefined; clearTenantDraft(); clearPasswords(form.current); }
    });
    void tenantDraftOwner(client).then(value => {
      if (!current || !active.current) return;
      owner.current = value;
      setDraft(value ? loadTenantDraft(value) ?? defaultTenantDraft() : defaultTenantDraft());
      setReady(true);
    });
    return () => { current = false; active.current = false; unsubscribe(); };
  }, [client]);

  useEffect(() => {
    if (!ready) return;
    const target = invalidField ? form.current?.elements.namedItem(invalidField) : heading.current;
    if (target instanceof HTMLElement) target.focus();
  }, [route.step, ready, invalidField, validation, busy]);

  const step = tenantInformationValid(draft) ? route.step : 1;
  useEffect(() => {
    const currentForm = form.current;
    return () => clearPasswords(currentForm);
  }, [step, ready]);
  function change(key: keyof TenantDraft, value: string) {
    const next = { ...draft, [key]: value };
    setDraft(next);
    if (active.current) saveTenantDraft(owner.current, next);
  }
  function navigate(step: 1 | 2 | 3) {
    clearPasswords(form.current);
    setValidation(""); setInvalidField(undefined);
    onRouteChange({ navigationId: "platform", task: "create", step });
  }
  function cancel() {
    clearPasswords(form.current); clearTenantDraft();
    onRouteChange({ navigationId: "platform", task: "tenants", page: 1 });
  }
  function showFailure(error: unknown) {
    clearPasswords(form.current);
    const issue = provisioningFailure(error);
    setValidation(issue.message); setInvalidField(issue.field);
    if (issue.step && issue.step !== route.step) onRouteChange({ navigationId: "platform", task: "create", step: issue.step });
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !active.current) return;
    setValidation(""); setInvalidField(undefined);
    if (!tenantInformationValid(draft)) { setValidation("请填写 2～40 个字符的组织名称及 3～20 位租户编码。"); return; }
    if (step === 1) { navigate(2); return; }
    let quotas;
    try { quotas = provisioningQuotas(draft); }
    catch (error) { showFailure(error); return; }
    if (step === 2) { navigate(3); return; }
    const submittedForm = event.currentTarget;
    const fields = new FormData(submittedForm);
    const password = String(fields.get("password") ?? "");
    if (!/^[a-z][a-z0-9._-]{2,63}$/.test(draft.loginName.trim()) || !password || password !== fields.get("passwordConfirmation") || !fields.has("confirmed")) {
      clearPasswords(submittedForm); setValidation("请检查管理员登录名，输入两次相同密码，并确认创建。"); return;
    }
    submitting.current = true; setBusy(true);
    try {
      const result = await client.provisionTenant({ tenantCode: draft.tenantCode.trim(), displayName: draft.displayName.trim(), administrator: { loginName: draft.loginName.trim(), password }, quotas });
      if (!active.current || !client.hasSession()) return;
      clearTenantDraft();
      onRouteChange({ navigationId: "platform", task: "detail", tenantId: result.tenantId });
    } catch (error) {
      if (active.current) showFailure(error);
    } finally {
      clearPasswords(submittedForm); submitting.current = false;
      if (active.current) setBusy(false);
    }
  }

  return <section className="tenant-onboarding" aria-busy={busy || !ready}>
    <ol className="tenant-onboarding__steps" aria-label="建立租户步骤">
      {["租户信息", "配额设置", "管理员账号"].map((label, index) => <li key={label} aria-current={index + 1 === step ? "step" : undefined} data-complete={index + 1 < step}><span>{index + 1}</span><strong>{label}</strong></li>)}
    </ol>
    {!ready ? <p role="status">正在恢复开户信息…</p> : <form key={step} ref={form} onSubmit={event => void submit(event)}>
      <header className="tenant-onboarding__heading"><h2 ref={heading} tabIndex={-1}>{["填写租户信息", "设置资源配额", "创建租户管理员"][step - 1]}</h2><span>{step} / 3</span></header>
      <fieldset disabled={busy}>
        {step === 1 ? <div className="tenant-onboarding__fields">
          <Field label="组织名称" hint="2–40 个字符。" required><Input name="displayName" value={draft.displayName} maxLength={80} autoComplete="organization" placeholder="例如：青岚科技" onChange={(_, data) => change("displayName", data.value)} /></Field>
          <Field label="租户编码" hint="3–20 位小写字母、数字或连字符；创建后不能修改。" required><Input name="tenantCode" value={draft.tenantCode} maxLength={20} pattern="[a-z][a-z0-9-]{2,19}" autoComplete="off" placeholder="例如：qinglan-demo" onChange={(_, data) => change("tenantCode", data.value)} /></Field>
        </div> : step === 2 ? <><div className="tenant-onboarding__fields tenant-onboarding__quotas">{quotaFields.map(field => <Field key={field.key} label={field.label} hint={field.integer ? "大于 0 的整数。" : "支持小数，须能精确换算为整数字节。"} required><Input name={field.key} inputMode={field.integer ? "numeric" : "decimal"} maxLength={40} value={draft[field.key]} contentAfter={<span>{field.unit}</span>} onChange={(_, data) => change(field.key, data.value)} /></Field>)}</div><p className="tenant-onboarding__note">租户隔离、权限校验和密码不回显始终开启。</p></> : <>
          <div className="tenant-onboarding__summary"><span><strong>{draft.displayName}</strong><small>{draft.tenantCode}</small></span><span><strong>{draft.knowledgeBases} 个知识库</strong><small>每库 {draft.storageGb} GB</small></span></div>
          <div className="tenant-onboarding__fields">
            <Field label="管理员登录名" hint="全局唯一，不需要提前创建账户。" required><Input name="loginName" value={draft.loginName} maxLength={64} pattern="[a-z][a-z0-9._-]{2,63}" autoComplete="off" placeholder="例如：qinglan.admin" onChange={(_, data) => change("loginName", data.value)} /></Field>
            <Field label="账号权限"><Input value="当前租户管理员" readOnly /></Field>
            <Field label="初始密码" required><Input name="password" type="password" maxLength={1024} autoComplete="new-password" /></Field>
            <Field label="再次输入" required><Input name="passwordConfirmation" type="password" maxLength={1024} autoComplete="new-password" /></Field>
          </div>
          <Checkbox name="confirmed" label="确认创建租户及其管理员账号" required />
        </>}
      </fieldset>
      {validation ? <p role="alert" className="tenant-onboarding__validation">{validation}</p> : null}
      <footer className="tenant-onboarding__actions"><Button appearance="subtle" type="button" disabled={busy} onClick={cancel}>取消</Button><div>{step > 1 ? <Button type="button" disabled={busy} onClick={() => navigate(step === 3 ? 2 : 1)}>上一步</Button> : null}<Button appearance="primary" type="submit" disabled={busy}>{busy ? "正在创建…" : step === 3 ? "创建租户" : "下一步"}</Button></div></footer>
    </form>}
  </section>;
}

function clearPasswords(form: HTMLFormElement | null): void {
  form?.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach(input => { input.value = ""; });
}
