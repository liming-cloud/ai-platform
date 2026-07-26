import {
  Badge,
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Radio,
  RadioGroup,
  Text,
  Textarea,
  Title3,
} from "@fluentui/react-components";
import {
  CheckmarkCircle20Filled,
  Circle20Regular,
  LockClosed20Regular,
} from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import {
  type FormEvent,
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
  type PublicationStage,
} from "./publication-workflow";

const STEP_ORDER: PublicationStage[] = [
  "login",
  "space",
  "content",
  "publish",
  "complete",
];

const STEPS = [
  { stage: "login", label: "验证演示租户" },
  { stage: "space", label: "确定知识空间" },
  { stage: "content", label: "上传首份文本" },
  { stage: "publish", label: "核验并发布" },
] as const;

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
      <aside className="publication-progress" aria-label="发布进度">
        <div className="progress-heading">
          <LockClosed20Regular aria-hidden />
          <Text weight="semibold">可信发布路径</Text>
        </div>
        <ol>
          {STEPS.map((step) => {
            const done = isStageDone(workflow.stage, step.stage);
            const current = workflow.stage === step.stage;
            return (
              <li
                key={step.stage}
                className={current ? "progress-step current" : "progress-step"}
                aria-current={current ? "step" : undefined}
              >
                {done ? (
                  <CheckmarkCircle20Filled aria-hidden />
                ) : (
                  <Circle20Regular aria-hidden />
                )}
                <Text weight={current ? "semibold" : "regular"}>
                  {step.label}
                </Text>
              </li>
            );
          })}
        </ol>
        <Text size={200} className="progress-note">
          每一步都由服务端权限、幂等和版本事实校验。页面状态不能替代安全边界。
        </Text>
      </aside>

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

function LoginForm({
  busy,
  onSubmit,
}: {
  busy: boolean;
  onSubmit: (input: {
    tenantCode: string;
    loginName: string;
    password: string;
  }) => Promise<void>;
}) {
  const [tenantCode, setTenantCode] = useState("demo-store");
  const [loginName, setLoginName] = useState("admin");
  const [password, setPassword] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    void onSubmit({ tenantCode, loginName, password });
  }

  return (
    <TaskSection
      title="登录演示租户"
      description="凭据只用于建立短生命周期会话，不会写入浏览器存储。"
    >
      <form className="task-form" onSubmit={submit}>
        <Field label="租户编码" required>
          <Input
            value={tenantCode}
            autoComplete="organization"
            disabled={busy}
            onChange={(_, data) => setTenantCode(data.value)}
          />
        </Field>
        <Field label="登录名" required>
          <Input
            value={loginName}
            autoComplete="username"
            disabled={busy}
            onChange={(_, data) => setLoginName(data.value)}
          />
        </Field>
        <Field label="密码" required>
          <Input
            value={password}
            type="password"
            autoComplete="current-password"
            disabled={busy}
            onChange={(_, data) => setPassword(data.value)}
          />
        </Field>
        <Button
          appearance="primary"
          type="submit"
          disabled={busy || password.length === 0}
        >
          {busy ? "正在验证" : "登录并继续"}
        </Button>
      </form>
    </TaskSection>
  );
}

function SpacePicker({
  spaces,
  selectedId,
  busy,
  onSelected,
  onContinue,
  onCreate,
}: {
  spaces: KnowledgeSpace[];
  selectedId: string;
  busy: boolean;
  onSelected: (id: string) => void;
  onContinue: () => void;
  onCreate: () => void;
}) {
  if (spaces.length === 0) {
    return (
      <StatePanel
        kind="empty"
        title="还没有知识空间"
        description="创建一个知识空间后即可登记首份文本数据。"
        actionLabel="创建知识空间"
        onAction={onCreate}
      />
    );
  }

  return (
    <TaskSection
      title="选择知识空间"
      description="继续已有空间，或为新的权限和数据边界创建空间。"
    >
      <RadioGroup
        value={selectedId}
        onChange={(_, data) => onSelected(data.value)}
        className="space-options"
      >
        {spaces.map((space) => (
          <Radio
            key={space.id}
            value={space.id}
            label={`${space.name}，${space.status === "DRAFT" ? "草稿" : "可用"}`}
          />
        ))}
      </RadioGroup>
      <div className="task-actions">
        <Button
          appearance="primary"
          disabled={busy || selectedId.length === 0}
          onClick={onContinue}
        >
          使用选中空间
        </Button>
        <Button appearance="secondary" disabled={busy} onClick={onCreate}>
          创建新空间
        </Button>
      </div>
    </TaskSection>
  );
}

