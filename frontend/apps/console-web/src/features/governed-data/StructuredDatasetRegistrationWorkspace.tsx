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
  type StructuredDatasetFieldInput,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { modelRuntimeFailure, requiresSessionRestart } from "./GovernedFileDataWorkspace";
import { PagedResourceNavigation } from "../shared/PagedResourceNavigation";

interface Failure {
  message: string;
  traceId: string;
}

type ViewState = "unauthenticated" | "loading" | "ready" | "error";

const LOGICAL_TYPES = ["TEXT", "NUMBER", "BOOLEAN", "DATE", "DATETIME"] as const;
const SENSITIVITY_LEVELS = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"] as const;

/** 初始映射只提供可检索的主内容与可选标题，管理员可在登记前按冻结契约增删字段。 */
function defaultFields(): StructuredDatasetFieldInput[] {
  return [
    { fieldName: "content", logicalType: "TEXT", sensitivityLevel: "PUBLIC" },
    { fieldName: "title", logicalType: "TEXT", sensitivityLevel: "PUBLIC" },
  ];
}

/**
 * 知识管理员的 STRUCTURED_DATASET 登记任务。
 *
 * <p>页面只提交冻结映射和私有 CSV；不会读取对象位置、正文、连接参数或任何敏感字段值。同步和发布只选择既有资源及其修订，质量结论始终由受控领域服务生成。</p>
 */
