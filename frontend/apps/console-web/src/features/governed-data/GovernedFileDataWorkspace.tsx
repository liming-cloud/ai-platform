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
  type KnowledgeSpace,
} from "../../api/control-plane-client";
import { LoginForm } from "../knowledge-publication/PublicationViews";

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
  sessionRevision,
  onSessionChanged,
}: {
  client: ControlPlaneClient;
  sessionRevision: number;
  onSessionChanged: () => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [knowledgeSpaceId, setKnowledgeSpaceId] = useState("");
  const [sourceName, setSourceName] = useState("");
  const [content, setContent] = useState("");
  const [source, setSource] = useState<GovernedDataSourceLifecycleResult>();
  const [failure, setFailure] = useState<Failure>();
  const [loggingIn, setLoggingIn] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      setSpaces([]);
      setKnowledgeSpaceId("");
      setSource(undefined);
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const page = await client.listKnowledgeSpaces();
      setSpaces(page.items);
      setKnowledgeSpaceId((current) => current || page.items[0]?.id || "");
      setViewState("ready");
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function login(credentials: {
    tenantCode: string;
    loginName: string;
    password: string;
  }) {
    setLoggingIn(true);
    setFailure(undefined);
    try {
      await client.login(credentials);
      onSessionChanged();
      await load();
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("unauthenticated");
    } finally {
      setLoggingIn(false);
    }
  }

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
    } catch (error) {
      setFailure(toFailure(error));
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
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function logout() {
    try {
      await client.logout();
    } finally {
      onSessionChanged();
      setSpaces([]);
      setKnowledgeSpaceId("");
      setSource(undefined);
      setViewState("unauthenticated");
    }
  }

  if (viewState === "unauthenticated") {
    return (
      <section className="governance-login">
        {failure ? <FailureMessage failure={failure} /> : null}
        <LoginForm busy={loggingIn} onSubmit={login} />
      </section>
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
        <Text>登记文件后由平台冻结对象事实，再以独立探测操作验证可读性；不会自动发布或建立索引。</Text>
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
            <Title3>登记并探测文件来源</Title3>
            <Text>当前仅支持 UTF-8 纯文本。对象键、桶名、摘要和正文均由平台处理，不会在控制台展示。</Text>
          </header>
          <form className="governed-file-form" onSubmit={register}>
            <Field label="知识空间" required>
              <Select
                value={knowledgeSpaceId}
                onChange={(_, data) => setKnowledgeSpaceId(data.value)}
                disabled={submitting || source !== undefined}
              >
                {spaces.map((space) => <option key={space.id} value={space.id}>{space.name}</option>)}
              </Select>
            </Field>
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
                <Button appearance="primary" disabled={submitting || source.status === "READY"} onClick={() => void probe()}>
                  {source.status === "FAILED" ? "重新探测" : "开始探测"}
                </Button>
                {source.status === "READY" ? <Text>探测已通过，后续同步、质量和索引工作将在 M3 后续切片交付。</Text> : null}
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