function SpaceForm({
  busy,
  canUseExisting,
  onCancel,
  onSubmit,
}: {
  busy: boolean;
  canUseExisting: boolean;
  onCancel: () => void;
  onSubmit: (input: { name: string; description: string }) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    void onSubmit({ name, description });
  }

  return (
    <TaskSection
      title="创建知识空间"
      description="空间是数据、权限和后续索引版本的共同边界。"
    >
      <form className="task-form" onSubmit={submit}>
        <Field label="空间名称" required>
          <Input
            value={name}
            maxLength={80}
            disabled={busy}
            onChange={(_, data) => setName(data.value)}
          />
        </Field>
        <Field label="用途说明">
          <Textarea
            value={description}
            maxLength={500}
            resize="vertical"
            disabled={busy}
            onChange={(_, data) => setDescription(data.value)}
          />
        </Field>
        <div className="task-actions">
          <Button
            appearance="primary"
            type="submit"
            disabled={busy || name.trim().length === 0}
          >
            {busy ? "正在创建" : "创建并继续"}
          </Button>
          {canUseExisting ? (
            <Button
              appearance="secondary"
              type="button"
              disabled={busy}
              onClick={onCancel}
            >
              返回已有空间
            </Button>
          ) : null}
        </div>
      </form>
    </TaskSection>
  );
}

function ContentForm({
  busy,
  spaceName,
  sourceName,
  initialContent,
  onSubmit,
}: {
  busy: boolean;
  spaceName: string;
  sourceName?: string;
  initialContent: string;
  onSubmit: (input: { sourceName: string; content: string }) => Promise<void>;
}) {
  const [name, setName] = useState(sourceName ?? "");
  const [content, setContent] = useState(initialContent);

  function submit(event: FormEvent) {
    event.preventDefault();
    void onSubmit({ sourceName: name, content });
  }

  return (
    <TaskSection
      title="上传首份文本"
      description={`内容将进入“${spaceName}”，通过完整性核验后才能发布。`}
    >
      <form className="task-form" onSubmit={submit}>
        <Field label="数据源名称" required>
          <Input
            value={name}
            maxLength={120}
            disabled={busy || sourceName !== undefined}
            onChange={(_, data) => setName(data.value)}
          />
        </Field>
        <Field
          label="纯文本内容"
          hint={`${new TextEncoder().encode(content).byteLength} 字节，限制 20 MiB`}
          required
        >
          <Textarea
            value={content}
            className="content-input"
            resize="vertical"
            disabled={busy}
            onChange={(_, data) => setContent(data.value)}
          />
        </Field>
        <Button
          appearance="primary"
          type="submit"
          disabled={
            busy
            || name.trim().length === 0
            || content.trim().length === 0
          }
        >
          {busy ? "正在上传并核验" : sourceName ? "重试上传" : "上传并核验"}
        </Button>
      </form>
    </TaskSection>
  );
}

function PublishReview({
  busy,
  spaceName,
  sourceName,
  characterCount,
  onPublish,
}: {
  busy: boolean;
  spaceName: string;
  sourceName: string;
  characterCount: number;
  onPublish: () => Promise<void>;
}) {
  return (
    <TaskSection
      title="核验并发布"
      description="发布前服务端会再次核验对象元数据、租户权限和乐观版本。"
    >
      <dl className="review-list">
        <div>
          <dt>知识空间</dt>
          <dd>{spaceName}</dd>
        </div>
        <div>
          <dt>数据源</dt>
          <dd>{sourceName}</dd>
        </div>
        <div>
          <dt>内容规模</dt>
          <dd>{characterCount} 个字符</dd>
        </div>
        <div>
          <dt>当前状态</dt>
          <dd><Badge appearance="tint">内容已核验</Badge></dd>
        </div>
      </dl>
      <Button
        appearance="primary"
        disabled={busy}
        onClick={() => void onPublish()}
      >
        {busy ? "正在发布" : "发布文本数据"}
      </Button>
    </TaskSection>
  );
}

function Completion({
  spaceName,
  sourceName,
  onSignOut,
}: {
  spaceName: string;
  sourceName: string;
  onSignOut: () => Promise<void>;
}) {
  return (
    <StatePanel
      kind="empty"
      title="首份知识数据已发布"
      description={`“${sourceName}”已进入“${spaceName}”。后续索引工作包可以消费已发布版本。`}
      actionLabel="退出演示会话"
      onAction={() => void onSignOut()}
      icon={<CheckmarkCircle20Filled aria-hidden />}
    />
  );
}

function TaskSection({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <>
      <header className="task-heading">
        <Title3>{title}</Title3>
        <Text>{description}</Text>
      </header>
      {children}
    </>
  );
}

function WorkflowFeedback({
  feedback,
}: {
  feedback: {
    kind: "error" | "denied" | "partial" | "stale";
    message: string;
    traceId: string;
  };
}) {
  const titles = {
    error: "操作未完成",
    denied: "当前账号无权执行",
    partial: "内容已保留，发布尚未完成",
    stale: "页面数据已经陈旧",
  };
  return (
    <MessageBar intent={feedback.kind === "error" ? "error" : "warning"}>
      <MessageBarBody>
        <MessageBarTitle>{titles[feedback.kind]}</MessageBarTitle>
        {feedback.message}
        {feedback.traceId ? `，追踪标识：${feedback.traceId}` : ""}
      </MessageBarBody>
    </MessageBar>
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

function isStageDone(
  current: PublicationStage,
  candidate: PublicationStage,
): boolean {
  return STEP_ORDER.indexOf(current) > STEP_ORDER.indexOf(candidate);
}
