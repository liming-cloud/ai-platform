import {
  Badge,
  Button,
  Field,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Select,
  Text,
  Textarea,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type GovernedDataSourceLifecycleResult,
  type GovernedFileDataSourceSummary,
  type IndexVersion,
  type KnowledgeSpace,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { PagedResourceNavigation } from "../shared/PagedResourceNavigation";

interface Failure {
  message: string;
  traceId: string;
}

type ViewState = "unauthenticated" | "loading" | "ready" | "error";

/**
 * 知识管理员的 M3 文件来源任务。页面只登记并探测受控 UTF-8 文本，绝不展示对象位置或正文回显。
 */
export function GovernedFileDataWorkspace({
  client,
  route,
  sessionRevision,
  onSessionChanged,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "governed-file" }>;
  sessionRevision: number;
  onSessionChanged: () => void;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [sourceName, setSourceName] = useState("");
  const [content, setContent] = useState("");
  const [source, setSource] = useState<GovernedDataSourceLifecycleResult>();
  const [fileSources, setFileSources] = useState<GovernedFileDataSourceSummary[]>([]);
  const [spaceHasPrevious, setSpaceHasPrevious] = useState(false);
  const [spaceHasNext, setSpaceHasNext] = useState(false);
  const [spaceTotal, setSpaceTotal] = useState(0);
  const [sourceHasPrevious, setSourceHasPrevious] = useState(false);
  const [sourceHasNext, setSourceHasNext] = useState(false);
  const [sourceTotal, setSourceTotal] = useState(0);
  const [publishedArtifactId, setPublishedArtifactId] = useState<string>();
  const [indexVersion, setIndexVersion] = useState<IndexVersion>();
  const [failure, setFailure] = useState<Failure>();
  const [submitting, setSubmitting] = useState(false);
  const knowledgeSpaceId = route.knowledgeSpaceId ?? "";

  const resetProtectedTaskState = useCallback(() => {
      setSpaces([]);
      setSourceName("");
    setContent("");
    setSource(undefined);
      setFileSources([]);
      setSpaceHasPrevious(false);
      setSpaceHasNext(false);
      setSpaceTotal(0);
      setSourceHasPrevious(false);
      setSourceHasNext(false);
      setSourceTotal(0);
    setPublishedArtifactId(undefined);
    setIndexVersion(undefined);
  }, []);

  const restartExpiredSession = useCallback((error: unknown): boolean => {
    if (!requiresSessionRestart(error, client.hasSession())) {
      return false;
    }
    resetProtectedTaskState();
    setFailure(toFailure(error));
    setViewState("unauthenticated");
    onSessionChanged();
    return true;
  }, [client, onSessionChanged, resetProtectedTaskState]);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      resetProtectedTaskState();
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const page = await client.listKnowledgeSpaces(route.knowledgeSpacePage);
      const selectedKnowledgeSpaceId = page.items.some((space) => space.id === knowledgeSpaceId)
        ? knowledgeSpaceId
        : page.items[0]?.id;
      setSpaces(page.items);
      setSpaceHasPrevious(page.hasPrevious);
      setSpaceHasNext(page.hasNext);
      setSpaceTotal(page.totalElements);
      if (selectedKnowledgeSpaceId !== undefined && selectedKnowledgeSpaceId !== knowledgeSpaceId) {
        onRouteChange({ ...route, knowledgeSpaceId: selectedKnowledgeSpaceId, sourcePage: 1 });
      }
      if (selectedKnowledgeSpaceId !== undefined) {
        const sources = await client.listGovernedFileDataSources(selectedKnowledgeSpaceId, route.sourcePage);
        setFileSources(sources.items);
        setSourceHasPrevious(sources.hasPrevious);
        setSourceHasNext(sources.hasNext);
        setSourceTotal(sources.totalElements);
      } else {
        setFileSources([]);
        setSourceHasPrevious(false);
        setSourceHasNext(false);
        setSourceTotal(0);
      }
      setViewState("ready");
    } catch (error) {
      if (restartExpiredSession(error)) {
        return;
      }
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client, knowledgeSpaceId, onRouteChange, resetProtectedTaskState, restartExpiredSession, route]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (knowledgeSpaceId.length === 0 || sourceName.trim().length === 0 || content.trim().length === 0) {
      setFailure({ message: "请选择知识空间，并填写来源名称和 UTF-8 文本内容。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      const registered = await client.registerFileDataSource(
        knowledgeSpaceId,
        sourceName.trim(),
        content,
      );
      setSource(registered);
      setContent("");
      setPublishedArtifactId(undefined);
      setIndexVersion(undefined);
      if (route.sourcePage === 1) {
        await load();
      } else {
        onRouteChange({ ...route, sourcePage: 1 });
      }
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function probe() {
    if (source === undefined) {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setSource(await client.probeFileDataSource(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      ));
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function synchronize() {
    if (source === undefined) {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setSource(await client.synchronizeFileDataSource(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      ));
      setIndexVersion(undefined);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function publishArtifact() {
    if (source?.syncArtifactId === null || source?.syncArtifactId === undefined) {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      const published = await client.publishFileSyncArtifact(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.syncArtifactId,
        source.version,
      );
      setSource(published);
      setPublishedArtifactId(source.syncArtifactId);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function buildIndex() {
    if (source === undefined || source.status !== "READY" || publishedArtifactId === undefined) {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      const configurations = await client.listModelConfigurations();
      const embedding = configurations.items.find(
        (configuration) => configuration.modelType === "EMBEDDING"
          && configuration.status === "ACTIVE"
          && configuration.credentialConfigured,
      );
      if (embedding === undefined) {
        throw new Error("请先在模型配置中启用一个可用的嵌入模型");
      }
      const created = await client.createIndexVersion(source.knowledgeSpaceId, embedding.id);
      setIndexVersion(created);
      await waitForIndex(source.knowledgeSpaceId, created.id);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(modelRuntimeFailure(error) ?? toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function waitForIndex(knowledgeSpaceId: string, indexVersionId: string) {
    for (let attempt = 0; attempt < 90; attempt += 1) {
      const page = await client.listIndexVersions(knowledgeSpaceId);
      const current = page.items.find((candidate) => candidate.id === indexVersionId);
      if (current === undefined) {
        throw new Error("服务端未返回刚创建的索引版本");
      }
      setIndexVersion(current);
      if (current.status === "READY" || current.status === "FAILED") {
        return;
      }
      await delay(1000);
    }
    throw new Error("索引构建仍在进行，请稍后刷新数据任务查看");
  }

  async function publishIndex() {
    if (source === undefined || indexVersion?.status !== "READY") {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setIndexVersion(await client.publishIndexVersion(
        source.knowledgeSpaceId,
        indexVersion.id,
        indexVersion.version,
      ));
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function retire() {
    if (source === undefined) {
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setSource(await client.retireFileDataSource(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      ));
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function logout() {
    try {
      await client.logout();
    } finally {
      onSessionChanged();
      resetProtectedTaskState();
      setViewState("unauthenticated");
    }
  }

  function selectSource(summary: GovernedFileDataSourceSummary | undefined) {
    setIndexVersion(undefined);
    if (summary === undefined) {
      setSource(undefined);
      setSourceName("");
      setContent("");
      setPublishedArtifactId(undefined);
      return;
    }
    setSource({
      dataSourceId: summary.dataSourceId,
      knowledgeSpaceId: summary.knowledgeSpaceId,
      status: summary.status,
      version: summary.version,
      failedOperation: summary.failedOperation,
      syncArtifactId: null,
      qualityStatus: null,
    });
    setSourceName(summary.displayName);
    setContent("");
    setPublishedArtifactId(summary.publishedArtifactId ?? undefined);
  }

  if (viewState === "unauthenticated") {
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="受保护的数据任务已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
    );
  }

  if (viewState === "loading") {
    return <StatePanel kind="loading" title="正在加载数据任务" description="正在读取当前租户可用知识空间。" />;
  }

  if (viewState === "error") {
    return (
      <StatePanel
        kind="error"
        title="数据任务暂时不可用"
        description={failureDescription(failure)}
        actionLabel="重新加载"
        onAction={() => void load()}
      />
    );
  }

  return (
    <div className="governed-file-workspace">
      <div className="governance-toolbar">
        <Text>文件必须依次完成探测、同步和质量检查，再显式发布、建立并发布索引；每一步均使用最新修订。</Text>
        <div className="task-actions">
          <Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => void load()}>
            刷新
          </Button>
          <Button appearance="subtle" onClick={() => void logout()}>
            退出登录
          </Button>
        </div>
      </div>

      {failure ? <FailureMessage failure={failure} /> : null}

      {spaces.length === 0 ? (
        <StatePanel
          kind="empty"
          title="尚无可用知识空间"
          description="先在“首份文本发布”任务创建知识空间，再返回此处登记受治理文件来源。"
        />
      ) : (
        <section className="governed-file-section">
          <header className="task-heading">
            <Title3>登记、同步并发布文件来源</Title3>
            <Text>当前仅支持 UTF-8 纯文本。对象键、桶名、摘要和正文均由平台处理，不会在控制台展示。</Text>
          </header>
          <Field label="已登记文件来源">
            <Select
              value={source?.dataSourceId ?? ""}
              disabled={submitting}
              onChange={(_, data) => selectSource(
                fileSources.find((candidate) => candidate.dataSourceId === data.value),
              )}
            >
              <option value="">登记新文件来源</option>
              {fileSources.map((candidate) => (
                <option key={candidate.dataSourceId} value={candidate.dataSourceId}>
                  {fileSourceOptionLabel(candidate)}
                </option>
              ))}
            </Select>
          </Field>
          <form className="governed-file-form" onSubmit={register}>
            <Field label="知识空间" required>
              <Select
                value={knowledgeSpaceId}
                onChange={(_, data) => {
                  onRouteChange({
                    ...route,
                    knowledgeSpaceId: data.value || undefined,
                    sourcePage: 1,
                  });
                  selectSource(undefined);
                }}
                disabled={submitting || source !== undefined}
              >
                {spaces.map((space) => <option key={space.id} value={space.id}>{space.name}</option>)}
              </Select>
            </Field>
            <PagedResourceNavigation
              label="知识空间"
              page={route.knowledgeSpacePage}
              total={spaceTotal}
              hasPrevious={spaceHasPrevious}
              hasNext={spaceHasNext}
              disabled={submitting || source !== undefined}
              onPrevious={() => {
                selectSource(undefined);
                onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage - 1, knowledgeSpaceId: undefined, sourcePage: 1 });
              }}
              onNext={() => {
                selectSource(undefined);
                onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage + 1, knowledgeSpaceId: undefined, sourcePage: 1 });
              }}
            />
            <Field label="来源名称" required>
              <input
                className="governed-file-name"
                value={sourceName}
                onChange={(event) => setSourceName(event.target.value)}
                disabled={submitting || source !== undefined}
              />
            </Field>
            <Field label="文件内容" required>
              <Textarea
                className="content-input"
                value={content}
                onChange={(_, data) => setContent(data.value)}
                disabled={submitting || source !== undefined}
                placeholder="粘贴 UTF-8 纯文本内容"
              />
            </Field>
            {source === undefined ? (
              <Button appearance="primary" type="submit" disabled={submitting}>
                {submitting ? "正在登记" : "登记受控文件"}
              </Button>
            ) : null}
          </form>

          <PagedResourceNavigation
            label="文件来源"
            page={route.sourcePage}
            total={sourceTotal}
            hasPrevious={sourceHasPrevious}
            hasNext={sourceHasNext}
            disabled={submitting}
            onPrevious={() => {
              selectSource(undefined);
              onRouteChange({ ...route, sourcePage: route.sourcePage - 1 });
            }}
            onNext={() => {
              selectSource(undefined);
              onRouteChange({ ...route, sourcePage: route.sourcePage + 1 });
            }}
          />

          {source ? (
            <article className="governed-file-result">
              <div>
                <Title3>{sourceName || "受治理文件来源"}</Title3>
                <Text>数据源编号：{source.dataSourceId}</Text>
              </div>
              <Badge appearance="tint" color={source.status === "READY" ? "success" : source.status === "FAILED" ? "danger" : "warning"}>
                {source.status}
              </Badge>
              <div className="task-actions">
                {canProbe(source) ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void probe()}>
                    {source.status === "FAILED" ? "重新探测" : "开始探测"}
                  </Button>
                ) : null}
                {canSynchronize(source) ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void synchronize()}>
                    {source.status === "FAILED" ? "重新同步" : "开始同步和质量检查"}
                  </Button>
                ) : null}
                {source.syncArtifactId ? (
                  <Text>同步产物已生成，质量结论：{source.qualityStatus ?? "未返回"}。</Text>
                ) : null}
                {canPublish(source, publishedArtifactId) ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void publishArtifact()}>
                    发布同步产物
                  </Button>
                ) : null}
                {source.qualityStatus === "BLOCK" ? (
                  <Text>质量检查已阻断，不能发布或建立索引。</Text>
                ) : null}
                {publishedArtifactId === source.syncArtifactId && source.syncArtifactId ? (
                  <Text>同步产物已发布，可建立新的索引版本。</Text>
                ) : null}
                {canBuildIndex(source, publishedArtifactId) ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void buildIndex()}>
                    创建索引版本
                  </Button>
                ) : null}
                {indexVersion ? (
                  <Text>索引版本：{indexVersion.status}{indexVersion.failureCode ? `（${indexVersion.failureCode}）` : ""}</Text>
                ) : null}
                {indexVersion?.status === "READY" ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void publishIndex()}>
                    发布索引版本
                  </Button>
                ) : null}
                {source.status !== "RETIRED" ? (
                  <Button appearance="subtle" disabled={submitting} onClick={() => void retire()}>
                    退役来源
                  </Button>
                ) : null}
              </div>
            </article>
          ) : null}
        </section>
      )}
    </div>
  );
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) {
    return { message: error.message, traceId: error.traceId };
  }
  return { message: "无法连接控制平面，请检查网络后重试", traceId: "" };
}

