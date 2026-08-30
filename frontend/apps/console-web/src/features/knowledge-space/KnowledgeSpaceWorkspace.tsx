import {
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogBody,
  DialogContent,
  DialogSurface,
  DialogTitle,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Textarea,
  Title3,
} from "@fluentui/react-components";
import {
  Add20Regular,
  ArrowRight20Regular,
  Database20Regular,
} from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  ApiRequestError,
  presentApiError,
  type ControlPlaneClient,
  type KnowledgeSpace,
  type UserFacingError,
} from "../../api/control-plane-client";
import { knowledgeDataTaskRoute, type ConsoleRoute } from "../../session/console-route";

type KnowledgeSpaceRoute = Extract<ConsoleRoute, { task: "knowledge-bases" }>;

/** 浏览器提交的规范化知识库创建载荷。 */
export interface KnowledgeSpaceCreateInput {
  name: string;
  description: string | null;
}

/** 待确认创建尝试把幂等键绑定到唯一的规范化载荷。 */
export interface KnowledgeSpaceCreateAttempt {
  input: KnowledgeSpaceCreateInput;
  fingerprint: string;
  key: string;
}

/**
 * 相同载荷重试复用幂等键；名称或说明发生实质变化时建立新尝试。
 */
export function prepareKnowledgeSpaceCreateAttempt(
  previous: KnowledgeSpaceCreateAttempt | undefined,
  input: { name: string; description: string },
  identifierFactory: () => string,
): KnowledgeSpaceCreateAttempt {
  const normalized: KnowledgeSpaceCreateInput = {
    name: input.name.trim(),
    description: input.description.trim() || null,
  };
  const fingerprint = JSON.stringify(normalized);
  if (previous?.fingerprint === fingerprint) {
    return previous;
  }
  return {
    input: normalized,
    fingerprint,
    key: identifierFactory(),
  };
}

/** 不确定响应只按完整规范化载荷确认，避免把同名的其他资源当作本次结果。 */
export function findConfirmedKnowledgeSpace(
  spaces: readonly KnowledgeSpace[],
  input: KnowledgeSpaceCreateInput,
): KnowledgeSpace | undefined {
  return spaces.find((space) =>
    space.name === input.name
    && space.description === input.description);
}

/** 当前租户内知识库的一等目录和创建入口。 */
export function KnowledgeSpaceWorkspace({
  client,
  route,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: KnowledgeSpaceRoute;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [spaces, setSpaces] = useState<KnowledgeSpace[]>([]);
  const [total, setTotal] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [busy, setBusy] = useState(false);
  const [directoryFailure, setDirectoryFailure] = useState<UserFacingError>();
  const [createFailure, setCreateFailure] = useState<UserFacingError>();
  const [success, setSuccess] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const createAttempt = useRef<KnowledgeSpaceCreateAttempt | undefined>(undefined);

  const reload = useCallback(async () => {
    setBusy(true);
    setDirectoryFailure(undefined);
    try {
      const page = await client.listKnowledgeSpaces(route.page);
      setSpaces(page.items);
      setTotal(page.totalElements);
      setHasPrevious(page.hasPrevious);
      setHasNext(page.hasNext);
    } catch (error) {
      setDirectoryFailure(presentApiError(error, "加载知识库目录"));
      setSpaces([]);
      setTotal(0);
      setHasPrevious(false);
      setHasNext(false);
    } finally {
      setBusy(false);
    }
  }, [client, route.page]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timeout);
  }, [reload]);

  async function createKnowledgeSpace(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === "") return;

    const attempt = prepareKnowledgeSpaceCreateAttempt(
      createAttempt.current,
      { name, description },
      () => globalThis.crypto.randomUUID(),
    );
    createAttempt.current = attempt;
    setBusy(true);
    setCreateFailure(undefined);
    try {
      const created = await client.createKnowledgeSpace(attempt.input, attempt.key);
      await completeKnowledgeSpaceCreation(created);
    } catch (error) {
      if (shouldReconcileKnowledgeSpaceCreate(error)) {
        try {
          const firstPage = await client.listKnowledgeSpaces(1);
          const confirmed = findConfirmedKnowledgeSpace(firstPage.items, attempt.input);
          if (confirmed !== undefined) {
            await completeKnowledgeSpaceCreation(confirmed);
            return;
          }
        } catch {
          // 回查失败不覆盖原创建错误；相同载荷仍使用同一幂等键安全重试。
        }
      }
      setCreateFailure(presentApiError(error, "新建知识库"));
    } finally {
      setBusy(false);
    }
  }

  async function completeKnowledgeSpaceCreation(created: KnowledgeSpace) {
    createAttempt.current = undefined;
    setName("");
    setDescription("");
    setCreateFailure(undefined);
    setCreateOpen(false);
    setSuccess(`知识库“${created.name}”已建立。现在可以进入知识库登记来源。`);
    if (route.page === 1) {
      await reload();
    } else {
      onRouteChange({ ...route, page: 1 });
    }
  }

  function closeCreateDialog() {
    if (busy) return;
    setCreateOpen(false);
    setName("");
    setDescription("");
    setCreateFailure(undefined);
    createAttempt.current = undefined;
  }

  return (
    <section className="knowledge-space-workspace" aria-live="polite">
      <KnowledgeSpaceDirectoryView
        spaces={spaces}
        page={route.page}
        total={total}
        hasPrevious={hasPrevious}
        hasNext={hasNext}
        busy={busy}
        failure={directoryFailure}
        success={success}
        onCreateRequest={() => {
          setDirectoryFailure(undefined);
          setCreateFailure(undefined);
          setSuccess("");
          setCreateOpen(true);
        }}
        onEnter={(space) => onRouteChange(
          knowledgeDataTaskRoute(route, "governed-file", space.id),
        )}
        onPageChange={(page) => onRouteChange({ ...route, page })}
      />

      <Dialog
        open={createOpen}
        onOpenChange={(_, data) => {
          if (!data.open) closeCreateDialog();
        }}
      >
        <DialogSurface>
          <DialogBody>
            <DialogTitle>新建知识库</DialogTitle>
            <DialogContent>
              <KnowledgeSpaceCreateForm
                name={name}
                description={description}
                busy={busy}
                failure={createFailure}
                onNameChange={(value) => {
                  setName(value);
                  setCreateFailure(undefined);
                }}
                onDescriptionChange={(value) => {
                  setDescription(value);
                  setCreateFailure(undefined);
                }}
                onSubmit={(event) => void createKnowledgeSpace(event)}
              />
            </DialogContent>
            <DialogActions>
              <Button appearance="secondary" disabled={busy} onClick={closeCreateDialog}>
                取消
              </Button>
              <Button
                appearance="primary"
                type="submit"
                form="knowledge-space-create-form"
                disabled={busy || name.trim() === ""}
              >
                {busy ? "正在建立" : "建立知识库"}
              </Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </section>
  );
}

