import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
} from "react";
import {
  ApiRequestError,
  ControlPlaneClient,
  type KnowledgeSpace,
} from "../../api/control-plane-client";
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
  LoginForm,
  PublishReview,
  SpaceForm,
  SpacePicker,
  WorkflowFeedback,
} from "./PublicationViews";

export function KnowledgePublicationFlow({
  client: providedClient,
  sessionRevision: sharedSessionRevision = 0,
  onSessionChanged,
}: {
  client?: ControlPlaneClient;
  sessionRevision?: number;
  onSessionChanged?: () => void;
}) {
  const ownedClient = useMemo(() => new ControlPlaneClient(), []);
  const client = providedClient ?? ownedClient;
  const [workflow, dispatch] = useReducer(reduceWorkflow, initialWorkflow);
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [selectedSpaceId, setSelectedSpaceId] = useState("");
  const [creatingSpace, setCreatingSpace] = useState(false);
  const [content, setContent] = useState("");
  const requestKeys = useRef(
    new Map<string, { fingerprint: string; key: string }>(),
  );
  const mounted = useRef(true);
  const sessionRevision = useRef(0);

  useEffect(() => () => {
    mounted.current = false;
  }, []);

  const loadSpaces = useCallback(async () => {
    dispatch({ type: "submitting" });
    try {
      const page = await client.listKnowledgeSpaces();
      setSpaces(page.items);
      setSelectedSpaceId(page.items[0]?.id ?? "");
      setCreatingSpace(page.items.length === 0);
      dispatch({ type: "authenticated" });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }, [client]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      if (!client.hasSession() && workflow.stage !== "login") {
        setSpaces([]);
        setSelectedSpaceId("");
        setCreatingSpace(false);
        setContent("");
        requestKeys.current.clear();
        dispatch({ type: "signed-out" });
        return;
      }
      if (workflow.stage === "login" && client.hasSession()) {
        void loadSpaces();
      }
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [client, loadSpaces, sharedSessionRevision, workflow.stage]);

  async function login(credentials: {
    tenantCode: string;
    loginName: string;
    password: string;
  }) {
    dispatch({ type: "submitting" });
    try {
      await client.login(credentials);
      await loadSpaces();
      onSessionChanged?.();
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  async function createSpace(input: {
    name: string;
    description: string;
  }) {
    dispatch({ type: "submitting" });
    try {
      const space = await client.createKnowledgeSpace(
        input,
        stableRequestKey(requestKeys.current, "create-space", input),
      );
      requestKeys.current.delete("create-space");
      dispatch({
        type: "space-ready",
        spaceId: space.id,
        spaceName: space.name,
      });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  function useSelectedSpace() {
    const space = spaces.find((candidate) => candidate.id === selectedSpaceId);
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
    if (workflow.space === undefined) {
      return;
    }
    const revision = sessionRevision.current;
    dispatch({ type: "submitting" });
    try {
      const configurations = await client.listModelConfigurations();
      const embedding = configurations.items.find(
        (configuration) =>
          configuration.modelType === "EMBEDDING"
          && configuration.status === "ACTIVE"
          && configuration.credentialConfigured,
      );
      if (embedding === undefined) {
        throw new Error("请先在模型配置中启用一个可用的嵌入模型");
      }
      const created = await client.createIndexVersion(
        workflow.space.id,
        embedding.id,
        stableRequestKey(requestKeys.current, "create-index", {
          knowledgeSpaceId: workflow.space.id,
          sourceVersion: workflow.source?.version,
          embeddingModelConfigurationId: embedding.id,
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

  async function signOut() {
    sessionRevision.current += 1;
    try {
      await client.logout();
    } finally {
      setSpaces([]);
      setSelectedSpaceId("");
      setCreatingSpace(false);
      setContent("");
      requestKeys.current.clear();
      dispatch({ type: "signed-out" });
      onSessionChanged?.();
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
          <LoginForm busy={workflow.busy} onSubmit={login} />
        ) : null}

        {workflow.stage === "space" ? (
          creatingSpace ? (
            <SpaceForm
              busy={workflow.busy}
              canUseExisting={spaces.length > 0}
              onCancel={() => setCreatingSpace(false)}
              onSubmit={createSpace}
            />
          ) : (
            <SpacePicker
              spaces={spaces}
              selectedId={selectedSpaceId}
              busy={workflow.busy}
              onSelected={setSelectedSpaceId}
              onContinue={useSelectedSpace}
              onCreate={() => setCreatingSpace(true)}
            />
          )
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
            onBuild={buildIndex}
            onPublish={publishIndex}
          />
        ) : null}

        {workflow.stage === "complete" ? (
          <Completion
            spaceName={workflow.space?.name ?? ""}
            sourceName={workflow.source?.name ?? ""}
            chunkCount={workflow.indexVersion?.chunkCount ?? 0}
            onSignOut={signOut}
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