export function StructuredDatasetRegistrationWorkspace({
  client,
  route,
  sessionRevision,
  onSessionChanged,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "structured-dataset" }>;
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
  const [mappingVersion, setMappingVersion] = useState("dataset-v1");
  const [fields, setFields] = useState<StructuredDatasetFieldInput[]>(defaultFields);
  const [contentField, setContentField] = useState("content");
  const [titleField, setTitleField] = useState("title");
  const [rowFilter, setRowFilter] = useState<"ALL_ROWS" | "NON_BLANK_CONTENT">("NON_BLANK_CONTENT");
  const [csv, setCsv] = useState<File>();
  const [fileInputKey, setFileInputKey] = useState(0);
  const [source, setSource] = useState<GovernedDataSourceLifecycleResult>();
  const [failure, setFailure] = useState<Failure>();
  const [submitting, setSubmitting] = useState(false);
  const [synchronizing, setSynchronizing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishedArtifactId, setPublishedArtifactId] = useState<string>();
  const [indexVersion, setIndexVersion] = useState<IndexVersion>();
  const [buildingIndex, setBuildingIndex] = useState(false);
  const knowledgeSpaceId = route.knowledgeSpaceId ?? "";

  const resetProtectedTaskState = useCallback(() => {
    setSpaces([]);
    setSpaceHasPrevious(false);
    setSpaceHasNext(false);
    setSpaceTotal(0);
    setName("");
    setMappingVersion("dataset-v1");
    setFields(defaultFields());
    setContentField("content");
    setTitleField("title");
    setRowFilter("NON_BLANK_CONTENT");
    setCsv(undefined);
    setFileInputKey((current) => current + 1);
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
    if (knowledgeSpaceId.length === 0 || name.trim().length === 0 || mappingVersion.trim().length === 0 || csv === undefined) {
      setFailure({ message: "请选择知识空间，填写数据集名称和映射版本，并选择 UTF-8 CSV。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      const registered = await client.registerStructuredDataset({
        knowledgeSpaceId,
        name: name.trim(),
        mappingVersion: mappingVersion.trim(),
        contentField,
        titleField: titleField || null,
        fields,
        rowFilter,
      }, csv);
      setSource(registered);
      setCsv(undefined);
      setFileInputKey((current) => current + 1);
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
    if (source === undefined) {
      return;
    }
    setSynchronizing(true);
    setFailure(undefined);
    try {
      const synchronized = await client.synchronizeStructuredDataset(
        source.knowledgeSpaceId,
        source.dataSourceId,
        source.version,
      );
      setSource(synchronized);
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

  async function publish() {
    if (source?.syncArtifactId === null || source?.syncArtifactId === undefined) {
      return;
    }
    const artifactId = source.syncArtifactId;
    setPublishing(true);
    setFailure(undefined);
    try {
      const published = await client.publishStructuredDatasetSyncArtifact(
        source.knowledgeSpaceId,
        source.dataSourceId,
        artifactId,
        source.version,
      );
      setSource({
        ...published,
        syncArtifactId: artifactId,
        qualityStatus: source.qualityStatus,
      });
      setPublishedArtifactId(artifactId);
    } catch (error) {
      if (!restartExpiredSession(error)) {
        setFailure(toFailure(error));
      }
    } finally {
      setPublishing(false);
    }
  }

  async function buildIndex() {
    if (source === undefined || !canBuildStructuredDatasetIndex(source, publishedArtifactId)) {
      return;
    }
    setBuildingIndex(true);
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

  function updateField(index: number, update: Partial<StructuredDatasetFieldInput>) {
    setFields((current) => current.map((field, fieldIndex) => (
      fieldIndex === index ? { ...field, ...update } : field
    )));
  }

  function addField() {
    setFields((current) => [...current, {
      fieldName: "",
      logicalType: "TEXT",
      sensitivityLevel: "PUBLIC",
    }]);
  }

  function removeField(index: number) {
    const removed = fields[index];
    if (removed === undefined || fields.length === 1) {
      return;
    }
    const next = fields.filter((_, fieldIndex) => fieldIndex !== index);
    setFields(next);
    if (contentField === removed.fieldName) {
      setContentField(next[0]?.fieldName ?? "");
    }
    if (titleField === removed.fieldName) {
      setTitleField("");
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
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="受保护的数据集任务已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
    );
  }

  if (viewState === "loading") {
    return <StatePanel kind="loading" title="正在加载数据集登记任务" description="正在读取当前租户可用知识空间。" />;
  }

  if (viewState === "error") {
    return <StatePanel kind="error" title="数据集登记暂时不可用" description={failureDescription(failure)} actionLabel="重新加载" onAction={() => void load()} />;
  }

  return (
    <div className="governed-file-workspace">
      <div className="governance-toolbar">
        <Text>仅登记平台私有 UTF-8 CSV 与冻结字段映射；同步只读取冻结版本，质量由领域规则生成，发布仅选择 PASS 或 WARN 产物。</Text>
        <div className="task-actions">
          <Button appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={() => void load()}>刷新</Button>
          <Button appearance="subtle" onClick={() => void logout()}>退出登录</Button>
        </div>
      </div>
      {failure ? <FailureMessage failure={failure} /> : null}
      {spaces.length === 0 ? (
        <StatePanel kind="empty" title="尚无可用知识空间" description="先在“首份文本发布”任务创建知识空间，再返回此处登记结构化数据集。" />
      ) : (
        <section className="governed-file-section">
          <header className="task-heading">
            <Title3>登记私有 CSV 结构化数据集</Title3>
            <Text>对象键、桶名、摘要、CSV 正文及敏感字段均不会在控制台展示或回显。</Text>
          </header>
          <form className="governed-file-form" onSubmit={register}>
            <Field label="知识空间" required>
              <Select
                value={knowledgeSpaceId}
                disabled={submitting || source !== undefined}
                onChange={(_, data) => {
                  setSource(undefined);
                  setPublishedArtifactId(undefined);
                  setIndexVersion(undefined);
                  onRouteChange({ ...route, knowledgeSpaceId: data.value || undefined });
                }}
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
            <Field label="数据集名称" required>
              <input className="governed-file-name" value={name} disabled={submitting || source !== undefined} onChange={(event) => setName(event.target.value)} />
            </Field>
            <Field label="映射版本" required>
              <input className="governed-file-name" value={mappingVersion} disabled={submitting || source !== undefined} onChange={(event) => setMappingVersion(event.target.value)} />
            </Field>
            <Field label="字段映射" required>
              <div className="task-actions">
                {fields.map((field, index) => (
                  <div key={`${index}-${field.fieldName}`} className="task-actions">
                    <input aria-label={`字段名称 ${index + 1}`} className="governed-file-name" value={field.fieldName} disabled={submitting || source !== undefined} onChange={(event) => updateField(index, { fieldName: event.target.value })} />
                    <Select aria-label={`字段逻辑类型 ${index + 1}`} value={field.logicalType} disabled={submitting || source !== undefined} onChange={(_, data) => updateField(index, { logicalType: data.value as StructuredDatasetFieldInput["logicalType"] })}>
                      {LOGICAL_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                    </Select>
                    <Select aria-label={`字段敏感级别 ${index + 1}`} value={field.sensitivityLevel} disabled={submitting || source !== undefined} onChange={(_, data) => updateField(index, { sensitivityLevel: data.value as StructuredDatasetFieldInput["sensitivityLevel"] })}>
                      {SENSITIVITY_LEVELS.map((level) => <option key={level} value={level}>{level}</option>)}
                    </Select>
                    <Button appearance="subtle" type="button" disabled={submitting || source !== undefined || fields.length === 1} onClick={() => removeField(index)}>删除字段</Button>
                  </div>
                ))}
                <Button appearance="subtle" type="button" disabled={submitting || source !== undefined} onClick={addField}>新增字段</Button>
              </div>
            </Field>
            <Field label="主内容字段" required>
              <Select value={contentField} disabled={submitting || source !== undefined} onChange={(_, data) => setContentField(data.value)}>
                {fields.map((field, index) => <option key={`${index}-${field.fieldName}`} value={field.fieldName}>{field.fieldName || "未命名字段"}</option>)}
              </Select>
            </Field>
            <Field label="标题字段">
              <Select value={titleField} disabled={submitting || source !== undefined} onChange={(_, data) => setTitleField(data.value)}>
                <option value="">不设置标题字段</option>
                {fields.map((field, index) => <option key={`${index}-${field.fieldName}`} value={field.fieldName}>{field.fieldName || "未命名字段"}</option>)}
              </Select>
            </Field>
            <Field label="行过滤" required>
              <Select value={rowFilter} disabled={submitting || source !== undefined} onChange={(_, data) => setRowFilter(data.value as "ALL_ROWS" | "NON_BLANK_CONTENT")}>
                <option value="NON_BLANK_CONTENT">跳过空主内容行</option>
                <option value="ALL_ROWS">保留全部数据行</option>
              </Select>
            </Field>
            <Field label="UTF-8 CSV" required>
              <input key={fileInputKey} type="file" accept=".csv,text/csv" disabled={submitting || source !== undefined} onChange={(event) => setCsv(event.currentTarget.files?.[0])} />
            </Field>
            {source === undefined ? <Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在登记" : "登记私有 CSV 数据集"}</Button> : null}
          </form>
          {source ? (
            <article className="governed-file-result">
              <div>
                <Title3>{name || "结构化数据集"}</Title3>
                <Text>数据源编号：{source.dataSourceId}</Text>
              </div>
              <Badge appearance="tint" color="warning">{source.status}</Badge>
              {source.syncArtifactId === null ? (
                <Text>登记完成。CSV 内容、对象位置、摘要和敏感字段不会展示；可开始同步冻结版本。</Text>
              ) : (
                <Text>同步完成，质量状态：{source.qualityStatus}。CSV 内容、对象位置、摘要和敏感字段不会展示。</Text>
              )}
              <div className="task-actions">
                {(source.status === "READY" || (source.status === "FAILED" && source.failedOperation === "SYNCHRONIZATION")) && (source.syncArtifactId === null || publishedArtifactId === source.syncArtifactId) ? (
                  <Button appearance="primary" disabled={synchronizing || publishing} onClick={() => void synchronize()}>
                    {synchronizing ? "正在同步" : "同步冻结 CSV"}
                  </Button>
                ) : null}
                {source.status === "READY" && source.syncArtifactId !== null && source.qualityStatus !== "BLOCK" && publishedArtifactId !== source.syncArtifactId ? (
                  <Button appearance="primary" disabled={synchronizing || publishing} onClick={() => void publish()}>
                    {publishing ? "正在发布" : "发布同步产物"}
                  </Button>
                ) : null}
                {publishedArtifactId === source.syncArtifactId ? <Text>同步产物已发布。</Text> : null}
                {canBuildStructuredDatasetIndex(source, publishedArtifactId) ? (
                  <Button appearance="primary" disabled={synchronizing || publishing || buildingIndex} onClick={() => void buildIndex()}>
                    {buildingIndex ? "正在建立索引" : "创建索引版本"}
                  </Button>
                ) : null}
                {indexVersion ? <Text>索引版本：{indexVersion.status}{indexVersion.failureCode ? `（${indexVersion.failureCode}）` : ""}</Text> : null}
                {indexVersion?.status === "READY" ? (
                  <Button appearance="primary" disabled={synchronizing || publishing || buildingIndex} onClick={() => void publishIndex()}>
                    发布索引版本
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

/** 只有当前 READY 的结构化数据集可使用已发布同步产物创建新的索引版本。 */
export function canBuildStructuredDatasetIndex(
  source: GovernedDataSourceLifecycleResult | undefined,
  publishedArtifactId?: string,
): boolean {
  return source?.status === "READY"
    && publishedArtifactId !== undefined
    && publishedArtifactId === source.syncArtifactId;
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function toFailure(error: unknown): Failure {
  if (error instanceof ApiRequestError) {
    return { message: error.message, traceId: error.traceId };
  }
  return { message: "无法连接控制平面，请检查网络后重试", traceId: "" };
}

function failureDescription(failure?: Failure) {
  if (failure === undefined) {
    return "请稍后重试；如果问题持续存在，请联系平台运维人员。";
  }
  return failure.traceId.length === 0 ? failure.message : `${failure.message}，追踪标识：${failure.traceId}`;
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
