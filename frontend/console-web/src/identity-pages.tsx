import { useCallback, useEffect, useRef, useState } from 'react';
import {
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
  Select,
  Text,
  Textarea,
  Title3,
} from '@fluentui/react-components';
import { Add20Regular } from '@fluentui/react-icons';
import {
  createUserInvitation,
  listIamRoles,
  listIamUsers,
  listUserInvitations,
  resendUserInvitation,
  revokeUserInvitation,
} from './api';
import { dialogBackdropMotion, dialogSurfaceMotion } from './dialog-motion';
import { renderCell } from './page-format';
import { PageHeading, RequestFeedback } from './page-shared';
import { PAGE_SIZE, Pagination } from './pagination';
import type { IamUserItem, RequestStatus, UserInvitationItem } from './types';

export function TenantUsersPage() {
  const [rows, setRows] = useState<IamUserItem[]>([]);
  const [roles, setRoles] = useState<{ roleId: string; name: string; type: string }[]>([]);
  const [invitations, setInvitations] = useState<UserInvitationItem[]>([]);
  const [panelOpen, setPanelOpen] = useState(false);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<Error>();
  const [inviteError, setInviteError] = useState<Error>();
  const [actionError, setActionError] = useState<Error>();
  const [revokeTarget, setRevokeTarget] = useState<UserInvitationItem>();
  const [revokeReason, setRevokeReason] = useState('');
  const [working, setWorking] = useState(false);
  const [result, setResult] = useState<string>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [roleId, setRoleId] = useState('');
  const loadController = useRef<AbortController>();

  const load = useCallback(async () => {
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    setStatus('loading');
    setError(undefined);
    try {
      const [userPage, roleRows, invitationRows] = await Promise.all([
        listIamUsers(page, pageSize, controller.signal),
        listIamRoles(controller.signal),
        listUserInvitations(controller.signal),
      ]);
      if (loadController.current !== controller || controller.signal.aborted) return;
      const manageable = roleRows.filter((role) => role.type.toLowerCase() === 'business');
      setRows(userPage.items);
      setTotal(userPage.total);
      setTotalPages(userPage.totalPages);
      if (userPage.page !== page) setPage(userPage.page);
      setRoles(manageable);
      setInvitations(invitationRows);
      setRoleId((current) => current || manageable[0]?.roleId || '');
      setStatus(userPage.total || invitationRows.length ? 'success' : 'empty');
    } catch (reason) {
      if (loadController.current === controller && !controller.signal.aborted) {
        setError(reason as Error);
        setStatus('error');
      }
    } finally {
      if (loadController.current === controller) loadController.current = undefined;
    }
  }, [page, pageSize]);

  useEffect(() => {
    void load();
    return () => loadController.current?.abort();
  }, [load]);

  function openInvitationDialog() {
    setInviteError(undefined);
    setResult(undefined);
    setPanelOpen(true);
  }

  function closeInvitationDialog() {
    setPanelOpen(false);
    setInviteError(undefined);
    setUsername('');
    setDisplayName('');
    setEmail('');
  }

  async function invite() {
    if (!username.trim() || !displayName.trim() || !email.trim() || !roleId) return;
    setWorking(true);
    setInviteError(undefined);
    setResult(undefined);
    try {
      const invitation = await createUserInvitation({
        username: username.trim(),
        displayName: displayName.trim(),
        email: email.trim(),
        roleId,
      });
      setResult(`邀请已创建，通知状态 ${invitation.deliveryStatus}，有效期至 ${new Date(invitation.expiresAt).toLocaleString('zh-CN')}`);
      closeInvitationDialog();
      await load();
    } catch (reason) {
      setInviteError(reason as Error);
    } finally {
      setWorking(false);
    }
  }

  function openRevokeDialog(invitation: UserInvitationItem) {
    setRevokeTarget(invitation);
    setRevokeReason('');
    setActionError(undefined);
  }

  function closeRevokeDialog() {
    setRevokeTarget(undefined);
    setRevokeReason('');
    setActionError(undefined);
  }

  async function invitationAction(invitationId: string, action: 'resend' | 'revoke', reason?: string) {
    setWorking(true);
    setActionError(undefined);
    try {
      if (action === 'resend') await resendUserInvitation(invitationId);
      else await revokeUserInvitation(invitationId, reason || '');
      if (action === 'revoke') {
        setRevokeTarget(undefined);
        setRevokeReason('');
      }
      await load();
    } catch (reason) {
      setActionError(reason as Error);
    } finally {
      setWorking(false);
    }
  }

  const inviteTraceId = inviteError && 'traceId' in inviteError
    ? String((inviteError as Error & { traceId?: string }).traceId || '')
    : '';
  const actionTraceId = actionError && 'traceId' in actionError
    ? String((actionError as Error & { traceId?: string }).traceId || '')
    : '';
  const loadTraceId = error && 'traceId' in error
    ? String((error as Error & { traceId?: string }).traceId || '')
    : '';

  return <main className="page-stack"><PageHeading title="用户管理" description="通过邀请激活添加本租户用户；管理员不接触初始密码，用户激活前不能登录或获得权限。" marker="邀请激活" /><div className="toolbar"><Button appearance="primary" icon={<Add20Regular />} disabled={(status !== 'success' && status !== 'empty') || roles.length === 0} onClick={openInvitationDialog}>邀请用户</Button></div><RequestFeedback status={status} error={error} onRetry={() => void load()} />{status === 'error' && loadTraceId && <Text className="trace-hint">错误追踪：{loadTraceId}</Text>}{actionError && !revokeTarget && <MessageBar intent="error" role="alert"><MessageBarBody>{actionError.message}{actionTraceId && <span className="trace-hint">错误追踪：{actionTraceId}</span>}</MessageBarBody></MessageBar>}{result && <MessageBar intent="success"><MessageBarBody>{result}</MessageBarBody></MessageBar>}
    {status === 'empty' && <section className="empty-state"><Title3 as="h2">暂无用户或待处理邀请</Title3><Text>当前租户尚未返回可管理用户或邀请记录。</Text></section>}
    {(status === 'success' || status === 'loading') && rows.length > 0 && <section className="surface data-region"><div className="table-wrap"><table aria-label="租户用户列表" aria-busy={status === 'loading'}><thead><tr><th>姓名</th><th>账号</th><th>邮箱</th><th>角色</th><th>状态</th></tr></thead><tbody>{rows.map((row) => <tr key={row.userId}><td>{renderCell(row.displayName)}</td><td>{renderCell(row.username)}</td><td>{renderCell(row.email)}</td><td>{renderCell(row.roleCodes)}</td><td>{renderCell(row.status)}</td></tr>)}</tbody></table></div><Pagination page={page} pageSize={pageSize} total={total} totalPages={totalPages} onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }} /></section>}
    {(status === 'success' || status === 'loading') && invitations.length > 0 && <section className="surface data-region"><div className="section-header"><div><Title3 as="h2">邀请记录</Title3><Text>创建邀请后进入通知队列；送达后用户在 24 小时内设置密码，账号才转为启用。</Text></div></div><div className="table-wrap"><table aria-label="用户邀请记录" aria-busy={status === 'loading'}><thead><tr><th>邮箱</th><th>邀请状态</th><th>通知状态</th><th>有效期</th><th>操作</th></tr></thead><tbody>{invitations.map((invitation) => <tr key={invitation.invitationId}><td>{invitation.email}</td><td>{invitation.statusLabel}</td><td>{invitation.deliveryStatusLabel}{invitation.deliveryError && <span className="cell-secondary">{invitation.deliveryError}</span>}</td><td>{new Date(invitation.expiresAt).toLocaleString('zh-CN')}</td><td><div className="row-actions"><Button disabled={working || invitation.status !== 'PENDING'} onClick={() => invitationAction(invitation.invitationId, 'resend')}>重发</Button><Button aria-label={`撤销 ${invitation.email}`} disabled={working || invitation.status !== 'PENDING'} onClick={() => openRevokeDialog(invitation)}>撤销</Button></div></td></tr>)}</tbody></table></div></section>}
    <Dialog open={Boolean(revokeTarget)} unmountOnClose={false} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open && !working) closeRevokeDialog(); }}><DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody><DialogTitle>撤销用户邀请</DialogTitle><DialogContent><div className="dialog-form"><Text>即将撤销 <strong>{revokeTarget?.email}</strong> 的待激活邀请。</Text><dl><dt>邀请编号</dt><dd>{revokeTarget?.invitationId}</dd><dt>原有效期</dt><dd>{revokeTarget ? new Date(revokeTarget.expiresAt).toLocaleString('zh-CN') : '-'}</dd></dl><MessageBar intent="warning"><MessageBarBody>撤销后，已发送的激活链接将立即失效。此操作不会删除已激活用户，也不能自动恢复。</MessageBarBody></MessageBar><Field label="撤销原因" required hint="记录人员变更、误发或安全处置依据，最多 500 个字符。"><Textarea maxLength={500} value={revokeReason} onChange={(_, data) => setRevokeReason(data.value)} /></Field>{actionError && <MessageBar intent="error" role="alert"><MessageBarBody>{actionError.message}{actionTraceId && <span className="trace-hint">错误追踪：{actionTraceId}</span>}</MessageBarBody></MessageBar>}</div></DialogContent><DialogActions><Button disabled={working} onClick={closeRevokeDialog}>取消</Button><Button appearance="primary" disabled={working || !revokeReason.trim() || !revokeTarget} onClick={() => { if (revokeTarget) void invitationAction(revokeTarget.invitationId, 'revoke', revokeReason.trim()); }}>{working ? '正在撤销' : '确认撤销'}</Button></DialogActions></DialogBody></DialogSurface></Dialog>
    <Dialog open={panelOpen} unmountOnClose={false} surfaceMotion={dialogSurfaceMotion} onOpenChange={(_, data) => { if (!data.open && !working) closeInvitationDialog(); }}><DialogSurface backdropMotion={dialogBackdropMotion}><DialogBody><DialogTitle>邀请用户</DialogTitle><DialogContent><div className="dialog-form"><Field label="姓名" required><Input value={displayName} onChange={(_, data) => setDisplayName(data.value)} /></Field><Field label="登录账号" required><Input value={username} onChange={(_, data) => setUsername(data.value)} /></Field><Field label="邮箱" required><Input type="email" value={email} onChange={(_, data) => setEmail(data.value)} /></Field><Field label="初始角色" required><Select aria-label="初始角色" value={roleId} onChange={(event) => setRoleId(event.target.value)}>{roles.map((role) => <option key={role.roleId} value={role.roleId}>{role.name}</option>)}</Select></Field><MessageBar intent="info"><MessageBarBody>系统发送一次性激活邮件；用户完成密码设置后账号才会启用。发送失败可在邀请记录中重试。</MessageBarBody></MessageBar>{inviteError && <MessageBar intent="error"><MessageBarBody>{inviteError.message}{inviteTraceId && <span className="trace-hint">错误追踪：{inviteTraceId}</span>}</MessageBarBody></MessageBar>}</div></DialogContent><DialogActions><Button disabled={working} onClick={closeInvitationDialog}>取消</Button><Button appearance="primary" disabled={working || !username.trim() || !displayName.trim() || !email.trim() || !roleId} onClick={invite}>{working ? '正在创建' : '发送邀请'}</Button></DialogActions></DialogBody></DialogSurface></Dialog>
  </main>;
}
