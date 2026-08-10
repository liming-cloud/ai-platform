import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type TenantCustomRoleSummary,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { previewNavigation } from "../../session/navigation-capability";

type RoleRoute = Extract<ConsoleRoute, { navigationId: "access"; task: "roles" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";

/**
 * 自定义角色目录始终使用服务端分页，并把创建、修改和停用隔离在显式确认的模态任务中。
 */
export function TenantCustomRoleWorkspace({
  client,
  route,
  sessionRevision,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: RoleRoute;
  sessionRevision: number;
  onRouteChange: (route: RoleRoute) => void;
}) {
  const [state, setState] = useState<ViewState>("unauthenticated");
  const [roles, setRoles] = useState<TenantCustomRoleSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [failure, setFailure] = useState<string>();
  const [editing, setEditing] = useState<TenantCustomRoleSummary>();
  const [bindingTarget, setBindingTarget] = useState<TenantCustomRoleSummary>();
  const [disableTarget, setDisableTarget] = useState<TenantCustomRoleSummary>();
  const [creationOpen, setCreationOpen] = useState(false);
  const [name, setName] = useState("");
  const [permissions, setPermissions] = useState("assistant:query");
  const [membershipId, setMembershipId] = useState("");
  const [unbindConfirmation, setUnbindConfirmation] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const permissionCodes = useMemo(() => parsePermissions(permissions), [permissions]);
  const preview = useMemo(() => previewNavigation(permissionCodes), [permissionCodes]);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setState("unauthenticated");
      return;
    }
    setState("loading");
    setFailure(undefined);
    try {
      const page = await client.listTenantCustomRoles(route.page);
      setRoles(page.items);
      setTotal(page.totalElements);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
      setState("ready");
    } catch (error) {
      setFailure(description(error));
      setState("error");
    }
  }, [client, route.page]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, sessionRevision]);

  function openCreation() {
    setName("");
    setPermissions("assistant:query");
    setCreationOpen(true);
  }

  function openEdit(role: TenantCustomRoleSummary) {
    setPermissions(role.permissionCodes.join(", "));
    setEditing(role);
  }

  async function submitCreation() {
    if (name.trim().length === 0 || permissionCodes.length === 0) {
      setFailure("请填写角色名称，并至少选择一项格式正确的权限编码。");
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.createTenantCustomRole(name.trim(), permissionCodes);
      setCreationOpen(false);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function submitPermissionChange() {
    if (editing === undefined || permissionCodes.length === 0) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.changeTenantCustomRolePermissions(editing.id, permissionCodes);
      setEditing(undefined);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function disable() {
    if (disableTarget === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.disableTenantCustomRole(disableTarget.id);
      setDisableTarget(undefined);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function bindMember() {
    if (bindingTarget === undefined || !/^\d+$/.test(membershipId)) {
      setFailure("请输入成员目录中显示的正整数成员标识。");
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.bindTenantMemberCustomRole(membershipId, bindingTarget.id);
      setMembershipId("");
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function unbindMember() {
    if (bindingTarget === undefined || !/^\d+$/.test(membershipId)) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.unbindTenantMemberCustomRole(membershipId, bindingTarget.id);
      setUnbindConfirmation(false);
      setMembershipId("");
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (state === "unauthenticated") {
    return <StatePanel kind="stale" title="浏览器会话已经失效" description="角色目录已清除，请返回统一登录入口。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  if (state === "loading") {
    return <StatePanel kind="loading" title="正在加载自定义角色目录" description="正在读取当前租户的最小角色投影。" />;
  }
  if (state === "error") {
    return <StatePanel kind="error" title="自定义角色目录暂时不可用" description={failure ?? "请稍后重试。"} actionLabel="重新加载" onAction={() => void load()} />;
  }

  return (
    <section className="model-configuration-section" aria-label="自定义角色目录">
      <header className="task-heading">
        <Title3>自定义角色</Title3>
        <Text>角色权限改变会使所有绑定成员的旧租户会话失效。停用保留审计事实，不能作为删除操作使用。</Text>
      </header>
      {failure === undefined ? null : <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failure}</MessageBarBody></MessageBar>}
      <div className="governance-toolbar"><Text>第 {route.page} 页，共 {total} 个角色。列表始终由服务端分页。</Text><div><Button appearance="secondary" onClick={openCreation}>新建角色</Button><Button appearance="subtle" onClick={() => void load()}>刷新</Button></div></div>
      <div className="member-directory-table" role="region" aria-label="自定义角色分页表格">
        <table>
          <thead><tr><th>角色</th><th>权限</th><th>可见入口预览</th><th>绑定成员</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{roles.length === 0 ? <tr><td colSpan={6}>当前页没有自定义角色。</td></tr> : roles.map((role) => <tr key={role.id}>
            <td>{role.name}</td><td>{role.permissionCodes.join("、")}</td><td>{previewNavigation(role.permissionCodes).map((item) => item.label).join("、") || "无"}</td><td>{role.boundMemberCount}</td><td><Badge appearance="tint" color={role.status === "ACTIVE" ? "success" : "danger"}>{role.status === "ACTIVE" ? "已启用" : "已停用"}</Badge></td>
            <td><Button appearance="secondary" disabled={submitting || role.status !== "ACTIVE"} onClick={() => openEdit(role)}>修改权限</Button><Button appearance="secondary" disabled={submitting || role.status !== "ACTIVE"} onClick={() => { setMembershipId(""); setBindingTarget(role); }}>管理成员</Button><Button appearance="secondary" disabled={submitting || role.status !== "ACTIVE"} onClick={() => setDisableTarget(role)}>停用</Button></td>
          </tr>)}</tbody>
        </table>
      </div>
      <div className="task-actions" aria-label="自定义角色目录分页"><Button appearance="subtle" disabled={!hasPrevious} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button><Button appearance="subtle" disabled={!hasNext} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button></div>
      <Dialog open={creationOpen} onOpenChange={(_, data) => { if (!data.open && !submitting) setCreationOpen(false); }}>
        <DialogSurface><DialogBody><DialogTitle>新建自定义角色</DialogTitle><DialogContent><label>角色名称<Input aria-label="角色名称" value={name} onChange={(_, data) => setName(data.value)} /></label><PermissionEditor value={permissions} onChange={setPermissions} preview={preview.map((item) => item.label)} /></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setCreationOpen(false)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void submitCreation()}>{submitting ? "正在创建" : "创建角色"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={editing !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setEditing(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>修改角色权限</DialogTitle><DialogContent>修改“{editing?.name}”会立刻撤销全部绑定成员的旧会话。<PermissionEditor value={permissions} onChange={setPermissions} preview={preview.map((item) => item.label)} /></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setEditing(undefined)}>取消</Button><Button appearance="primary" disabled={submitting || permissionCodes.length === 0} onClick={() => void submitPermissionChange()}>{submitting ? "正在保存" : "确认修改"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={disableTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setDisableTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>确认停用角色</DialogTitle><DialogContent>停用“{disableTarget?.name}”会使所有绑定成员的旧会话失效，并保留角色和绑定历史用于审计。停用后不能重新启用。</DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setDisableTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void disable()}>{submitting ? "正在停用" : "确认停用"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={bindingTarget !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) { setBindingTarget(undefined); setUnbindConfirmation(false); } }}>
        <DialogSurface><DialogBody><DialogTitle>管理角色成员</DialogTitle><DialogContent>输入成员目录中的成员标识，为该成员绑定或解除“{bindingTarget?.name}”。每次操作都会撤销目标成员的旧租户会话；若解除会移除最后一个访问管理员，系统将拒绝。<Input aria-label="成员标识" inputMode="numeric" value={membershipId} onChange={(_, data) => setMembershipId(data.value)} /></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setBindingTarget(undefined)}>关闭</Button><Button appearance="secondary" disabled={submitting} onClick={() => setUnbindConfirmation(true)}>解除绑定</Button><Button appearance="primary" disabled={submitting} onClick={() => void bindMember()}>{submitting ? "正在提交" : "绑定成员"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={unbindConfirmation} onOpenChange={(_, data) => { if (!data.open && !submitting) setUnbindConfirmation(false); }}>
        <DialogSurface><DialogBody><DialogTitle>确认解除自定义角色</DialogTitle><DialogContent>这会撤销成员“{membershipId}”的“{bindingTarget?.name}”，并立即使其旧租户会话失效。</DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setUnbindConfirmation(false)}>取消</Button><Button appearance="primary" disabled={submitting || !/^\d+$/.test(membershipId)} onClick={() => void unbindMember()}>{submitting ? "正在解除" : "确认解除"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
    </section>
  );
}

function PermissionEditor({ value, onChange, preview }: { value: string; onChange: (value: string) => void; preview: string[] }) {
  return <><label>权限编码（用英文逗号分隔）<Input aria-label="权限编码" value={value} onChange={(_, data) => onChange(data.value)} /></label><Text>导航预览：{preview.length === 0 ? "无可见入口" : preview.join("、")}。预览不替代服务端授权。</Text></>;
}

function parsePermissions(value: string): string[] {
  const values = value.split(",").map((item) => item.trim()).filter((item) => /^[a-z][a-z-]*:[a-z][a-z-]*$/.test(item));
  return [...new Set(values)];
}

function description(error: unknown): string {
  return error instanceof ApiRequestError && error.traceId.length > 0
    ? `${error.message}，追踪标识：${error.traceId}`
    : error instanceof ApiRequestError ? error.message : "无法连接控制平面，请检查网络后重试。";
}
