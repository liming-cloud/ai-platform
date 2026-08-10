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
import { useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type TenantMemberSummary,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

type MemberRoute = Extract<ConsoleRoute, { navigationId: "access" }>;
type ViewState = "unauthenticated" | "loading" | "ready" | "error";

/**
 * 当前租户成员目录只使用服务端分页，并将停用操作限制在明确的二次确认弹窗内。
 */
export function TenantMemberWorkspace({
  client,
  route,
  sessionRevision,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: MemberRoute;
  sessionRevision: number;
  onRouteChange: (route: MemberRoute) => void;
}) {
  const [state, setState] = useState<ViewState>("unauthenticated");
  const [members, setMembers] = useState<TenantMemberSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [failure, setFailure] = useState<string>();
  const [target, setTarget] = useState<TenantMemberSummary>();
  const [establishmentOpen, setEstablishmentOpen] = useState(false);
  const [subjectId, setSubjectId] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setState("unauthenticated");
      return;
    }
    setState("loading");
    setFailure(undefined);
    try {
      const page = await client.listTenantMembers(route.page);
      setMembers(page.items);
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

  async function confirmDisable() {
    if (target === undefined) return;
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.disableTenantMember(target.membershipId);
      setTarget(undefined);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function enable(member: TenantMemberSummary) {
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.enableTenantMember(member.membershipId);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function establish() {
    if (!/^\d+$/.test(subjectId) || BigInt(subjectId) < 1n) {
      setFailure("请输入既有全局主体的正整数标识。");
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.establishTenantMember(subjectId);
      setSubjectId("");
      setEstablishmentOpen(false);
      await load();
    } catch (error) {
      setFailure(description(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (state === "unauthenticated") {
    return <StatePanel kind="stale" title="浏览器会话已经失效" description="成员目录已清除，请返回统一登录入口。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  if (state === "loading") {
    return <StatePanel kind="loading" title="正在加载成员目录" description="正在读取当前租户的最小成员投影。" />;
  }
  if (state === "error") {
    return <StatePanel kind="error" title="成员目录暂时不可用" description={failure ?? "请稍后重试。"} actionLabel="重新加载" onAction={() => void load()} />;
  }

  return (
    <section className="model-configuration-section" aria-label="成员目录">
      <header className="task-heading">
        <Title3>成员与权限</Title3>
        <Text>仅展示当前租户成员的最小身份与状态。角色绑定与停用均会撤销目标成员的旧会话。</Text>
      </header>
      {failure === undefined ? null : <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failure}</MessageBarBody></MessageBar>}
      <div className="governance-toolbar"><Text>第 {route.page} 页，共 {total} 名成员。列表始终由服务端分页。</Text><div><Button appearance="secondary" onClick={() => setEstablishmentOpen(true)}>新建成员</Button><Button appearance="subtle" onClick={() => void load()}>刷新</Button></div></div>
      <div className="member-directory-table" role="region" aria-label="成员分页表格">
        <table>
          <thead><tr><th>登录名</th><th>成员角色</th><th>状态</th><th>权限修订</th><th>操作</th></tr></thead>
          <tbody>{members.length === 0 ? <tr><td colSpan={5}>当前页没有成员。</td></tr> : members.map((member) => <tr key={member.membershipId}>
            <td>{member.loginName}</td><td>{member.membershipRole}</td><td><Badge appearance="tint" color={member.status === "ENABLED" ? "success" : "danger"}>{member.status === "ENABLED" ? "已启用" : "已停用"}</Badge></td><td>{member.permissionVersion}</td>
            <td>{member.status === "ENABLED" ? <Button appearance="secondary" disabled={submitting} onClick={() => setTarget(member)}>停用</Button> : <Button appearance="secondary" disabled={submitting} onClick={() => void enable(member)}>启用</Button>}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <div className="task-actions" aria-label="成员目录分页"><Button appearance="subtle" disabled={!hasPrevious} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button><Button appearance="subtle" disabled={!hasNext} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button></div>
      <Dialog open={target !== undefined} onOpenChange={(_, data) => { if (!data.open && !submitting) setTarget(undefined); }}>
        <DialogSurface><DialogBody><DialogTitle>确认停用成员</DialogTitle><DialogContent>将停用“{target?.loginName}”并立即撤销其已签发的租户会话。该操作不删除主体，也不会移除其历史审计事实。</DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setTarget(undefined)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void confirmDisable()}>{submitting ? "正在停用" : "确认停用"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
      <Dialog open={establishmentOpen} onOpenChange={(_, data) => { if (!data.open && !submitting) setEstablishmentOpen(false); }}>
        <DialogSurface><DialogBody><DialogTitle>新建租户成员</DialogTitle><DialogContent>输入已存在且启用的全局主体标识。新成员默认启用，但不自动获得任何业务权限；请在建立后单独完成角色绑定。<Input aria-label="全局主体标识" inputMode="numeric" value={subjectId} onChange={(_, data) => setSubjectId(data.value)} /></DialogContent><DialogActions><Button appearance="secondary" disabled={submitting} onClick={() => setEstablishmentOpen(false)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={() => void establish()}>{submitting ? "正在建立" : "建立成员"}</Button></DialogActions></DialogBody></DialogSurface>
      </Dialog>
    </section>
  );
}

function description(error: unknown): string {
  return error instanceof ApiRequestError && error.traceId.length > 0
    ? `${error.message}，追踪标识：${error.traceId}`
    : error instanceof ApiRequestError ? error.message : "无法连接控制平面，请检查网络后重试。";
}
