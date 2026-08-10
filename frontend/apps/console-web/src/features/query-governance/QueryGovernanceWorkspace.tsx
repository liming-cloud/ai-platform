import {
  Badge,
  Button,
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
import type { ConsoleRoute } from "../../session/console-route";
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
  route,
  sessionRevision,
  onSessionChanged,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "query" }>;
  sessionRevision: number;
  onSessionChanged: () => void;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] =
    useState<OperationsViewState>("unauthenticated");
  const [usage, setUsage] = useState<QueryUsage>();
  const [records, setRecords] = useState<QueryAuditRecord[]>([]);
  const [hasPreviousAuditPage, setHasPreviousAuditPage] = useState(false);
  const [hasNextAuditPage, setHasNextAuditPage] = useState(false);
  const [auditTotal, setAuditTotal] = useState(0);
  const [failure, setFailure] = useState<Failure>();
  const [loadedAt, setLoadedAt] = useState(0);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      setUsage(undefined);
      setRecords([]);
      setHasPreviousAuditPage(false);
      setHasNextAuditPage(false);
      setAuditTotal(0);
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    const [usageResult, auditResult] = await Promise.allSettled([
      client.getQueryUsage(),
      client.listQueryAuditRecords(route.auditPage, 20),
    ]);
    setLoadedAt(Date.now());
    if (usageResult.status === "fulfilled") {
      setUsage(usageResult.value);
    } else {
      setUsage(undefined);
    }
    if (auditResult.status === "fulfilled") {
      setRecords(auditResult.value.items);
      setHasPreviousAuditPage(auditResult.value.hasPrevious);
      setHasNextAuditPage(auditResult.value.hasNext);
      setAuditTotal(auditResult.value.totalElements);
    } else {
      setRecords([]);
      setHasPreviousAuditPage(false);
      setHasNextAuditPage(false);
      setAuditTotal(0);
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
  }, [client, route.auditPage]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function logout() {
    try {
      await client.logout();
    } finally {
      onSessionChanged();
      setViewState("unauthenticated");
      setUsage(undefined);
      setRecords([]);
      setHasPreviousAuditPage(false);
      setHasNextAuditPage(false);
      setAuditTotal(0);
    }
  }

  if (viewState === "unauthenticated") {
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="受保护的治理任务已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
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
        page={route.auditPage}
        total={auditTotal}
        hasPrevious={hasPreviousAuditPage}
        hasNext={hasNextAuditPage}
        onPrevious={() => onRouteChange({ ...route, auditPage: route.auditPage - 1 })}
        onNext={() => onRouteChange({ ...route, auditPage: route.auditPage + 1 })}
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
  page,
  total,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: {
  records: QueryAuditRecord[];
  page: number;
  total: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
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
          <div className="task-actions" aria-label="脱敏查询审计分页">
            <Text size={200}>第 {page} 页，共 {total} 条审计记录</Text>
            <Button appearance="secondary" disabled={!hasPrevious} onClick={onPrevious}>上一页</Button>
            <Button appearance="secondary" disabled={!hasNext} onClick={onNext}>下一页</Button>
          </div>
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