/**
 * 默认安全部署不会装配需要本地研发主密钥的模型配置读取入口。
 * 仅在创建索引任务中将该受控不可用状态转为可恢复提示，避免向知识管理员暴露路由或装配细节。
 */
export function modelRuntimeFailure(error: unknown): Failure | undefined {
  if (error instanceof ApiRequestError && error.status === 404) {
    return {
      message: "当前环境尚未启用受控嵌入模型。请联系平台运维完成模型运行时配置后重试。",
      traceId: error.traceId,
    };
  }
  return undefined;
}

/**
 * 只有当前请求已使同一客户端失去会话时才重启登录，防止旧请求的 401 覆盖已建立的新身份。
 */
export function requiresSessionRestart(error: unknown, hasSession: boolean): boolean {
  return error instanceof ApiRequestError && error.status === 401 && !hasSession;
}

/** 只有仍处于 READY 的来源才能使用已发布产物创建新的索引版本。 */
export function fileSourceOptionLabel(source: GovernedFileDataSourceSummary): string {
  return source.publishedArtifactId !== null && source.status === "READY"
    ? `${source.displayName}（可重建）`
    : source.displayName;
}

function failureDescription(failure?: Failure) {
  if (failure === undefined) {
    return "请稍后重试；如果问题持续存在，请联系平台运维人员。";
  }
  return failure.traceId.length === 0 ? failure.message : `${failure.message}，追踪标识：${failure.traceId}`;
}

