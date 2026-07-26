import { useMemo, useReducer, useRef, useState } from "react";
import {
  ApiRequestError,
  ControlPlaneClient,
  type KnowledgeSpace,
} from "../../api/control-plane-client";
import {
  initialWorkflow,
  reduceWorkflow,
  stableRequestKey,
} from "./publication-workflow";
import { PublicationProgress } from "./PublicationProgress";
import {
  Completion,
  ContentForm,
  LoginForm,
  PublishReview,
  SpaceForm,
  SpacePicker,
  WorkflowFeedback,
} from "./PublicationViews";

export function KnowledgePublicationFlow() {
  const client = useMemo(() => new ControlPlaneClient(), []);
  const [workflow, dispatch] = useReducer(reduceWorkflow, initialWorkflow);
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [selectedSpaceId, setSelectedSpaceId] = useState("");
  const [creatingSpace, setCreatingSpace] = useState(false);
  const [content, setContent] = useState("");
  const requestKeys = useRef(
    new Map<string, { fingerprint: string; key: string }>(),
  );

  async function login(credentials: {
    tenantCode: string;
    loginName: string;
    password: string;
  }) {
    dispatch({ type: "submitting" });
    try {
      await client.login(credentials);
      const page = await client.listKnowledgeSpaces();
      setSpaces(page.items);
      setSelectedSpaceId(page.items[0]?.id ?? "");
      setCreatingSpace(page.items.length === 0);
      dispatch({ type: "authenticated" });
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
      dispatch({ type: "published", version: published.version });
    } catch (error) {
      dispatchFailure(dispatch, error);
    }
  }

  async function signOut() {
    try {
      await client.logout();
    } finally {
      setSpaces([]);
      setSelectedSpaceId("");
      setCreatingSpace(false);
      setContent("");
      requestKeys.current.clear();
      dispatch({ type: "signed-out" });
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

        {workflow.stage === "complete" ? (
          <Completion
            spaceName={workflow.space?.name ?? ""}
            sourceName={workflow.source?.name ?? ""}
            onSignOut={signOut}
          />
        ) : null}
      </section>
    </div>
  );
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
