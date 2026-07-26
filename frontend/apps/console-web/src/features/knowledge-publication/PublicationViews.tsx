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
import { CheckmarkCircle20Filled } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, type ReactNode, useState } from "react";
import type { KnowledgeSpace } from "../../api/control-plane-client";

export function LoginForm({
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

export function SpacePicker({
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

export function SpaceForm({
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

export function ContentForm({
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

export function PublishReview({
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

export function Completion({
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

export function WorkflowFeedback({
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

function TaskSection({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
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
