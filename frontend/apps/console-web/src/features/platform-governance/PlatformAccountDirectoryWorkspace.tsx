import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type PlatformAccountSummary,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type AccountRoute = Extract<ConsoleRoute, { task: "accounts" }>;
type PlatformRoleCode = "PLATFORM_ACCOUNT_ADMINISTRATOR" | "PLATFORM_TENANT_GOVERNOR" | "PLATFORM_OPERATOR";
type RoleDialogTarget = { kind: "role"; account: PlatformAccountSummary; roleCode: PlatformRoleCode; action: "bind" | "revoke" };
type DialogTarget = RoleDialogTarget | { kind: "enable"; account: PlatformAccountSummary };

const roles: ReadonlyArray<{ code: PlatformRoleCode; label: string; navigation: string }> = [
  { code: "PLATFORM_ACCOUNT_ADMINISTRATOR", label: "平台账户管理员", navigation: "平台账户目录" },
  { code: "PLATFORM_TENANT_GOVERNOR", label: "平台租户治理员", navigation: "平台租户生命周期" },
  { code: "PLATFORM_OPERATOR", label: "平台运营员", navigation: "平台工作区" },
];

function roleLabel(code: string): string {
  return roles.find((role) => role.code === code)?.label ?? code;
}

/**
 * 平台账户目录只呈现当前服务器页的安全摘要。角色写入必须逐项经单一 Dialog 确认，
 * 成功后重读服务端事实，避免把本地推断当作导航权限投影。
 */
export function PlatformAccountDirectoryWorkspace({
  client,
  route,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: AccountRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [accounts, setAccounts] = useState<PlatformAccountSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const [dialog, setDialog] = useState<DialogTarget>();

  const reload = useCallback(async () => {
    setBusy(true);
    setFailure("");
    try {
      const page = await client.listPlatformAccounts(route.page);
      setAccounts(page.items);
      setTotal(page.totalElements);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
    } catch (error) {
      setAccounts([]);
      setTotal(0);
      setHasPrevious(false);
      setHasNext(false);
      setFailure(error instanceof ApiRequestError ? error.message : "账户目录暂时不可用，请稍后重试。");
    } finally {
      setBusy(false);
    }
  }, [client, route.page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timeout);
  }, [reload]);

  async function changeRole(target: RoleDialogTarget) {
    setBusy(true);
    setFailure("");
    try {
      await (target.action === "bind"
        ? client.bindPlatformRole(target.account.subjectId, target.roleCode)
        : client.revokePlatformRole(target.account.subjectId, target.roleCode));
      setDialog(undefined);
      await reload();
    } catch (error) {
      setFailure(error instanceof ApiRequestError ? error.message : "角色操作暂时不可用，请稍后重试。");
    } finally {
      setBusy(false);
    }
  }

  async function enableAccount(account: PlatformAccountSummary) {
    setBusy(true);
    setFailure("");
    try {
      await client.enablePlatformAccount(account.subjectId);
      setDialog(undefined);
      await reload();
    } catch (error) {
      setFailure(error instanceof ApiRequestError ? error.message : "账户启用暂时不可用，请稍后重试。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading">
        <div>
          <Title3>平台账户目录</Title3>
          <Text>仅显示固定角色代码；每次角色变更都会使目标账户的全部会话失效。</Text>
        </div>
      </header>
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>读取未完成</MessageBarTitle>{failure}</MessageBarBody></MessageBar> : null}
      {busy && accounts.length === 0 ? <StatePanel kind="loading" title="正在加载平台账户" description="正在读取当前服务器页。" /> : null}
      {!busy && accounts.length === 0 && !failure ? <StatePanel kind="empty" title="当前页没有账户" description="请切换到其他服务端页继续查看。" /> : null}
      <div className="model-configuration-list">
        {accounts.map((account) => (
          <article className="model-configuration-card" key={account.subjectId}>
            <div>
              <Title3>{account.loginName}</Title3>
              <Text>主体编号：{account.subjectId}；修订：{account.version}；建立：{account.createdAt}</Text>
              <div className="task-actions" aria-label={`${account.loginName} 的已绑定角色`}>
                {account.activeRoleCodes.length === 0 ? <Text>尚未绑定平台角色</Text> : account.activeRoleCodes.map((roleCode) => (
                  <Badge key={roleCode} appearance="outline">{roleLabel(roleCode)}</Badge>
                ))}
              </div>
            </div>
            <Badge appearance="tint" color={account.status === "ACTIVE" ? "success" : "danger"}>{account.status}</Badge>
            {account.status === "ACTIVE" ? (
              <div className="task-actions" aria-label={`${account.loginName} 的角色操作`}>
                {roles.filter((role) => !account.activeRoleCodes.includes(role.code)).map((role) => (
                  <Button key={role.code} appearance="secondary" disabled={busy} onClick={() => setDialog({ kind: "role", account, roleCode: role.code, action: "bind" })}>绑定{role.label}</Button>
                ))}
                {account.activeRoleCodes.filter((code): code is PlatformRoleCode => roles.some((role) => role.code === code)).map((roleCode) => (
                  <Button key={roleCode} appearance="secondary" disabled={busy} onClick={() => setDialog({ kind: "role", account, roleCode, action: "revoke" })}>撤销{roleLabel(roleCode)}</Button>
                ))}
              </div>
            ) : <Button appearance="primary" disabled={busy} onClick={() => setDialog({ kind: "enable", account })}>启用账户</Button>}
          </article>
        ))}
      </div>
      <div className="task-actions" aria-label="平台账户目录分页">
        <Text>第 {route.page} 页，共 {total} 个账户</Text>
        <Button appearance="subtle" disabled={!hasPrevious || busy} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button>
        <Button appearance="subtle" disabled={!hasNext || busy} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button>
      </div>
      <Dialog open={dialog !== undefined} onOpenChange={(_, data) => { if (!data.open && !busy) setDialog(undefined); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>{dialog?.kind === "enable" ? "确认启用平台账户" : dialog?.action === "bind" ? "确认绑定平台角色" : "确认撤销平台角色"}</DialogTitle>
            <DialogContent>{dialog?.kind === "enable"
              ? `${dialog.account.loginName} 将恢复为活动账户。提交后该主体的全部平台与租户旧会话将失效，需重新认证。`
              : dialog?.kind === "role"
                ? `${dialog.account.loginName} 将${dialog.action === "bind" ? "获得" : "失去"}“${roleLabel(dialog.roleCode)}”。该角色投影的导航能力为“${roles.find((role) => role.code === dialog.roleCode)?.navigation}”；提交后目标账户的全部会话将失效。${dialog.action === "revoke" && dialog.roleCode === "PLATFORM_ACCOUNT_ADMINISTRATOR" ? "若这会留下最后一个活动的平台账户管理员，服务端将拒绝操作。" : ""}`
                : ""}</DialogContent>
            <DialogActions>
              <Button appearance="secondary" disabled={busy} onClick={() => setDialog(undefined)}>取消</Button>
              <Button appearance="primary" disabled={busy || dialog === undefined} onClick={() => dialog && void (dialog.kind === "enable" ? enableAccount(dialog.account) : changeRole(dialog))}>{busy ? "正在提交" : "确认提交"}</Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </section>
  );
}
