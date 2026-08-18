import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Drawer,
  DrawerBody,
  DrawerHeader,
  DrawerHeaderTitle,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Menu,
  MenuButton,
  MenuItem,
  MenuList,
  MenuPopover,
  MenuTrigger,
  Text,
  Title3,
} from "@fluentui/react-components";
import { Dismiss20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useState } from "react";
import {
  presentApiError,
  type ControlPlaneClient,
  type PlatformAccountSummary,
  type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type AccountRoute = Extract<ConsoleRoute, { task: "accounts" }>;
type PlatformRoleCode = "PLATFORM_ADMINISTRATOR";
type RoleDialogTarget = { kind: "role"; account: PlatformAccountSummary; roleCode: PlatformRoleCode; action: "bind" | "revoke" };
type DialogTarget = RoleDialogTarget | { kind: "enable"; account: PlatformAccountSummary } | { kind: "create" } | { kind: "reset"; account: PlatformAccountSummary };

const roles: ReadonlyArray<{ code: PlatformRoleCode; label: string; navigation: string }> = [
  { code: "PLATFORM_ADMINISTRATOR", label: "平台管理员", navigation: "运营、账户、租户和模型供应链" },
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
  const [failure, setFailure] = useState<UserFacingError>();
  const [dialog, setDialog] = useState<DialogTarget>();
  const [detailAccount, setDetailAccount] = useState<PlatformAccountSummary>();
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");

  const reload = useCallback(async () => {
    setBusy(true);
    setFailure(undefined);
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
      setFailure(presentApiError(error, "加载平台账户"));
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
    setFailure(undefined);
    try {
      await (target.action === "bind"
        ? client.bindPlatformRole(target.account.subjectId, target.roleCode)
        : client.revokePlatformRole(target.account.subjectId, target.roleCode));
      setDialog(undefined);
      await reload();
    } catch (error) {
      setFailure(presentApiError(error, "变更平台角色"));
    } finally {
      setBusy(false);
    }
  }

  async function enableAccount(account: PlatformAccountSummary) {
    setBusy(true);
    setFailure(undefined);
    try {
      await client.enablePlatformAccount(account.subjectId);
      setDialog(undefined);
      await reload();
    } catch (error) {
      setFailure(presentApiError(error, "启用平台账户"));
    } finally {
      setBusy(false);
    }
  }

  async function resetAccountPassword(account: PlatformAccountSummary) {
    setBusy(true);
    setFailure(undefined);
    try {
      await client.resetPlatformAccountPassword(account.subjectId, password);
      closeDialog();
      await reload();
    } catch (error) {
      setFailure(presentApiError(error, "重置平台账户口令"));
    } finally {
      setPassword("");
      setBusy(false);
    }
  }

  function closeDialog() {
    setDialog(undefined);
    setLoginName("");
    setPassword("");
  }

  async function createAccount() {
    setBusy(true);
    setFailure(undefined);
    try {
      await client.createPlatformAccount({ loginName: loginName.trim(), password });
      closeDialog();
      if (route.page === 1) {
        await reload();
      } else {
        onRouteChange({ ...route, page: 1 });
      }
    } catch (error) {
      setFailure(presentApiError(error, "建立平台账户"));
    } finally {
      setPassword("");
      setBusy(false);
    }
  }

  return (
    <section className="platform-account-workspace" aria-live="polite">
      <header className="task-heading task-heading--split">
        <div>
          <Title3>平台账户目录</Title3>
          <Text>仅显示固定角色代码；每次角色变更都会使目标账户的全部会话失效。</Text>
        </div>
        <Button appearance="primary" disabled={busy} onClick={() => setDialog({ kind: "create" })}>建立账户</Button>
      </header>
      {failure ? <MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar> : null}
      {busy && accounts.length === 0 ? <StatePanel kind="loading" title="正在加载平台账户" description="正在读取当前服务器页。" /> : null}
      {!busy && accounts.length === 0 && !failure ? <StatePanel kind="empty" title="当前页没有账户" description="请切换到上一页或下一页继续查看。" /> : null}
      <div className="platform-account-list">
        {accounts.map((account) => (
          <article className="platform-account-row" key={account.subjectId}>
            <div className="platform-account-row__identity">
              <Title3>{account.loginName}</Title3>
              <Text>建立于 {new Date(account.createdAt).toLocaleString("zh-CN")}</Text>
              <div className="platform-account-row__roles" aria-label={`${account.loginName} 的已绑定角色`}>
                {account.activeRoleCodes.length === 0 ? <Text>尚未绑定平台角色</Text> : account.activeRoleCodes.map((roleCode) => (
                  <Badge key={roleCode} appearance="outline">{roleLabel(roleCode)}</Badge>
                ))}
              </div>
            </div>
            <Badge className="platform-account-row__status" appearance="tint" color={account.status === "ACTIVE" ? "success" : "danger"}>{account.status === "ACTIVE" ? "已启用" : "已停用"}</Badge>
            <div className="task-actions" aria-label={`${account.loginName} 的账户操作`}>
              <Button appearance="secondary" disabled={busy} onClick={() => setDetailAccount(account)}>查看详情</Button>
              <Menu>
                <MenuTrigger disableButtonEnhancement>
                  <MenuButton disabled={busy}>更多操作</MenuButton>
                </MenuTrigger>
                <MenuPopover><MenuList>
                  {account.status === "DISABLED" ? (
                    <MenuItem onClick={() => setDialog({ kind: "enable", account })}>启用账户</MenuItem>
                  ) : null}
                  <MenuItem onClick={() => setDialog({ kind: "reset", account })}>重置口令</MenuItem>
                  {account.status === "ACTIVE" ? roles.filter((role) => !account.activeRoleCodes.includes(role.code)).map((role) => (
                    <MenuItem key={role.code} onClick={() => setDialog({ kind: "role", account, roleCode: role.code, action: "bind" })}>绑定{role.label}</MenuItem>
                  )) : null}
                  {account.status === "ACTIVE" ? account.activeRoleCodes.filter((code): code is PlatformRoleCode => roles.some((role) => role.code === code)).map((roleCode) => (
                    <MenuItem key={roleCode} onClick={() => setDialog({ kind: "role", account, roleCode, action: "revoke" })}>撤销{roleLabel(roleCode)}</MenuItem>
                  )) : null}
                </MenuList></MenuPopover>
              </Menu>
            </div>
          </article>
        ))}
      </div>
      <div className="platform-account-pagination" aria-label="平台账户目录分页">
        <Text>第 {route.page} 页，共 {total} 个账户</Text>
        <Button appearance="subtle" disabled={!hasPrevious || busy} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button>
        <Button appearance="subtle" disabled={!hasNext || busy} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button>
      </div>
      <Drawer
        className="foundation-detail-drawer"
        type="overlay"
        position="end"
        size="medium"
        open={detailAccount !== undefined}
        onOpenChange={(_, data) => { if (!data.open) setDetailAccount(undefined); }}
      >
        <DrawerHeader>
          <DrawerHeaderTitle
            action={<Button appearance="subtle" aria-label="关闭账户详情" icon={<Dismiss20Regular />} onClick={() => setDetailAccount(undefined)} />}
          >
            账户详情
          </DrawerHeaderTitle>
        </DrawerHeader>
        <DrawerBody>
          {detailAccount === undefined ? null : (
            <div className="account-detail">
              <div className="account-detail__heading">
                <span className="account-detail__context">平台人员账户</span>
                <Title3>{detailAccount.loginName}</Title3>
                <Badge appearance="tint" color={detailAccount.status === "ACTIVE" ? "success" : "danger"}>
                  {detailAccount.status === "ACTIVE" ? "已启用" : "已停用"}
                </Badge>
              </div>
              <dl className="account-detail__facts">
                <div><dt>建立时间</dt><dd>{new Date(detailAccount.createdAt).toLocaleString("zh-CN")}</dd></div>
                <div><dt>平台角色</dt><dd>{detailAccount.activeRoleCodes.length === 0 ? "尚未绑定" : detailAccount.activeRoleCodes.map(roleLabel).join("、")}</dd></div>
                <div><dt>会话影响</dt><dd>账户状态、口令或角色变化后，已有平台与租户会话都会失效。</dd></div>
              </dl>
              <div className="account-detail__boundary">
                <strong>信息边界</strong>
                <Text>详情不展示主体标识、账户修订、密码摘要、会话或权限快照。</Text>
              </div>
            </div>
          )}
        </DrawerBody>
      </Drawer>
      <Dialog open={dialog !== undefined} onOpenChange={(_, data) => { if (!data.open && !busy) closeDialog(); }}>
        <DialogSurface className="foundation-task-dialog">
          <DialogBody>
            <DialogTitle>{dialog?.kind === "create" ? "建立平台账户" : dialog?.kind === "reset" ? "重置平台账户口令" : dialog?.kind === "enable" ? "确认启用平台账户" : dialog?.action === "bind" ? "确认绑定平台角色" : "确认撤销平台角色"}</DialogTitle>
            <DialogContent>{dialog?.kind === "create"
              ? <>
                <Text>新账户默认不绑定任何平台角色；请建立后再单独授予最小权限。</Text>
                <Field label="全局登录名" required>
                  <Input size="large" value={loginName} onChange={(_, data) => setLoginName(data.value)} autoComplete="username" />
                </Field>
                <Field label="初始口令" required>
                  <Input size="large" type="password" value={password} onChange={(_, data) => setPassword(data.value)} autoComplete="new-password" />
                </Field>
              </>
              : dialog?.kind === "reset"
                ? <><Text>{dialog.account.loginName} 的旧平台和租户会话会全部失效，需使用新口令重新认证。</Text><Field label="新口令" required><Input type="password" value={password} onChange={(_, data) => setPassword(data.value)} autoComplete="new-password" /></Field></>
              : dialog?.kind === "enable"
              ? `${dialog.account.loginName} 将恢复为活动账户。提交后该主体的全部平台与租户旧会话将失效，需重新认证。`
              : dialog?.kind === "role"
                ? `${dialog.account.loginName} 将${dialog.action === "bind" ? "获得" : "失去"}“${roleLabel(dialog.roleCode)}”。该角色可使用“${roles.find((role) => role.code === dialog.roleCode)?.navigation}”；提交后目标账户的全部会话将失效。${dialog.action === "revoke" ? "平台必须至少保留一名可用的平台管理员。" : ""}`
                : ""}</DialogContent>
            <DialogActions>
              <Button appearance="secondary" disabled={busy} onClick={closeDialog}>取消</Button>
              <Button appearance="primary" disabled={busy || dialog === undefined || ((dialog.kind === "create" || dialog.kind === "reset") && (dialog.kind === "create" ? loginName.trim() === "" || password === "" : password === ""))} onClick={() => dialog && void (dialog.kind === "create" ? createAccount() : dialog.kind === "reset" ? resetAccountPassword(dialog.account) : dialog.kind === "enable" ? enableAccount(dialog.account) : changeRole(dialog))}>{busy ? "正在提交" : dialog?.kind === "create" ? "建立账户" : dialog?.kind === "reset" ? "重置口令" : "确认提交"}</Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </section>
  );
}