/** 新建知识库弹窗内的可聚焦表单，错误不会被模态遮罩隔离。 */
export function KnowledgeSpaceCreateForm({
  name,
  description,
  busy,
  failure,
  onNameChange,
  onDescriptionChange,
  onSubmit,
}: {
  name: string;
  description: string;
  busy: boolean;
  failure?: UserFacingError;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const nameInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (failure !== undefined) {
      nameInput.current?.focus();
    }
  }, [failure]);

  return (
    <>
      <Text className="knowledge-space-dialog__intro">
        知识库只属于当前租户。建立后从目录进入，再登记来源、完成质量检查并发布索引。
      </Text>
      {failure ? (
        <MessageBar intent="error" role="alert">
          <MessageBarBody>
            <MessageBarTitle>{failure.title}</MessageBarTitle>
            {failure.description}
          </MessageBarBody>
        </MessageBar>
      ) : null}
      <form
        id="knowledge-space-create-form"
        className="foundation-dialog-form knowledge-space-dialog__form"
        onSubmit={onSubmit}
      >
        <Field
          label="知识库名称"
          hint={`${Array.from(name).length} / 80 个字符`}
          required
          validationState={failure === undefined ? "none" : "error"}
          validationMessage={failure === undefined ? undefined : "请核对名称和说明后重试。"}
        >
          <Input
            ref={nameInput}
            size="large"
            autoFocus
            aria-invalid={failure !== undefined}
            value={name}
            maxLength={80}
            disabled={busy}
            placeholder="例如：客户服务知识库"
            onChange={(_, data) => onNameChange(data.value)}
          />
        </Field>
        <Field
          label="用途说明"
          hint={`${Array.from(description).length} / 500 个字符，仅当前租户管理员可在目录中查看。`}
        >
          <Textarea
            size="large"
            value={description}
            maxLength={500}
            rows={4}
            disabled={busy}
            placeholder="说明知识范围、维护责任或适用助手"
            onChange={(_, data) => onDescriptionChange(data.value)}
          />
        </Field>
      </form>
    </>
  );
}

/**
 * 只有网络异常或服务端错误可能存在“已提交但响应丢失”，确定性 4xx 不做目录回查。
 */
function shouldReconcileKnowledgeSpaceCreate(error: unknown): boolean {
  return !(error instanceof ApiRequestError) || error.status >= 500;
}

export interface KnowledgeSpaceDirectoryViewProps {
  spaces: KnowledgeSpace[];
  page: number;
  total: number;
  hasPrevious: boolean;
  hasNext: boolean;
  busy: boolean;
  failure?: UserFacingError;
  success?: string;
  onCreateRequest: () => void;
  onEnter: (space: KnowledgeSpace) => void;
  onPageChange: (page: number) => void;
}

