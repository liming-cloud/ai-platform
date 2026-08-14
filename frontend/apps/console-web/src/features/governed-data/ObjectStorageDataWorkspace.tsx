import {
  Badge,
  Button,
  Field,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Select,
  Text,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlPlaneClient,
  type GovernedDataSourceLifecycleResult,
  type IndexVersion,
  type KnowledgeSpace,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { modelRuntimeFailure, requiresSessionRestart } from "./GovernedFileDataWorkspace";
import { EmbeddingModelConfigurationPicker } from "../shared/EmbeddingModelConfigurationPicker";
import { PagedResourceNavigation } from "../shared/PagedResourceNavigation";

interface Failure {
  message: string;
  traceId: string;
}

type ViewState = "unauthenticated" | "loading" | "ready" | "error";

/**
 * 知识管理员的 OBJECT_STORAGE 受治理来源任务。
 *
 * <p>页面只接收部署已签发的范围标识与选择修订；不会读取或展示对象位置、桶名、对象键、正文或凭据。</p>
 */
export function ObjectStorageDataWorkspace({
  client,
  route,
  sessionRevision,
  onSessionChanged,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "object-storage" }>;
  sessionRevision: number;
  onSessionChanged: () => void;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [spaceHasPrevious, setSpaceHasPrevious] = useState(false);
  const [spaceHasNext, setSpaceHasNext] = useState(false);
  const [spaceTotal, setSpaceTotal] = useState(0);
  const [name, setName] = useState("");
  const [rangeId, setRangeId] = useState("");
  const [selectionRevision, setSelectionRevision] = useState("");
  const [source, setSource] = useState<GovernedDataSourceLifecycleResult>();
  const [publishedArtifactId, setPublishedArtifactId] = useState<string>();
  const [indexVersion, setIndexVersion] = useState<IndexVersion>();
  const [failure, setFailure] = useState<Failure>();
  const [submitting, setSubmitting] = useState(false);
  const [probing, setProbing] = useState(false);
  const [synchronizing, setSynchronizing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [buildingIndex, setBuildingIndex] = useState(false);
  const knowledgeSpaceId = route.knowledgeSpaceId ?? "";

  const resetProtectedTaskState = useCallback(() => {
    setSpaces([]);
    setSpaceHasPrevious(false);
    setSpaceHasNext(false);
    setSpaceTotal(0);
    setName("");
    setRangeId("");
    setSelectionRevision("");
    setSource(undefined);
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
        onRouteChange({ ...route, knowledgeSpaceId: selectedKnowledgeSpaceId });
      }
      setViewState("ready");
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
        setViewState("error");
      }
    }
  }, [client, knowledgeSpaceId, onRouteChange, resetProtectedTaskState, restartExpiredSession, route]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const revision = Number(selectionRevision);
    if (knowledgeSpaceId.length === 0 || name.trim().length === 0 || rangeId.trim().length === 0
      || !Number.isSafeInteger(revision) || revision < 1) {
      setFailure({ message: "请选择知识空间，并填写来源名称、平台签发范围标识和正整数选择修订。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      setSource(await client.registerObjectStorageDataSource(
        knowledgeSpaceId,
        name.trim(),
        rangeId.trim(),
        revision,
      ));
      setPublishedArtifactId(undefined);
      setIndexVersion(undefined);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function synchronize() {
    if (source === undefined || !canSynchronizeObjectStorage(source)) {
      return;
    }
    setSynchronizing(true);
    setFailure(undefined);
    try {
      setSource(await client.synchronizeObjectStorageDataSource(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      ));
      setPublishedArtifactId(undefined);
      setIndexVersion(undefined);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setSynchronizing(false);
    }
  }

  async function probe() {
    if (source === undefined || !canProbeObjectStorage(source)) {
      return;
    }
    setProbing(true);
    setFailure(undefined);
    try {
      setSource(await client.probeObjectStorageDataSource(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      ));
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setProbing(false);
    }
  }

  async function publish() {
    if (source === undefined || source.syncArtifactId === null
      || !canPublishObjectStorageArtifact(source, publishedArtifactId)) {
      return;
    }
    setPublishing(true);
    setFailure(undefined);
    try {
      const result = await client.publishObjectStorageSyncArtifact(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.syncArtifactId,
        source.version,
      );
      setSource(result);
      setPublishedArtifactId(source.syncArtifactId);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setPublishing(false);
    }
  }

  async function buildIndex() {
    if (source === undefined || !canBuildObjectStorageIndex(source, publishedArtifactId)
      || route.embeddingModelConfigurationId === undefined) {
      if (route.embeddingModelConfigurationId === undefined) {
        setFailure({ message: "请选择可用嵌入模型后再创建索引版本。", traceId: "" });
      }
      return;
    }
    setBuildingIndex(true);
    setFailure(undefined);
    try {
      const created = await client.createIndexVersion(
        source.knowledgeSpaceId,
        route.embeddingModelConfigurationId,
      );
      setIndexVersion(created);
      await waitForIndex(source.knowledgeSpaceId, created.id);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(modelRuntimeFailure(error) ?? toFailure(error));
      }
    } finally {
      setBuildingIndex(false);
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
    setBuildingIndex(true);
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
      setBuildingIndex(false);
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

  if (viewState === "unauthenticated") {
    return <StatePanel kind="stale" title="浏览器会话已经失效" description="受保护的对象存储任务已清除，请返回统一登录入口。" actionLabel="返回登录入口" onAction={() => window.location.assign("/")} />;
  }
  if (viewState === "loading") {
    return <StatePanel kind="loading" title="正在加载对象存储任务" description="正在读取当前租户可用知识空间。" />;
  }
  if (viewState === "error") {
    return <StatePanel kind="error" title="对象存储任务暂时不可用" description={failureDescription(failure)} actionLabel="重新加载" onAction={() => void load()} />;
  }

  return (
    <div className="governed-file-workspace">
      <div className="governance-toolbar">
        <Text>仅接收平台签发范围标识与选择修订；对象位置、桶名、对象键、正文和凭据均不会展示或提交。</Text>
        <div className="task-actions"><Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => void load()}>刷新</Button><Button appearance="subtle" onClick={() => void logout()}>退出登录</Button></div>
      </div>
      {failure ? <FailureMessage failure={failure} /> : null}
      {spaces.length === 0 ? <StatePanel kind="empty" title="尚无可用知识空间" description="先在“首份文本发布”任务创建知识空间，再登记平台对象存储范围。" /> : (
        <section className="governed-file-section">
          <header className="task-heading"><Title3>登记平台对象存储范围</Title3><Text>范围标识由部署签发；控制台不会枚举平台范围或读取其内部位置。</Text></header>
          <form className="governed-file-form" onSubmit={register}>
            <Field label="知识空间" required><Select value={knowledgeSpaceId} disabled={submitting || source !== undefined} onChange={(_, data) => {
              setSource(undefined);
              setPublishedArtifactId(undefined);
              setIndexVersion(undefined);
              onRouteChange({ ...route, knowledgeSpaceId: data.value || undefined });
            }}>{spaces.map((space) => <option key={space.id} value={space.id}>{space.name}</option>)}</Select></Field>
            <PagedResourceNavigation
              label="知识空间"
              page={route.knowledgeSpacePage}
              total={spaceTotal}
              hasPrevious={spaceHasPrevious}
              hasNext={spaceHasNext}
              disabled={submitting || source !== undefined}
              onPrevious={() => {
                setSource(undefined);
                setPublishedArtifactId(undefined);
                setIndexVersion(undefined);
                onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage - 1, knowledgeSpaceId: undefined });
              }}
              onNext={() => {
                setSource(undefined);
                setPublishedArtifactId(undefined);
                setIndexVersion(undefined);
                onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage + 1, knowledgeSpaceId: undefined });
              }}
            />
            <Field label="来源名称" required><input className="governed-file-name" value={name} disabled={submitting || source !== undefined} onChange={(event) => setName(event.target.value)} /></Field>
            <Field label="平台签发范围标识" required><input className="governed-file-name" value={rangeId} disabled={submitting || source !== undefined} onChange={(event) => setRangeId(event.target.value)} /></Field>
            <Field label="范围选择修订" required><input className="governed-file-name" inputMode="numeric" value={selectionRevision} disabled={submitting || source !== undefined} onChange={(event) => setSelectionRevision(event.target.value)} /></Field>
            {source === undefined ? <Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在登记" : "登记对象存储范围"}</Button> : null}
          </form>
          {source ? <article className="governed-file-result">
            <div><Title3>{name || "对象存储来源"}</Title3><Text>数据源编号：{source.dataSourceId}</Text></div>
            <Badge appearance="tint" color="warning">{source.status}</Badge>
            {canBuildObjectStorageIndex(source, publishedArtifactId) ? <EmbeddingModelConfigurationPicker
              client={client}
              page={route.embeddingModelConfigurationPage}
              selectedId={route.embeddingModelConfigurationId}
              disabled={probing || synchronizing || publishing || buildingIndex}
              onChange={(embeddingModelConfigurationPage, embeddingModelConfigurationId) => onRouteChange({
                ...route,
                embeddingModelConfigurationPage,
                embeddingModelConfigurationId,
              })}
              onSessionExpired={restartExpiredSession}
            /> : null}
            <Text>{source.syncArtifactId === null ? "登记完成，可同步冻结对象集合。" : `同步完成，质量状态：${source.qualityStatus}。`}</Text>
            <div className="task-actions">
              {canProbeObjectStorage(source) ? <Button appearance="primary" disabled={probing || synchronizing || publishing} onClick={() => void probe()}>{probing ? "正在探测" : "探测冻结范围"}</Button> : null}
              {canSynchronizeObjectStorage(source) ? <Button appearance="primary" disabled={probing || synchronizing || publishing} onClick={() => void synchronize()}>{synchronizing ? "正在同步" : "同步冻结对象集合"}</Button> : null}
              {canPublishObjectStorageArtifact(source, publishedArtifactId) && source.syncArtifactId !== null ? <Button appearance="primary" disabled={probing || synchronizing || publishing} onClick={() => void publish()}>{publishing ? "正在发布" : "发布同步产物"}</Button> : null}
              {publishedArtifactId === source.syncArtifactId ? <Text>同步产物已发布。</Text> : null}
              {canBuildObjectStorageIndex(source, publishedArtifactId) ? <Button appearance="primary" disabled={probing || synchronizing || publishing || buildingIndex} onClick={() => void buildIndex()}>{buildingIndex ? "正在建立索引" : "创建索引版本"}</Button> : null}
              {indexVersion ? <Text>索引版本：{indexVersion.status}{indexVersion.failureCode ? `（${indexVersion.failureCode}）` : ""}</Text> : null}
              {indexVersion?.status === "READY" ? <Button appearance="primary" disabled={probing || synchronizing || publishing || buildingIndex} onClick={() => void publishIndex()}>发布索引版本</Button> : null}
            </div>
          </article> : null}
        </section>
      )}
    </div>
  );
}

