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
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type TenantLifecycleSummary,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type TenantRoute = Extract<ConsoleRoute, { task: "tenants" }>;
type TenantAction = "activate" | "suspend" | "restore";
type DialogTarget = { kind: "provision" } | {
  kind: "transition";
  lifecycle: TenantLifecycleSummary;
  action: TenantAction;
};

/**
 * 平台租户治理只展示当前服务端页的生命周期摘要。建立和状态变迁均是独立高风险操作，
 * 必须在单一 Dialog 中确认并在成功后重读服务器事实。
 */
export function PlatformTenantLifecycleWorkspace({
  client,
  route,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: TenantRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [lifecycles, setLifecycles] = useState<TenantLifecycleSummary[]>([]);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const [dialog, setDialog] = useState<DialogTarget>();
  const [tenantCode, setTenantCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [initialAdministratorSubjectId, setInitialAdministratorSubjectId] = useState("");

  const reload = useCallback(async () => {
    setBusy(true);
    setFailure("");
    try {
      const page = await client.listTenantLifecycles(route.page);
      setLifecycles(page.items);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
      setTotal(page.totalElements);
    } catch (error) {
      setFailure(toFailure(error));
      setLifecycles([]);
      setHasPrevious(false);
      setHasNext(false);
      setTotal(0);
    } finally {
      setBusy(false);
    }
  }, [client, route.page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timeout);
  }, [reload]);

  function closeDialog() {
    if (busy) {
      return;
    }
    setDialog(undefined);
    setTenantCode("");
    setDisplayName("");
    setInitialAdministratorSubjectId("");
  }

  async function provision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setFailure("");
    try {
      await client.provisionTenant({
        tenantCode: tenantCode.trim(),
        displayName: displayName.trim(),
        initialAdministratorSubjectId: initialAdministratorSubjectId.trim(),
      });
      closeDialogAfterSuccess();
      if (route.page === 1) {
        await reload();
      } else {
        onRouteChange({ ...route, page: 1 });
      }
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setBusy(false);
    }
  }

  async function transition(lifecycle: TenantLifecycleSummary, action: TenantAction) {
    setBusy(true);
    setFailure("");
    try {
      await (action === "activate"
        ? client.activateTenant(lifecycle.tenantId)
        : action === "suspend"
          ? client.suspendTenant(lifecycle.tenantId)
          : client.restoreTenant(lifecycle.tenantId));
      closeDialogAfterSuccess();
      await reload();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setBusy(false);
    }
  }

  function closeDialogAfterSuccess() {
    setDialog(undefined);
    setTenantCode("");
    setDisplayName("");
    setInitialAdministratorSubjectId("");
  }

  function transitionAction(lifecycle: TenantLifecycleSummary): TenantAction {
    return lifecycle.status === "PROVISIONING"
      ? "activate"
      : lifecycle.status === "ACTIVE"
        ? "suspend"
        : "restore";
  }

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading">
        <div>
          <Title3>平台租户生命周期</Title3>
          <Text>只显示生命周期摘要；初始管理员主体是既有业务资源目标，不代表当前平台操作者。</Text>
        </div>
        <Button appearance="primary" onClick={() => setDialog({ kind: "provision" })} disabled={busy}>
          建立准备中租户
        </Button>
      </header>
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failure}</MessageBarBody></MessageBar> : null}
      {busy && lifecycles.length === 0 ? <StatePanel kind="loading" title="正在加载平台租户" description="正在读取当前服务器页的生命周期摘要。" /> : null}
      {!busy && lifecycles.length === 0 && !failure ? <StatePanel kind="empty" title="当前页没有租户" description="可建立准备中租户，或切换到其他服务端页继续查看。" /> : null}
      <div className="model-configuration-list">
        {lifecycles.map((lifecycle) => {
          const action = transitionAction(lifecycle);
          return (
            <article className="model-configuration-card" key={lifecycle.tenantId}>
              <div>
                <Title3>{lifecycle.displayName}</Title3>
                <Text>租户编号：{lifecycle.tenantId}；修订：{lifecycle.version}；最后变更：{lifecycle.lastChangedAt}</Text>
              </div>
              <Badge appearance="tint" color={lifecycle.status === "ACTIVE" ? "success" : lifecycle.status === "SUSPENDED" ? "danger" : "warning"}>{lifecycle.status}</Badge>
              <Button appearance="secondary" disabled={busy} onClick={() => setDialog({ kind: "transition", lifecycle, action })}>{transitionLabel(action)}</Button>
            </article>
          );
        })}
      </div>
      <div className="task-actions" aria-label="平台租户目录分页">
        <Text>第 {route.page} 页，共 {total} 个租户</Text>
        <Button appearance="subtle" disabled={!hasPrevious || busy} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button>
        <Button appearance="subtle" disabled={!hasNext || busy} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button>
      </div>

      <Dialog open={dialog?.kind === "provision"} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>建立准备中租户</DialogTitle>
            <form onSubmit={(event) => void provision(event)}>
              <DialogContent>
                <Text>建立后仍需明确激活；不能从此页面读取租户正文、成员详情或凭据。</Text>
                <Field label="租户编码" required><Input autoFocus value={tenantCode} disabled={busy} onChange={(_, data) => setTenantCode(data.value)} /></Field>
                <Field label="显示名称" required><Input value={displayName} disabled={busy} onChange={(_, data) => setDisplayName(data.value)} /></Field>
                <Field label="初始管理员主体标识" required><Input value={initialAdministratorSubjectId} disabled={busy} onChange={(_, data) => setInitialAdministratorSubjectId(data.value)} /></Field>
              </DialogContent>
              <DialogActions>
                <Button appearance="secondary" type="button" disabled={busy} onClick={closeDialog}>取消</Button>
                <Button appearance="primary" type="submit" disabled={busy || !tenantCode.trim() || !displayName.trim() || !initialAdministratorSubjectId.trim()}>{busy ? "正在建立" : "确认建立"}</Button>
              </DialogActions>
            </form>
          </DialogBody>
        </DialogSurface>
      </Dialog>

      <Dialog open={dialog?.kind === "transition"} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>确认{dialog?.kind === "transition" ? transitionLabel(dialog.action) : "状态迁移"}</DialogTitle>
            <DialogContent>{dialog?.kind === "transition" ? `${dialog.lifecycle.displayName} 将从 ${dialog.lifecycle.status} 进入下一合法状态。此操作记录审计事实，失败时不会返回成功。` : ""}</DialogContent>
            <DialogActions>
              <Button appearance="secondary" disabled={busy} onClick={closeDialog}>取消</Button>
              <Button appearance="primary" disabled={busy} onClick={() => dialog?.kind === "transition" && void transition(dialog.lifecycle, dialog.action)}>{busy ? "正在提交" : "确认提交"}</Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </section>
  );
}

function transitionLabel(action: TenantAction) {
  return action === "activate" ? "激活租户" : action === "suspend" ? "挂起租户" : "恢复租户";
}

function toFailure(error: unknown) {
  return error instanceof ApiRequestError ? error.message : "租户治理操作未完成，请稍后重试。";
}