/** 无副作用目录视图，供加载状态、响应式结构和服务端分页单独回归。 */
export function KnowledgeSpaceDirectoryView({
  spaces,
  page,
  total,
  hasPrevious,
  hasNext,
  busy,
  failure,
  success,
  onCreateRequest,
  onEnter,
  onPageChange,
}: KnowledgeSpaceDirectoryViewProps) {
  return (
    <>
      <header className="task-heading task-heading--split knowledge-space-heading">
        <div>
          <Title3>租户知识库</Title3>
          <Text>创建并维护当前租户拥有的知识边界，再在每个知识库中登记来源和发布索引。</Text>
        </div>
        <Button
          appearance="primary"
          icon={<Add20Regular />}
          disabled={busy}
          onClick={onCreateRequest}
        >
          新建知识库
        </Button>
      </header>

      <section className="knowledge-space-boundary" aria-label="知识库所有权说明">
        <Database20Regular aria-hidden="true" />
        <div>
          <Text weight="semibold">当前租户专属</Text>
          <Text>
            平台管理员不能进入知识库或查看知识内容。只有当前租户管理员可以维护本目录。
          </Text>
        </div>
        <ol aria-label="知识库发布路径">
          <li><strong>建立</strong><span>定义知识边界</span></li>
          <li><strong>接入</strong><span>登记受控来源</span></li>
          <li><strong>核验</strong><span>完成同步与质量检查</span></li>
          <li><strong>发布</strong><span>建立活动索引</span></li>
        </ol>
      </section>

      {success ? (
        <MessageBar intent="success">
          <MessageBarBody>
            <MessageBarTitle>知识库已建立</MessageBarTitle>
            {success}
          </MessageBarBody>
        </MessageBar>
      ) : null}
      {failure ? (
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>{failure.title}</MessageBarTitle>
            {failure.description}
          </MessageBarBody>
        </MessageBar>
      ) : null}

      {busy && spaces.length === 0 ? (
        <StatePanel
          kind="loading"
          title="正在加载知识库"
          description="正在读取当前租户的知识库目录。"
        />
      ) : null}

      {!busy && spaces.length === 0 && total === 0 && failure === undefined ? (
        <div className="knowledge-space-empty">
          <Database20Regular aria-hidden="true" />
          <div>
            <Title3>还没有知识库</Title3>
            <Text>先建立一个租户专属知识库，再进入其中登记文件、数据集或对象存储来源。</Text>
          </div>
          <Button appearance="primary" icon={<Add20Regular />} onClick={onCreateRequest}>
            新建知识库
          </Button>
        </div>
      ) : null}

      {spaces.length > 0 ? (
        <div className="knowledge-space-list" aria-label="知识库目录">
          {spaces.map((space) => (
            <article key={space.id} className="knowledge-space-row">
              <div className="knowledge-space-row__identity">
                <span aria-hidden="true"><Database20Regular /></span>
                <div>
                  <Title3>{space.name}</Title3>
                  <Text>{space.description || "未填写用途说明"}</Text>
                </div>
              </div>
              <div className="knowledge-space-row__facts">
                <Badge appearance="tint" color={statusColor(space.status)}>
                  {statusLabel(space.status)}
                </Badge>
                <Text>创建于 {new Date(space.createdAt).toLocaleDateString("zh-CN")}</Text>
              </div>
              <Button
                appearance="subtle"
                icon={<ArrowRight20Regular />}
                iconPosition="after"
                disabled={busy}
                onClick={() => onEnter(space)}
              >
                进入知识库
              </Button>
            </article>
          ))}
        </div>
      ) : null}

      {total > 0 ? (
        <footer className="knowledge-space-pagination" aria-label="知识库目录分页">
          <Text>第 {page} 页，共 {total} 个知识库</Text>
          <div>
            <Button
              appearance="subtle"
              disabled={!hasPrevious || busy}
              onClick={() => onPageChange(page - 1)}
            >
              上一页
            </Button>
            <Button
              appearance="subtle"
              disabled={!hasNext || busy}
              onClick={() => onPageChange(page + 1)}
            >
              下一页
            </Button>
          </div>
        </footer>
      ) : null}
    </>
  );
}

function statusLabel(status: KnowledgeSpace["status"]) {
  switch (status) {
    case "DRAFT": return "草稿";
    case "VERIFIED": return "已核验";
    case "PUBLISHED": return "已发布";
    case "DISABLED": return "已停用";
  }
}

function statusColor(status: KnowledgeSpace["status"]): "informative" | "success" | "danger" {
  return status === "DISABLED" ? "danger" : status === "DRAFT" ? "informative" : "success";
}