/** 只有未生成当前产物或可恢复同步失败的来源可再次同步。 */
export function canProbeObjectStorage(source: GovernedDataSourceLifecycleResult): boolean {
  return source.status === "DRAFT"
    || (source.status === "FAILED" && source.failedOperation === "PROBE");
}

/** 只有探测通过或可恢复同步失败的来源可继续同步。 */
export function canSynchronizeObjectStorage(source: GovernedDataSourceLifecycleResult): boolean {
  return (source.status === "READY" && source.syncArtifactId === null)
    || (source.status === "FAILED" && source.failedOperation === "SYNCHRONIZATION");
}

/** 只允许当前非阻断质量产物被显式发布。 */
export function canPublishObjectStorageArtifact(source: GovernedDataSourceLifecycleResult, publishedArtifactId?: string): boolean {
  return source.status === "READY" && source.syncArtifactId !== null
    && source.qualityStatus !== "BLOCK" && publishedArtifactId !== source.syncArtifactId;
}

/** 只允许当前已发布同步产物创建新索引。 */
export function canBuildObjectStorageIndex(source: GovernedDataSourceLifecycleResult, publishedArtifactId?: string): boolean {
  return source.status === "READY" && source.syncArtifactId !== null
    && publishedArtifactId === source.syncArtifactId;
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));
}

function FailureMessage({ failure }: { failure: Failure }) {
  return <MessageBar intent="error"><MessageBarBody><MessageBarTitle>操作未完成</MessageBarTitle>{failureDescription(failure)}</MessageBarBody></MessageBar>;
}

function failureDescription(failure?: Failure): string {
  if (failure === undefined) return "请稍后重试。";
  return failure.traceId.length === 0 ? failure.message : `${failure.message}，追踪标识：${failure.traceId}`;
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) return { message: error.message, traceId: error.traceId };
  return { message: error instanceof Error ? error.message : "请求失败", traceId: "" };
}
