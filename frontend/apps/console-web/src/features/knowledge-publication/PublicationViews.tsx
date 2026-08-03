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
import type { PublicationWorkflow } from "./publication-workflow";

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
  chunkCount,
  onSignOut,
}: {
  spaceName: string;
  sourceName: string;
  chunkCount: number;
  onSignOut: () => Promise<void>;
}) {
  return (
    <StatePanel
      kind="empty"
      title="首份知识数据已可检索"
      description={`“${sourceName}”已进入“${spaceName}”，当前索引包含 ${chunkCount} 个文本分块。`}
      actionLabel="退出演示会话"
      onAction={() => void onSignOut()}
      icon={<CheckmarkCircle20Filled aria-hidden />}
    />
  );
}

export function IndexBuildReview({
  busy,
  indexVersion,
  onBuild,
  onPublish,
}: {
  busy: boolean;
  indexVersion: PublicationWorkflow["indexVersion"];
  onBuild: () => Promise<void>;
  onPublish: () => Promise<void>;
}) {
  if (indexVersion === undefined || indexVersion.status === "FAILED") {
    return (
      <TaskSection
        title={
          indexVersion === undefined ? "构建检索索引" : "重新构建检索索引"
        }
        description={
          indexVersion === undefined
            ? "系统将固定数据快照、分块规则和嵌入模型版本，并在后台生成可核验的索引。"
            : `上次构建未完成，错误码为 ${indexVersion.failureCode ?? "未知"}。重新构建会创建新版本，不覆盖失败记录。`
        }
      >
        <Button
          appearance="primary"
          disabled={busy}
          onClick={() => void onBuild()}
        >
          {busy ? "正在提交" : "开始构建"}
        </Button>
      </TaskSection>
    );
  }

  const ready = indexVersion.status === "READY";
  return (
    <TaskSection
      title={ready ? "发布检索索引" : "正在构建检索索引"}
      description={
        ready
          ? "索引已完成一致性核验。发布后，它将成为该知识空间唯一可查询的活动版本。"
          : "后台正在进行确定性分块和向量写入。页面会自动读取服务端状态。"
      }
    >
      <dl className="review-list">
        <div>
          <dt>索引状态</dt>
          <dd>
            <Badge appearance="tint">
              {ready ? "构建就绪" : "构建中"}
            </Badge>
          </dd>
        </div>
        <div>
          <dt>文本分块</dt>
          <dd>{ready ? `${indexVersion.chunkCount} 个` : "计算中"}</dd>
        </div>
        <div>
          <dt>索引版本</dt>
          <dd>{indexVersion.version}</dd>
        </div>
        <div>
          <dt>发布约束</dt>
          <dd>{ready ? "允许发布" : "等待构建完成"}</dd>
        </div>
      </dl>
      <Button
        appearance="primary"
        disabled={busy || !ready}
        onClick={() => void onPublish()}
      >
        {busy ? "正在发布" : ready ? "发布检索索引" : "正在等待"}
      </Button>
    </TaskSection>
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
