import {
  Badge,
  Button,
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

/**
 * 平台账户目录只呈现当前服务器页的安全摘要；账户操作留在受控的独立任务中。
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

  return (
    <section className="governed-file-workspace" aria-live="polite">
      <header className="task-heading">
        <div>
          <Title3>平台账户目录</Title3>
          <Text>只显示服务端分页摘要，不显示凭据、会话或权限快照。</Text>
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
            </div>
            <Badge appearance="tint" color={account.status === "ACTIVE" ? "success" : "danger"}>{account.status}</Badge>
          </article>
        ))}
      </div>
      <div className="task-actions" aria-label="平台账户目录分页">
        <Text>第 {route.page} 页，共 {total} 个账户</Text>
        <Button appearance="subtle" disabled={!hasPrevious || busy} onClick={() => onRouteChange({ ...route, page: route.page - 1 })}>上一页</Button>
        <Button appearance="subtle" disabled={!hasNext || busy} onClick={() => onRouteChange({ ...route, page: route.page + 1 })}>下一页</Button>
      </div>
    </section>
  );
}
