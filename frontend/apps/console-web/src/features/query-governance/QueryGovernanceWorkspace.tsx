import {
  Badge,
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  ProgressBar,
  Skeleton,
  SkeletonItem,
  Text,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import {
  useCallback,
  useEffect,
  useState,
} from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type QueryAuditRecord,
  type QueryUsage,
} from "../../api/control-plane-client";
import { LoginForm } from "../knowledge-publication/PublicationViews";
import {
  isOperationsStale,
  resolveOperationsViewState,
  type OperationsViewState,
} from "./query-governance-state";

interface Failure {
  code: string;
  message: string;
  traceId: string;
}

export function QueryGovernanceWorkspace({
  client,
  sessionRevision,
  onSessionChanged,
}: {
  client: ControlPlaneClient;
  sessionRevision: number;
  onSessionChanged: () => void;
}) {
  const [viewState, setViewState] =
    useState<OperationsViewState>("unauthenticated");
  const [usage, setUsage] = useState<QueryUsage>();
  const [records, setRecords] = useState<QueryAuditRecord[]>([]);
  const [nextCursor, setNextCursor] = useState<string>();
  const [failure, setFailure] = useState<Failure>();
  const [loggingIn, setLoggingIn] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadedAt, setLoadedAt] = useState(0);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      setUsage(undefined);
      setRecords([]);
      setNextCursor(undefined);
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    const [usageResult, auditResult] = await Promise.allSettled([
      client.getQueryUsage(),
      client.listQueryAuditRecords(undefined, 20),
    ]);
    setLoadedAt(Date.now());
    if (usageResult.status === "fulfilled") {
      setUsage(usageResult.value);
    } else {
      setUsage(undefined);
    }
    if (auditResult.status === "fulfilled") {
      setRecords(auditResult.value.items);
      setNextCursor(auditResult.value.nextCursor ?? undefined);
    } else {
      setRecords([]);
      setNextCursor(undefined);
    }
    const failures = [usageResult, auditResult]
      .filter((result) => result.status === "rejected")
      .map((result) => toFailure(result.reason));
    const first = failures[0] ?? {
      code: "UNKNOWN_FAILURE",
      message: "运营数据暂时不可用",
      traceId: "",
    };
    setFailure(failures.length === 0 ? undefined : first);
    setViewState(resolveOperationsViewState(
      usageResult.status === "fulfilled",
      auditResult.status === "fulfilled",
      failures.map((item) => item.code),
    ));
  }, [client]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function login(credentials: {
    tenantCode: string;
    loginName: string;
    password: string;
  }) {
    setLoggingIn(true);
    setFailure(undefined);
    try {
      await client.login(credentials);
      onSessionChanged();
      await load();
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("unauthenticated");
    } finally {
      setLoggingIn(false);
    }
  }

  async function logout() {
    try {
      await client.logout();
    } finally {
      onSessionChanged();
      setViewState("unauthenticated");
      setUsage(undefined);
      setRecords([]);
      setNextCursor(undefined);
    }
  }

  async function loadMore() {
    if (nextCursor === undefined || loadingMore) {
      return;
    }
    setLoadingMore(true);
    try {
      const page = await client.listQueryAuditRecords(nextCursor, 20);
      setRecords((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor ?? undefined);
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("partial");
    } finally {
      setLoadingMore(false);
    }
  }

  if (viewState === "unauthenticated") {
    return (
      <section className="governance-login">
        {failure ? (
          <MessageBar intent="error">
            <MessageBarBody>
              <MessageBarTitle>登录失败</MessageBarTitle>
              {failure.message}
            </MessageBarBody>
          </MessageBar>
        ) : null}
        <LoginForm busy={loggingIn} onSubmit={login} />
      </section>
    );
  }

  if (viewState === "loading") {
    return <OperationsSkeleton />;
  }

  if (viewState === "denied") {
    return (
      <StatePanel
        kind="denied"
        title="没有治理查看权限"
        description="当前成员缺少用量或审计读取权限，请联系租户管理员调整角色。"
      />
    );
  }

  if (viewState === "error") {
    return (
      <StatePanel
        kind="error"
        title="运营数据暂时不可用"
        description={failureDescription(failure)}
        actionLabel="重新加载"
        onAction={() => void load()}
      />
    );
  }

  const stale = usage === undefined
    ? false
    : isOperationsStale(usage.generatedAt, loadedAt);

  return (
    <div className="governance-workspace">
      <div className="governance-toolbar">
        <Text>
          查询配额和审计事实按当前租户隔离，问题正文与回答内容不在此页展示。
        </Text>
        <div className="task-actions">
          <Button
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            onClick={() => void load()}
          >
            刷新
          </Button>
          <Button appearance="subtle" onClick={() => void logout()}>
            退出登录
          </Button>
        </div>
      </div>

      {viewState === "partial" ? (
        <StatePanel
          kind="partial"
          title="部分运营数据未能加载"
          description={failureDescription(failure)}
          actionLabel="重新加载"
          onAction={() => void load()}
        />
      ) : null}

      {stale ? (
        <StatePanel
          kind="stale"
          title="当前数据可能已经过期"
          description="统计生成时间超过五分钟，请刷新后再据此进行运营判断。"
          actionLabel="刷新数据"
          onAction={() => void load()}
        />
      ) : null}

      {usage ? <UsageSummary usage={usage} /> : null}
      <AuditRecords
        records={records}
        hasMore={nextCursor !== undefined}
        loadingMore={loadingMore}
        onLoadMore={loadMore}
      />
    </div>
  );
}