function canProbe(source: GovernedDataSourceLifecycleResult): boolean {
  return source.status === "DRAFT"
    || (source.status === "FAILED" && source.failedOperation === "PROBE");
}

function canSynchronize(source: GovernedDataSourceLifecycleResult): boolean {
  return (source.status === "READY" && hasSyncArtifact(source) === false)
    || (source.status === "FAILED" && source.failedOperation === "SYNCHRONIZATION");
}

function canPublish(
  source: GovernedDataSourceLifecycleResult,
  publishedArtifactId?: string,
): boolean {
  return source.status === "READY"
    && hasSyncArtifact(source)
    && source.qualityStatus !== "BLOCK"
    && publishedArtifactId !== source.syncArtifactId;
}

function canBuildIndex(
  source: GovernedDataSourceLifecycleResult,
  publishedArtifactId?: string,
): boolean {
  return source.status === "READY"
    && publishedArtifactId !== undefined;
}

function hasSyncArtifact(source: GovernedDataSourceLifecycleResult): boolean {
  return typeof source.syncArtifactId === "string" && source.syncArtifactId.length > 0;
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function FailureMessage({ failure }: { failure: Failure }) {
  return (
    <MessageBar intent="error">
      <MessageBarBody>
        <MessageBarTitle>操作未完成</MessageBarTitle>
        {failureDescription(failure)}
      </MessageBarBody>
    </MessageBar>
  );
}
