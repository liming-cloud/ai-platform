import {
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type TenantLifecycle,
} from "../../api/control-plane-client";

/** 平台治理只操作生命周期投影，不读取租户知识、问答或成员详情。 */
export function PlatformTenantLifecycleWorkspace({
  client,
}: {
  client: ControlPlaneClient;
}) {
  const [tenantCode, setTenantCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [initialAdministratorSubjectId, setInitialAdministratorSubjectId] = useState("");
  const [lifecycle, setLifecycle] = useState<TenantLifecycle>();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");

  async function provision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setFailure("");
    try {
      setLifecycle(await client.provisionTenant({
        tenantCode: tenantCode.trim(),
        displayName: displayName.trim(),
        initialAdministratorSubjectId: initialAdministratorSubjectId.trim(),
      }));
    } catch (error) {
      setFailure(error instanceof ApiRequestError ? error.message : "租户建立未完成，请稍后重试。");
    } finally {
      setBusy(false);
    }
  }

  async function transition(action: "activate" | "suspend" | "restore") {
    if (lifecycle === undefined) {
      return;
    }
    setBusy(true);
    setFailure("");
    try {
      const next = action === "activate"
        ? await client.activateTenant(lifecycle.tenantId)
        : action === "suspend"
        ? await client.suspendTenant(lifecycle.tenantId)
        : await client.restoreTenant(lifecycle.tenantId);
      setLifecycle(next);
    } catch (error) {
      setFailure(error instanceof ApiRequestError ? error.message : "状态迁移未完成，请稍后重试。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading">
        <Title3>建立并迁移租户状态</Title3>
        <Text>初始管理员主体标识是既有业务资源目标，不代表当前平台操作者。</Text>
      </header>
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failure}</MessageBarBody></MessageBar> : null}
      <form className="governed-file-form" onSubmit={(event) => void provision(event)}>
        <Field label="租户编码" required><Input value={tenantCode} disabled={busy} onChange={(_, data) => setTenantCode(data.value)} /></Field>
        <Field label="显示名称" required><Input value={displayName} disabled={busy} onChange={(_, data) => setDisplayName(data.value)} /></Field>
        <Field label="初始管理员主体标识" required><Input value={initialAdministratorSubjectId} disabled={busy} onChange={(_, data) => setInitialAdministratorSubjectId(data.value)} /></Field>
        <Button appearance="primary" type="submit" disabled={busy || !tenantCode.trim() || !displayName.trim() || !initialAdministratorSubjectId.trim()}>建立准备中租户</Button>
      </form>
      {lifecycle ? (
        <StatePanel
          kind="partial"
          title={`${lifecycle.displayName}：${lifecycle.status}`}
          description={`租户编号：${lifecycle.tenantId}；当前修订：${lifecycle.version}。`}
          actionLabel={lifecycle.status === "PROVISIONING" ? "激活租户" : lifecycle.status === "ACTIVE" ? "挂起租户" : "恢复租户"}
          onAction={() => void transition(lifecycle.status === "PROVISIONING" ? "activate" : lifecycle.status === "ACTIVE" ? "suspend" : "restore")}
        />
      ) : null}
    </section>
  );
}