function UsageSummary({ usage }: { usage: QueryUsage }) {
  return (
    <section className="operations-section" aria-labelledby="usage-heading">
      <div className="operations-heading">
        <div>
          <Title3 id="usage-heading">今日查询用量</Title3>
          <Text>
            统计区间：{formatTime(usage.from)} 至 {formatTime(usage.to)}
          </Text>
        </div>
        <Text>更新于 {formatTime(usage.generatedAt)}</Text>
      </div>
      <dl className="operations-metrics">
        <Metric label="已接受" value={usage.acceptedQueries} />
        <Metric label="已回答" value={usage.answeredQueries} />
        <Metric label="已拒答" value={usage.refusedQueries} />
        <Metric label="失败" value={usage.failedQueries} />
        <Metric label="运行中" value={usage.runningQueries} />
        <Metric label="令牌总量" value={usage.totalTokens} />
      </dl>
      <div className="quota-list">
        <QuotaLine
          label="每日查询"
          used={usage.dailyQuota.used}
          limit={usage.dailyQuota.limit}
          note={`重置于 ${formatTime(usage.dailyQuota.resetsAt)}`}
        />
        <QuotaLine
          label="并发查询"
          used={usage.concurrentQuota.used}
          limit={usage.concurrentQuota.limit}
          note="运行租约过期后自动释放"
        />
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value.toLocaleString("zh-CN")}</dd>
    </div>
  );
}

function QuotaLine({
  label,
  used,
  limit,
  note,
}: {
  label: string;
  used: number;
  limit: number;
  note: string;
}) {
  const ratio = limit === 0 ? 0 : Math.min(1, used / limit);
  return (
    <div className="quota-line">
      <div className="quota-label">
        <Text weight="semibold">{label}</Text>
        <Text>{used} / {limit}</Text>
      </div>
      <ProgressBar value={ratio} thickness="large" />
      <Text size={200}>{note}</Text>
    </div>
  );
}

function AuditRecords({
  records,
  hasMore,
  loadingMore,
  onLoadMore,
}: {
  records: QueryAuditRecord[];
  hasMore: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;
}) {
  return (
    <section className="operations-section" aria-labelledby="audit-heading">
      <div className="operations-heading">
        <div>
          <Title3 id="audit-heading">脱敏查询审计</Title3>
          <Text>只展示定位和计量所需事实，不展示顾客或师生提问内容。</Text>
        </div>
      </div>
      {records.length === 0 ? (
        <StatePanel
          kind="empty"
          title="还没有查询记录"
          description="发布助手并完成首次可信查询后，审计事实会出现在这里。"
        />
      ) : (
        <>
          <div className="audit-table-scroll">
            <table className="audit-table">
              <thead>
                <tr>
                  <th scope="col">发生时间</th>
                  <th scope="col">结果</th>
                  <th scope="col">助手版本</th>
                  <th scope="col">问题摘要</th>
                  <th scope="col">输入</th>
                  <th scope="col">输出</th>
                  <th scope="col">总量</th>
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr key={record.queryRunId}>
                    <td>{formatTime(record.createdAt)}</td>
                    <td><StatusBadge record={record} /></td>
                    <td title={record.assistantVersionId}>
                      {shortId(record.assistantVersionId)}
                    </td>
                    <td title={record.questionDigest}>
                      {record.questionDigest.slice(0, 12)}
                    </td>
                    <td>{record.inputTokens}</td>
                    <td>{record.outputTokens}</td>
                    <td>{record.totalTokens}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {hasMore ? (
            <Button
              appearance="secondary"
              disabled={loadingMore}
              onClick={onLoadMore}
            >
              {loadingMore ? "正在加载" : "加载更多"}
            </Button>
          ) : (
            <Text size={200}>已显示全部审计记录</Text>
          )}
        </>
      )}
    </section>
  );
}

function StatusBadge({ record }: { record: QueryAuditRecord }) {
  const labels: Record<QueryAuditRecord["status"], string> = {
    ANSWERED: "已回答",
    REFUSED: "已拒答",
    FAILED: "失败",
    RUNNING: "运行中",
  };
  const color = record.status === "ANSWERED"
    ? "success"
    : record.status === "RUNNING"
      ? "informative"
      : record.status === "REFUSED"
        ? "warning"
        : "danger";
  return (
    <Badge appearance="tint" color={color}>
      {labels[record.status]}
    </Badge>
  );
}

function OperationsSkeleton() {
  return (
    <div className="governance-workspace" aria-label="正在加载运营数据">
      <Skeleton className="operations-skeleton">
        <SkeletonItem size={32} />
        <SkeletonItem size={64} />
        <SkeletonItem size={64} />
      </Skeleton>
      <Skeleton className="operations-skeleton">
        <SkeletonItem size={32} />
        <SkeletonItem size={96} />
        <SkeletonItem size={96} />
      </Skeleton>
    </div>
  );
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) {
    return {
      code: error.code,
      message: error.message,
      traceId: error.traceId,
    };
  }
  return {
    code: "NETWORK_FAILURE",
    message: "无法连接控制平面，请检查网络后重试",
    traceId: "",
  };
}

function failureDescription(failure?: Failure) {
  if (failure === undefined) {
    return "请稍后重试；如果问题持续存在，请联系平台运维人员。";
  }
  return failure.traceId.length === 0
    ? failure.message
    : `${failure.message}，追踪标识：${failure.traceId}`;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(new Date(value));
}

function shortId(value: string) {
  return `${value.slice(0, 8)}…${value.slice(-4)}`;
}
