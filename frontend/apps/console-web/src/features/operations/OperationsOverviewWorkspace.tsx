import {
  Badge,
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Skeleton,
  SkeletonItem,
  Text,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { useCallback, useEffect, useState } from "react";
import {
  presentApiError,
  type ControlPlaneClient,
  type OperationsNoticeView,
  type OperationsRange,
  type PlatformOperationsOverviewView,
  type PlatformTenantOperationsSummaryView,
  type QueryOperationsView,
  type ResourceStatusView,
  type TenantOperationsOverviewView,
  type UserFacingError,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import {
  isOverviewStale,
  numberLabel,
  percentLabel,
  statusLabel,
} from "./operations-presentation";
import { loadPlatformOperationsSnapshot } from "./operations-loading";

type OperationsRoute = Extract<ConsoleRoute, { task: "overview" }>;

const RANGE_OPTIONS: { value: OperationsRange; label: string }[] = [
  { value: "DAY_1", label: "1 天" },
  { value: "DAY_7", label: "7 天" },
  { value: "DAY_30", label: "30 天" },
];

export function OperationsOverviewWorkspace({
  client,
  route,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: OperationsRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const platform = route.navigationId === "platform-operations";
  const range: OperationsRange = route.range;
  const [overview, setOverview] = useState<PlatformOperationsOverviewView | TenantOperationsOverviewView>();
  const [tenantSummaries, setTenantSummaries] = useState<PlatformTenantOperationsSummaryView[]>([]);
  const [tenantTotal, setTenantTotal] = useState(0);
  const [tenantHasPrevious, setTenantHasPrevious] = useState(false);
  const [tenantHasNext, setTenantHasNext] = useState(false);
  const [overviewFailure, setOverviewFailure] = useState<UserFacingError>();
  const [tenantFailure, setTenantFailure] = useState<UserFacingError>();
  const [tenantSnapshotAvailable, setTenantSnapshotAvailable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [, setFreshnessRevision] = useState(0);
  const page = platform ? route.page : 1;

  const load = useCallback(async (manual = false) => {
    if (manual) setRefreshing(true);
    else {
      setLoading(true);
      setOverview(undefined);
      setTenantSummaries([]);
      setTenantTotal(0);
      setTenantHasPrevious(false);
      setTenantHasNext(false);
      setTenantSnapshotAvailable(false);
    }
    setOverviewFailure(undefined);
    setTenantFailure(undefined);
    try {
      if (platform) {
        const snapshot = await loadPlatformOperationsSnapshot(client, range, page);
        if (snapshot.overview !== undefined) {
          setOverview(snapshot.overview);
        } else {
          setOverviewFailure(presentApiError(snapshot.overviewFailure, "加载平台运营总览"));
        }
        if (snapshot.tenants !== undefined) {
          setTenantSummaries(snapshot.tenants.items);
          setTenantTotal(snapshot.tenants.totalElements);
          setTenantHasPrevious(snapshot.tenants.hasPrevious);
          setTenantHasNext(snapshot.tenants.hasNext);
          setTenantSnapshotAvailable(true);
        } else {
          setTenantFailure(presentApiError(snapshot.tenantFailure, "加载租户运营摘要"));
        }
      } else {
        setOverview(await client.getTenantOperationsOverview(range));
        setTenantSummaries([]);
      }
    } catch (error) {
      setOverviewFailure(presentApiError(error, "加载运营总览"));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [client, page, platform, range]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load]);

  useEffect(() => {
    if (overview === undefined) return undefined;
    const delay = Math.max(0, Date.parse(overview.staleAfter) - Date.now());
    const timeout = window.setTimeout(
      () => setFreshnessRevision((revision) => revision + 1),
      Math.min(delay + 10, 2_147_483_647),
    );
    return () => window.clearTimeout(timeout);
  }, [overview]);

  if (loading && overview === undefined && !tenantSnapshotAvailable) {
    return <OperationsSkeleton />;
  }
  if (overview === undefined && (!platform || !tenantSnapshotAvailable)) {
    return (
      <StatePanel
        kind="error"
        title={overviewFailure?.title ?? "运营总览暂时不可用"}
        description={overviewFailure?.description ?? tenantFailure?.description ?? "请稍后重试。"}
        actionLabel="重新加载"
        onAction={() => void load()}
      />
    );
  }

  const stale = overview === undefined ? false : isOverviewStale(overview.staleAfter);
  return (
    <div className="operations-overview">
      <div className="operations-overview__toolbar">
        <div className="operations-overview__ranges" aria-label="统计范围">
          {RANGE_OPTIONS.map((option) => (
            <Button
              key={option.value}
              appearance={range === option.value ? "primary" : "subtle"}
              size="small"
              onClick={() => onRouteChange(platform ? {
                navigationId: "platform-operations",
                task: "overview",
                page: 1,
                range: option.value,
              } : {
                navigationId: "operations",
                task: "overview",
                range: option.value,
              })}
            >
              {option.label}
            </Button>
          ))}
        </div>
        <div className="operations-overview__freshness">
          {overview === undefined ? <Text size={200}>平台总览尚未加载</Text> : <>
            <Text size={200}>数据生成于 {new Date(overview.generatedAt).toLocaleString("zh-CN")}</Text>
            {stale ? <Badge appearance="tint" color="warning">可能已过期</Badge> : <Badge appearance="tint" color="success">最新</Badge>}
          </>}
          <RefreshButton refreshing={refreshing} onRefresh={() => void load(true)} />
        </div>
      </div>

      {overviewFailure !== undefined ? (
        <MessageBar intent="warning">
          <MessageBarBody>
            <MessageBarTitle>{overviewFailure.title}</MessageBarTitle>
            {overviewFailure.description}{overview === undefined ? "" : " 当前仍显示上一次成功获取的总览数据。"}
          </MessageBarBody>
        </MessageBar>
      ) : null}

      {overview === undefined ? null : <>
        <QuerySummary queries={overview.queries} />
        {platform
          ? <PlatformOverview overview={overview as PlatformOperationsOverviewView} />
          : <TenantOverview overview={overview as TenantOperationsOverviewView} />}
        <NoticeList notices={overview.notices} />
      </>}
      {tenantFailure !== undefined && tenantSnapshotAvailable ? (
        <MessageBar intent="warning">
          <MessageBarBody>
            <MessageBarTitle>{tenantFailure.title}</MessageBarTitle>
            {tenantFailure.description} 当前仍显示上一次成功获取的租户摘要。
          </MessageBarBody>
        </MessageBar>
      ) : null}
      {platform ? (
        tenantSnapshotAvailable ? <TenantSummaryTable
            items={tenantSummaries}
            total={tenantTotal}
            page={page}
            hasPrevious={tenantHasPrevious}
            hasNext={tenantHasNext}
            onPageChange={(nextPage) => onRouteChange({
              navigationId: "platform-operations",
              task: "overview",
              page: nextPage,
              range,
            })}
          /> : <StatePanel
            kind="error"
            title={tenantFailure?.title ?? "租户运营摘要暂时不可用"}
            description={tenantFailure?.description ?? "请稍后重试。"}
            actionLabel="重新加载"
            onAction={() => void load()}
          />
      ) : null}
    </div>
  );
}

function QuerySummary({ queries }: { queries: QueryOperationsView }) {
  const values = [
    ["受理", queries.accepted],
    ["已回答", queries.answered],
    ["已拒答", queries.refused],
    ["失败", queries.failed],
    ["运行中", queries.running],
    ["Token 用量", queries.totalTokens],
  ] as const;
  return (
    <section className="operations-overview__section" aria-labelledby="query-summary-title">
      <header className="operations-overview__section-heading">
        <div>
          <Title3 id="query-summary-title">查询运行</Title3>
          <Text>统计周期内的查询受理、终态和资源用量。</Text>
        </div>
        <div className="operations-overview__rates" aria-label="查询比率">
          <span>回答率 <strong>{percentLabel(queries.rates.answerRate)}</strong></span>
          <span>拒答率 <strong>{percentLabel(queries.rates.refusalRate)}</strong></span>
          <span>失败率 <strong>{percentLabel(queries.rates.failureRate)}</strong></span>
        </div>
      </header>
      <dl className="operations-overview__metrics">
        {values.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{numberLabel(value)}</dd></div>)}
      </dl>
      <QueryTrend points={queries.daily ?? []} />
      <QueryDistribution queries={queries} />
    </section>
  );
}

function QueryTrend({ points }: { points: NonNullable<QueryOperationsView["daily"]> }) {
  if (points.length === 0) {
    return <Text size={200}>当前统计周期还没有每日查询趋势。</Text>;
  }
  const maximum = Math.max(...points.map((point) => point.accepted), 1);
  const coordinates = points.map((point, index) => {
    const x = points.length === 1 ? 50 : index / (points.length - 1) * 100;
    const y = 38 - point.accepted / maximum * 34;
    return `${x},${y}`;
  }).join(" ");
  return <div className="operations-overview__trend">
    <svg viewBox="0 0 100 42" role="img"
      aria-label={points.map((point) => `${point.date} 受理 ${numberLabel(point.accepted)} 次`).join("，")}>
      <line x1="0" y1="38" x2="100" y2="38" stroke="#d8d8d8" strokeWidth="0.7" />
      <polyline points={coordinates} fill="none" stroke="#111111" strokeWidth="1.6"
        strokeLinecap="round" strokeLinejoin="round" />
    </svg>
    <div className="operations-overview__trend-table" aria-label="每日查询趋势文字说明">
      {points.map((point) => <span key={point.date}>{point.date.slice(5)} · {numberLabel(point.accepted)} 次</span>)}
    </div>
  </div>;
}

function QueryDistribution({ queries }: { queries: QueryOperationsView }) {
  const total = Math.max(queries.accepted, 1);
  const segments = [
    { label: "已回答", value: queries.answered, color: "#15803d" },
    { label: "已拒答", value: queries.refused, color: "#a16207" },
    { label: "失败", value: queries.failed, color: "#b42318" },
    { label: "运行中", value: queries.running, color: "#2563eb" },
  ];
  let offset = 0;
  return (
    <div className="operations-overview__distribution">
      <svg viewBox="0 0 100 8" role="img" aria-label={segments.map((item) => `${item.label} ${numberLabel(item.value)}`).join("，")}>
        <rect x="0" y="0" width="100" height="8" rx="4" fill="#ececec" />
        {segments.map((item) => {
          const width = item.value / total * 100;
          const x = offset;
          offset += width;
          return <rect key={item.label} x={x} y="0" width={width} height="8" fill={item.color} />;
        })}
      </svg>
      <div>{segments.map((item) => <span key={item.label}><i style={{ background: item.color }} />{item.label} {numberLabel(item.value)}</span>)}</div>
    </div>
  );
}

function PlatformOverview({ overview }: { overview: PlatformOperationsOverviewView }) {
  return (
    <section className="operations-overview__section" aria-labelledby="platform-resources-title">
      <header className="operations-overview__section-heading">
        <div><Title3 id="platform-resources-title">平台资源健康</Title3><Text>全平台脱敏总量，不包含租户内容和个人明细。</Text></div>
        <Badge appearance="tint" color={overview.attentionTenantCount > 0 ? "warning" : "success"}>
          {overview.attentionTenantCount > 0 ? `${numberLabel(overview.attentionTenantCount)} 个租户需要关注` : "未发现租户风险"}
        </Badge>
      </header>
      <div className="operations-overview__resource-grid">
        <StatusBlock title="租户" value={overview.tenants} />
        <StatusBlock title="平台管理员" value={overview.platformAdministrators} />
        <StatusBlock title="租户管理员" value={overview.tenantAdministrators} />
        <StatusBlock title="普通用户" value={overview.tenantUsers} />
        <StatusBlock title="知识库" value={overview.knowledgeSpaces} />
        <StatusBlock title="数据来源" value={overview.dataSources} />
        <StatusBlock title="索引" value={overview.indexes} />
        <StatusBlock title="同步与索引任务" value={overview.indexJobs} />
        <StatusBlock title="平台模型连接" value={overview.platformModelConnections} />
        <StatusBlock title="平台模型部署" value={overview.platformModelDeployments} />
      </div>
    </section>
  );
}

function TenantOverview({ overview }: { overview: TenantOperationsOverviewView }) {
  return (
    <section className="operations-overview__section" aria-labelledby="tenant-resources-title">
      <header className="operations-overview__section-heading">
        <div><Title3 id="tenant-resources-title">当前租户资源</Title3><Text>成员、知识、模型、助手和评测的当前状态。</Text></div>
        <Text>日配额 {numberLabel(overview.quota.dailyUsed)} / {numberLabel(overview.quota.dailyLimit)} · 并发 {numberLabel(overview.quota.concurrentUsed)} / {numberLabel(overview.quota.concurrentLimit)}</Text>
      </header>
      <div className="operations-overview__resource-grid">
        <StatusBlock title="租户管理员" value={overview.members.administrators} />
        <StatusBlock title="普通用户" value={overview.members.users} />
        <StatusBlock title="知识库" value={overview.knowledgeSpaces} />
        <StatusBlock title="数据来源" value={overview.dataSources} />
        <StatusBlock title="索引" value={overview.indexes} />
        <StatusBlock title="同步与索引任务" value={overview.indexJobs} />
        <StatusBlock title="模型连接" value={overview.modelConnections} />
        <StatusBlock title="模型部署" value={overview.modelDeployments} />
        <StatusBlock title="模型配置" value={overview.modelConfigurations} />
        <SimpleCount title="助手" value={overview.assistants} />
        <StatusBlock title="助手版本" value={overview.assistantVersions} />
        <StatusBlock title="助手测试" value={overview.assistantTestRuns} />
        <StatusBlock title="评测运行" value={overview.evaluationRuns} />
        <StatusBlock title="质量门结论" value={overview.releaseDecisions} />
      </div>
    </section>
  );
}

export function StatusBlock({ title, value }: { title: string; value: ResourceStatusView }) {
  return (
    <article className="operations-overview__resource">
      <div><Text weight="semibold">{title}</Text><strong>{numberLabel(value.total)}</strong></div>
      <p>{Object.entries(value.statuses).length === 0
        ? "暂无数据"
        : Object.entries(value.statuses).map(([status, count]) => `${statusLabel(status)} ${numberLabel(count)}`).join(" · ")}</p>
    </article>
  );
}

function SimpleCount({ title, value }: { title: string; value: number }) {
  return <article className="operations-overview__resource"><div><Text weight="semibold">{title}</Text><strong>{numberLabel(value)}</strong></div><p>当前总数</p></article>;
}

function NoticeList({ notices }: { notices: OperationsNoticeView[] }) {
  return (
    <section className="operations-overview__section" aria-labelledby="operations-notices-title">
      <header className="operations-overview__section-heading"><div><Title3 id="operations-notices-title">注意事项</Title3><Text>按影响优先处理可操作问题。</Text></div></header>
      <div className="operations-overview__notices">
        {notices.length === 0 ? <p>当前没有需要处理的运营事项。</p> : notices.map((notice, index) => (
          <article key={`${notice.title}-${index}`}>
            <Badge appearance="tint" color={notice.severity === "CRITICAL" ? "danger" : notice.severity === "WARNING" ? "warning" : "informative"}>{notice.severity === "CRITICAL" ? "紧急" : notice.severity === "WARNING" ? "注意" : "提示"}</Badge>
            <div><strong>{notice.title}</strong><p>{notice.description}</p></div>
            <Button appearance="subtle" as="a" href={notice.targetRoute}>前往处理</Button>
          </article>
        ))}
      </div>
    </section>
  );
}

export function TenantSummaryTable({ items, total, page, hasPrevious, hasNext, onPageChange }: {
  items: PlatformTenantOperationsSummaryView[];
  total: number;
  page: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPageChange: (page: number) => void;
}) {
  return (
    <section className="operations-overview__section" aria-labelledby="tenant-summary-title">
      <header className="operations-overview__section-heading"><div><Title3 id="tenant-summary-title">租户运营摘要</Title3><Text>共 {numberLabel(total)} 个租户，仅展示安全运营指标。</Text></div></header>
      <div className="operations-overview__table-scroll">
        <table>
          <thead><tr><th>租户</th><th>生命周期</th><th>查询受理</th><th>回答率</th><th>失败率</th><th>关注状态</th></tr></thead>
          <tbody>{items.length === 0 ? <tr><td colSpan={6}>当前没有租户数据。</td></tr> : items.map((item) => (
            <tr key={item.tenantReference}><td>{item.tenantName}</td><td>{statusLabel(item.lifecycleStatus)}</td><td>{numberLabel(item.queries.accepted)}</td><td>{percentLabel(item.queries.rates.answerRate)}</td><td>{percentLabel(item.queries.rates.failureRate)}</td><td><Badge appearance="tint" color={item.attentionRequired ? "warning" : "success"}>{item.attentionRequired ? "需要关注" : "正常"}</Badge></td></tr>
          ))}</tbody>
        </table>
      </div>
      <div className="operations-overview__pagination">
        <Text>第 {page} 页</Text>
        <div><Button appearance="subtle" disabled={!hasPrevious} onClick={() => onPageChange(page - 1)}>上一页</Button><Button appearance="subtle" disabled={!hasNext} onClick={() => onPageChange(page + 1)}>下一页</Button></div>
      </div>
    </section>
  );
}

export function RefreshButton({ refreshing, onRefresh }: {
  refreshing: boolean;
  onRefresh: () => void;
}) {
  return <Button
    appearance="subtle"
    icon={<ArrowClockwise20Regular />}
    disabled={refreshing}
    onClick={onRefresh}
  >
    {refreshing ? "正在刷新" : "刷新"}
  </Button>;
}

export function OperationsSkeleton() {
  return <div className="operations-overview__skeleton" aria-label="正在加载运营总览"><Skeleton><SkeletonItem size={32} /><SkeletonItem size={96} /><SkeletonItem size={96} /></Skeleton></div>;
}
