import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
} from "react";
import { StatePanel } from "@foundation/design-system";
import {
  ApiRequestError,
  ControlPlaneClient,
  type KnowledgeSpace,
} from "../../api/control-plane-client";
import {
  knowledgeDataTaskRoute,
  resolveKnowledgeSpaceIdOnPage,
  type ConsoleRoute,
} from "../../session/console-route";
import { EmbeddingModelConfigurationPicker } from "../shared/EmbeddingModelConfigurationPicker";
import {
  initialWorkflow,
  reduceWorkflow,
  stableRequestKey,
  type PublicationWorkflow,
} from "./publication-workflow";
import { PublicationProgress } from "./PublicationProgress";
import {
  Completion,
  ContentForm,
  IndexBuildReview,
  PublishReview,
  SpacePicker,
  WorkflowFeedback,
} from "./PublicationViews";

export function KnowledgePublicationFlow({
  client: providedClient,
  route,
  sessionRevision: sharedSessionRevision = 0,
  onRouteChange,
}: {
  client?: ControlPlaneClient;
  route: Extract<ConsoleRoute, { task: "first-publication" }>;
  sessionRevision?: number;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const ownedClient = useMemo(() => new ControlPlaneClient(), []);
  const client = providedClient ?? ownedClient;
  const [workflow, dispatch] = useReducer(reduceWorkflow, initialWorkflow);
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [spaceTotal, setSpaceTotal] = useState(0);
  const [spaceHasPrevious, setSpaceHasPrevious] = useState(false);
  const [spaceHasNext, setSpaceHasNext] = useState(false);
  const [canBuildIndex, setCanBuildIndex] = useState(false);
  const [content, setContent] = useState("");
  const requestKeys = useRef(
    new Map<string, { fingerprint: string; key: string }>(),
  );
  const mounted = useRef(true);
  const sessionRevision = useRef(0);
  const selectedKnowledgeSpaceId = resolveKnowledgeSpaceIdOnPage(
    route.knowledgeSpaceId, spaces);

  useEffect(() => () => {
    mounted.current = false;
  }, []);

  const loadSpaces = useCallback(async () => {
    dispatch({ type: "submitting" });
    try {
      const page = await client.listKnowledgeSpaces(route.knowledgeSpacePage);
      setSpaces(page.items);
      setSpaceTotal(page.totalElements);
      setSpaceHasPrevious(page.hasPrevious);
      setSpaceHasNext(page.hasNext);
      const selectedSpaceId = resolveKnowledgeSpaceIdOnPage(
        route.knowledgeSpaceId,
        page.items,
      );
      dispatch({ type: "authenticated" });
      if (route.knowledgeSpaceId !== undefined && selectedSpaceId === undefined) {
        dispatch({
          type: "failed",
          code: "KNOWLEDGE_SPACE_SELECTION_NOT_FOUND",
          message: "地址中的知识库不在当前目录页。请返回知识库目录重新选择；系统不会自动切换到其他知识库。",
          traceId: "",
        });
      }
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }, [client, route]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      if (!client.hasSession() && workflow.stage !== "login") {
        setSpaces([]);
        setSpaceTotal(0);
        setSpaceHasPrevious(false);
        setSpaceHasNext(false);
        setContent("");
        requestKeys.current.clear();
        dispatch({ type: "signed-out" });
        return;
      }
      if ((workflow.stage === "login" || workflow.stage === "space") && client.hasSession()) {
        void loadSpaces();
      }
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [client, loadSpaces, sharedSessionRevision, workflow.stage]);

  function useSelectedSpace() {
    const space = spaces.find((candidate) => candidate.id === route.knowledgeSpaceId);
    if (space === undefined) {
      return;
    }
    dispatch({
      type: "space-ready",
      spaceId: space.id,
      spaceName: space.name,
    });
  }

  async function upload(input: { sourceName: string; content: string }) {
    if (workflow.space === undefined) {
      return;
    }
    dispatch({ type: "submitting" });
    try {
      let source = workflow.source;
      if (source === undefined) {
        const created = await client.createTextDataSource(
          workflow.space.id,
          input.sourceName,
          stableRequestKey(requestKeys.current, "create-source", {
            knowledgeSpaceId: workflow.space.id,
            name: input.sourceName,
          }),
        );
        requestKeys.current.delete("create-source");
        source = {
          id: created.id,
          name: created.name,
          version: created.version,
        };
        dispatch({
          type: "source-ready",
          sourceId: source.id,
          sourceName: source.name,
          version: source.version,
        });
      }
      const uploaded = await client.uploadTextContent(
        workflow.space.id,
        source.id,
        source.version,
        input.content,
        stableRequestKey(requestKeys.current, "upload-content", {
          knowledgeSpaceId: workflow.space.id,
          dataSourceId: source.id,
          expectedVersion: source.version,
          content: input.content,
        }),
      );
      requestKeys.current.delete("upload-content");
      setContent(input.content);
      dispatch({ type: "content-ready", version: uploaded.version });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  async function publish() {
    if (workflow.space === undefined || workflow.source === undefined) {
      return;
    }
    dispatch({ type: "submitting" });
    try {
      const published = await client.publishTextDataSource(
        workflow.space.id,
        workflow.source.id,
        workflow.source.version,
        stableRequestKey(requestKeys.current, "publish-source", {
          knowledgeSpaceId: workflow.space.id,
          dataSourceId: workflow.source.id,
          expectedVersion: workflow.source.version,
        }),
      );
      requestKeys.current.delete("publish-source");
      dispatch({ type: "source-published", version: published.version });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  async function buildIndex() {
    if (workflow.space === undefined || route.embeddingModelConfigurationId === undefined) {
      return;
    }
    const revision = sessionRevision.current;
    dispatch({ type: "submitting" });
    try {
      const created = await client.createIndexVersion(
        workflow.space.id,
        route.embeddingModelConfigurationId,
        stableRequestKey(requestKeys.current, "create-index", {
          knowledgeSpaceId: workflow.space.id,
          sourceVersion: workflow.source?.version,
          embeddingModelConfigurationId: route.embeddingModelConfigurationId,
        }),
      );
      requestKeys.current.delete("create-index");
      updateIndex(created);
      await waitForIndex(workflow.space.id, created.id, revision);
    } catch (error) {
      if (mounted.current && revision === sessionRevision.current) {
        dispatchFailure(dispatch, error);
      }
    }
  }

  async function waitForIndex(
    knowledgeSpaceId: string,
    indexVersionId: string,
    revision: number,
  ) {
    for (let attempt = 0; attempt < 90; attempt += 1) {
      if (!mounted.current || revision !== sessionRevision.current) {
        return;
      }
      const page = await client.listIndexVersions(knowledgeSpaceId);
      const current = page.items.find(
        (candidate) => candidate.id === indexVersionId,
      );
      if (current === undefined) {
        throw new Error("服务端未返回刚创建的索引版本");
      }
      updateIndex(current);
      if (current.status === "READY" || current.status === "FAILED") {
        return;
      }
      await delay(1000);
    }
    throw new Error("索引构建仍在进行，请稍后重新进入页面查看");
  }

  function updateIndex(indexVersion: Parameters<typeof toWorkflowIndex>[0]) {
    dispatch({
      type: "index-updated",
      indexVersion: toWorkflowIndex(indexVersion),
    });
  }

  async function publishIndex() {
    if (
      workflow.space === undefined
      || workflow.indexVersion === undefined
      || workflow.indexVersion.status !== "READY"
    ) {
      return;
    }
    dispatch({ type: "submitting" });
    try {
      const published = await client.publishIndexVersion(
        workflow.space.id,
        workflow.indexVersion.id,
        workflow.indexVersion.version,
        stableRequestKey(requestKeys.current, "publish-index", {
          knowledgeSpaceId: workflow.space.id,
          indexVersionId: workflow.indexVersion.id,
          expectedVersion: workflow.indexVersion.version,
        }),
      );
      requestKeys.current.delete("publish-index");
      dispatch({
        type: "index-published",
        indexVersion: toWorkflowIndex(published),
      });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  return (
    <div className="publication-layout">
      <PublicationProgress stage={workflow.stage} />

      <section className="publication-task" aria-live="polite">
        {workflow.feedback ? (
          <WorkflowFeedback feedback={workflow.feedback} />
        ) : null}

        {workflow.stage === "login" ? (
          <StatePanel
            kind="stale"
            title="浏览器会话已经失效"
            description="受保护任务已被清除，请返回统一登录入口。"
            actionLabel="返回登录入口"
            onAction={() => window.location.assign("/")}
          />
        ) : null}

        {workflow.stage === "space" ? (
          <SpacePicker
            spaces={spaces}
            selectedId={selectedKnowledgeSpaceId ?? ""}
            busy={workflow.busy}
            page={route.knowledgeSpacePage}
            total={spaceTotal}
            hasPrevious={spaceHasPrevious}
            hasNext={spaceHasNext}
            onSelected={(knowledgeSpaceId) => onRouteChange({ ...route, knowledgeSpaceId })}
            onPrevious={() => onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage - 1, knowledgeSpaceId: undefined })}
            onNext={() => onRouteChange({ ...route, knowledgeSpacePage: route.knowledgeSpacePage + 1, knowledgeSpaceId: undefined })}
            onContinue={useSelectedSpace}
            onManage={() => onRouteChange(knowledgeDataTaskRoute(route, "knowledge-bases"))}
          />
        ) : null}

        {workflow.stage === "content" ? (
          <ContentForm
            busy={workflow.busy}
            spaceName={workflow.space?.name ?? ""}
            sourceName={workflow.source?.name}
            initialContent={content}
            onSubmit={upload}
          />
        ) : null}

        {workflow.stage === "publish" ? (
          <PublishReview
            busy={workflow.busy}
            spaceName={workflow.space?.name ?? ""}
            sourceName={workflow.source?.name ?? ""}
            characterCount={content.length}
            onPublish={publish}
          />
        ) : null}

        {workflow.stage === "index" ? (
          <IndexBuildReview
            busy={workflow.busy}
            indexVersion={workflow.indexVersion}
            selectedEmbeddingModelConfigurationId={route.embeddingModelConfigurationId}
            canBuildIndex={canBuildIndex}
            embeddingModelPicker={<EmbeddingModelConfigurationPicker
              client={client}
              page={route.embeddingModelConfigurationPage}
              selectedId={route.embeddingModelConfigurationId}
              disabled={workflow.busy}
              onChange={(embeddingModelConfigurationPage, embeddingModelConfigurationId) => onRouteChange({
                ...route,
                embeddingModelConfigurationPage,
                embeddingModelConfigurationId,
              })}
              onSelectionAvailabilityChange={setCanBuildIndex}
            />}
            onBuild={buildIndex}
            onPublish={publishIndex}
          />
        ) : null}

        {workflow.stage === "complete" ? (
          <Completion
            spaceName={workflow.space?.name ?? ""}
            sourceName={workflow.source?.name ?? ""}
            chunkCount={workflow.indexVersion?.chunkCount ?? 0}
            onReturn={() => onRouteChange(knowledgeDataTaskRoute(route, "knowledge-bases"))}
          />
        ) : null}
      </section>
    </div>
  );
}

function toWorkflowIndex(indexVersion: {
  id: string;
  status: string;
  version: number;
  chunkCount: number;
  failureCode: string | null;
}): NonNullable<PublicationWorkflow["indexVersion"]> {
  if (
    indexVersion.status !== "BUILDING"
    && indexVersion.status !== "READY"
    && indexVersion.status !== "ACTIVE"
    && indexVersion.status !== "FAILED"
  ) {
    throw new Error(`索引状态不受支持：${indexVersion.status}`);
  }
  return {
    id: indexVersion.id,
    status: indexVersion.status,
    version: indexVersion.version,
    chunkCount: indexVersion.chunkCount,
    failureCode: indexVersion.failureCode,
  };
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function dispatchFailure(
  dispatch: React.Dispatch<Parameters<typeof reduceWorkflow>[1]>,
  error: unknown,
) {
  if (error instanceof ApiRequestError) {
    dispatch({
      type: "failed",
      code: error.code,
      message: error.message,
      traceId: error.traceId,
    });
    return;
  }
  dispatch({
    type: "failed",
    code: "NETWORK_FAILURE",
    message: "无法连接控制平面，请检查网络后重试",
    traceId: "",
  });
}
