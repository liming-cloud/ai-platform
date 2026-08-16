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
  Select,
  Text,
  Title3,
} from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { StatePanel } from "@foundation/design-system";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import {
  ApiRequestError,
  type ControlledModelCatalogEntry,
  type ControlPlaneClient,
  type ModelConfiguration,
} from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";

interface Failure {
  message: string;
  traceId: string;
}

type ViewState = "unauthenticated" | "loading" | "ready" | "error";
type ModelConfigurationRoute = Extract<ConsoleRoute, { task: "configuration" }>;
type DialogTarget =
  | { kind: "create" }
  | { kind: "activate"; configuration: ModelConfiguration }
  | { kind: "rotate"; configuration: ModelConfiguration };

/**
 * 模型配置控制台只展示当前分页的目录和配置摘要。API Key 只在单次受控 Dialog 写请求中存在，
 * 成功或关闭后立即清除；浏览器不累加未查看页面，也不把本地对象作为写后的最终事实。
 */
export function ModelConfigurationWorkspace({
  client,
  route,
  sessionRevision,
  onSessionChanged,
  onRouteChange,
}: {
  client: ControlPlaneClient;
  route: ModelConfigurationRoute;
  sessionRevision: number;
  onSessionChanged: () => void;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [configurations, setConfigurations] = useState<ModelConfiguration[]>([]);
  const [catalog, setCatalog] = useState<ControlledModelCatalogEntry[]>([]);
  const [configurationHasPrevious, setConfigurationHasPrevious] = useState(false);
  const [configurationHasNext, setConfigurationHasNext] = useState(false);
  const [configurationTotal, setConfigurationTotal] = useState(0);
  const [catalogHasPrevious, setCatalogHasPrevious] = useState(false);
  const [catalogHasNext, setCatalogHasNext] = useState(false);
  const [catalogTotal, setCatalogTotal] = useState(0);
  const [failure, setFailure] = useState<Failure>();
  const [submitting, setSubmitting] = useState(false);
  const [dialog, setDialog] = useState<DialogTarget>();
  const [creationDisplayName, setCreationDisplayName] = useState("");
  const [creationApiKey, setCreationApiKey] = useState("");
  const [creationCatalogEntryId, setCreationCatalogEntryId] = useState("");
  const [rotationApiKey, setRotationApiKey] = useState("");

  const load = useCallback(async () => {
    if (!client.hasSession()) {
      setViewState("unauthenticated");
      setConfigurations([]);
      setCatalog([]);
      return;
    }
    setViewState("loading");
    setFailure(undefined);
    try {
      const [configurationPage, catalogPage] = await Promise.all([
        client.listModelConfigurations(route.configurationPage),
        client.listControlledModelCatalog(route.catalogPage),
      ]);
      setConfigurations(configurationPage.items);
      setConfigurationHasPrevious(configurationPage.hasPrevious);
      setConfigurationHasNext(configurationPage.hasNext);
      setConfigurationTotal(configurationPage.totalElements);
      setCatalog(catalogPage.items);
      setCatalogHasPrevious(catalogPage.hasPrevious);
      setCatalogHasNext(catalogPage.hasNext);
      setCatalogTotal(catalogPage.totalElements);
      setViewState("ready");
    } catch (error) {
      setFailure(toFailure(error));
      setViewState("error");
    }
  }, [client, route.catalogPage, route.configurationPage]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timeout);
  }, [load, sessionRevision]);

  async function logout() {
    try {
      await client.logout();
    } finally {
      onSessionChanged();
      setViewState("unauthenticated");
      setConfigurations([]);
      setCatalog([]);
    }
  }

  function openCreation(catalogEntryId?: string) {
    setCreationCatalogEntryId(catalogEntryId ?? catalog[0]?.id ?? "");
    setDialog({ kind: "create" });
  }

  function closeDialog() {
    if (submitting) {
      return;
    }
    setDialog(undefined);
    setCreationDisplayName("");
    setCreationApiKey("");
    setCreationCatalogEntryId("");
    setRotationApiKey("");
  }

  async function createConfiguration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const apiKey = creationApiKey;
    const displayName = creationDisplayName;
    const scope = String(formData.get("scope") ?? "PERSONAL") as ModelConfiguration["scope"];
    const catalogEntryId = creationCatalogEntryId;
    if (
      apiKey.trim().length === 0 || displayName.trim().length === 0
      || catalogEntryId.length === 0
    ) {
      setFailure({ message: "请填写配置名称、模型和 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.createModelConfiguration({ scope, displayName, catalogEntryId, apiKey });
      closeDialogAfterSuccess();
      await load();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setCreationApiKey("");
      setSubmitting(false);
    }
  }

  async function activate(configuration: ModelConfiguration) {
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.activateModelConfiguration(configuration.id);
      closeDialogAfterSuccess();
      await load();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function rotateCredential(event: FormEvent<HTMLFormElement>, configuration: ModelConfiguration) {
    event.preventDefault();
    const apiKey = rotationApiKey;
    if (apiKey.trim().length === 0) {
      setFailure({ message: "请输入新的 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      await client.rotateModelConfigurationCredential(configuration.id, apiKey);
      closeDialogAfterSuccess();
      await load();
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setRotationApiKey("");
      setSubmitting(false);
    }
  }

  function closeDialogAfterSuccess() {
    setDialog(undefined);
    setCreationDisplayName("");
    setCreationApiKey("");
    setCreationCatalogEntryId("");
    setRotationApiKey("");
  }

  function changeConfigurationPage(page: number) {
    onRouteChange({ ...route, configurationPage: page });
  }

  function changeCatalogPage(page: number) {
    onRouteChange({ ...route, catalogPage: page });
  }

  if (viewState === "unauthenticated") {
    return (
      <StatePanel
        kind="stale"
        title="浏览器会话已经失效"
        description="受保护的模型任务已清除，请返回统一登录入口。"
        actionLabel="返回登录入口"
        onAction={() => window.location.assign("/")}
      />
    );
  }

  if (viewState === "loading") {
    return (
      <StatePanel
        kind="loading"
        title="正在加载模型配置"
        description="正在读取当前页的可选模型目录和配置状态。"
      />
    );
  }

  if (viewState === "error") {
    return (
      <StatePanel
        kind="error"
        title="模型配置暂时不可用"
        description={failureDescription(failure)}
        actionLabel="重新加载"
        onAction={() => void load()}
      />
    );
  }

  return (
    <div className="model-configuration-workspace">
      <div className="governance-toolbar">
        <Text>配置只引用平台发布目录；API Key 只在创建和轮换时写入，随后不可查看。</Text>
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

      <section className="model-configuration-section">
        <header className="task-heading">
          <div>
            <Title3>当前模型配置</Title3>
            <Text>仅展示当前成员在所属租户内可使用的配置及安全状态。</Text>
          </div>
          <Button appearance="primary" onClick={() => openCreation()} disabled={catalog.length === 0}>
            新建模型配置
          </Button>
        </header>
        <div className="model-configuration-list">
          {configurations.length === 0 ? (
            <Text>本页没有可用模型配置。</Text>
          ) : configurations.map((configuration) => (
            <article className="model-configuration-card" key={configuration.id}>
              <div>
                <Title3>{configuration.displayName}</Title3>
                <Text>{configuration.scope} · {configuration.modelType}</Text>
              </div>
              <div className="model-configuration-status">
                <Badge appearance="tint" color={statusColor(configuration.status)}>{configuration.status}</Badge>
                <Text>{configuration.credentialConfigured ? "凭据已配置" : "未配置凭据"}</Text>
              </div>
              <div className="task-actions">
                {configuration.status === "DRAFT" && configuration.credentialConfigured ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => setDialog({ kind: "activate", configuration })}>
                    启用
                  </Button>
                ) : null}
                <Button disabled={submitting} onClick={() => setDialog({ kind: "rotate", configuration })}>
                  轮换凭据
                </Button>
              </div>
            </article>
          ))}
        </div>
        <PageNavigation
          label="模型配置"
          page={route.configurationPage}
          total={configurationTotal}
          hasPrevious={configurationHasPrevious}
          hasNext={configurationHasNext}
          onPrevious={() => changeConfigurationPage(route.configurationPage - 1)}
          onNext={() => changeConfigurationPage(route.configurationPage + 1)}
        />
      </section>

      <section className="model-configuration-section">
        <header className="task-heading">
          <div>
            <Title3>平台模型目录</Title3>
            <Text>目录由平台发布；选择模型后在受控弹窗中建立配置。</Text>
          </div>
        </header>
        <div className="model-configuration-list">
          {catalog.length === 0 ? (
            <Text>本页没有可选模型。</Text>
          ) : catalog.map((entry) => (
            <article className="model-configuration-card" key={entry.id}>
              <div>
                <Title3>{entry.modelCode}</Title3>
                <Text>{entry.modelType} · {entry.providerKind}</Text>
              </div>
              <Button onClick={() => openCreation(entry.id)}>使用此模型</Button>
            </article>
          ))}
        </div>
        <PageNavigation
          label="平台模型目录"
          page={route.catalogPage}
          total={catalogTotal}
          hasPrevious={catalogHasPrevious}
          hasNext={catalogHasNext}
          onPrevious={() => changeCatalogPage(route.catalogPage - 1)}
          onNext={() => changeCatalogPage(route.catalogPage + 1)}
        />
      </section>

      <Dialog open={dialog?.kind === "create"} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>新建模型配置</DialogTitle>
            <form onSubmit={(event) => void createConfiguration(event)}>
              <DialogContent>
                <Text>模型和能力来自当前页平台目录；API Key 仅用于本次写入，不会回显。</Text>
                <Field label="使用范围" required>
                  <Select name="scope" defaultValue="PERSONAL" disabled={submitting}>
                    <option value="PERSONAL">个人</option>
                    <option value="TENANT">租户</option>
                    <option value="PLATFORM">平台</option>
                  </Select>
                </Field>
                <Field label="配置名称" required>
                  <Input autoFocus autoComplete="off" value={creationDisplayName} onChange={(_, data) => setCreationDisplayName(data.value)} disabled={submitting} />
                </Field>
                <Field label="平台模型目录" required>
                  <Select value={creationCatalogEntryId} onChange={(_, data) => setCreationCatalogEntryId(data.value)} disabled={submitting || catalog.length === 0}>
                    <option value="">请选择已发布模型</option>
                    {catalog.map((entry) => <option key={entry.id} value={entry.id}>{entry.modelCode} · {entry.modelType}</option>)}
                  </Select>
                </Field>
                <Field label="API Key" required>
                  <Input type="password" autoComplete="new-password" value={creationApiKey} onChange={(_, data) => setCreationApiKey(data.value)} disabled={submitting} />
                </Field>
              </DialogContent>
              <DialogActions>
                <Button appearance="secondary" type="button" disabled={submitting} onClick={closeDialog}>取消</Button>
                <Button appearance="primary" type="submit" disabled={submitting || catalog.length === 0}>{submitting ? "正在保存" : "保存为草稿"}</Button>
              </DialogActions>
            </form>
          </DialogBody>
        </DialogSurface>
      </Dialog>

      <Dialog open={dialog?.kind === "activate"} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>确认启用模型配置</DialogTitle>
            <DialogContent>启用 {dialog?.kind === "activate" ? dialog.configuration.displayName : "该配置"} 后，后续受权调用将使用其当前受控模型能力。</DialogContent>
            <DialogActions>
              <Button appearance="secondary" disabled={submitting} onClick={closeDialog}>取消</Button>
              <Button appearance="primary" disabled={submitting} onClick={() => dialog?.kind === "activate" && void activate(dialog.configuration)}>{submitting ? "正在启用" : "确认启用"}</Button>
            </DialogActions>
          </DialogBody>
        </DialogSurface>
      </Dialog>

      <Dialog open={dialog?.kind === "rotate"} onOpenChange={(_, data) => { if (!data.open) closeDialog(); }}>
        <DialogSurface>
          <DialogBody>
            <DialogTitle>轮换模型配置凭据</DialogTitle>
            <form onSubmit={(event) => dialog?.kind === "rotate" && void rotateCredential(event, dialog.configuration)}>
              <DialogContent>
                <Text>轮换 {dialog?.kind === "rotate" ? dialog.configuration.displayName : "该配置"} 的凭据会使旧凭据立即失效；新 API Key 不会回显。</Text>
                <Field label="新的 API Key" required>
                  <Input autoFocus type="password" autoComplete="new-password" value={rotationApiKey} onChange={(_, data) => setRotationApiKey(data.value)} disabled={submitting} />
                </Field>
              </DialogContent>
              <DialogActions>
                <Button appearance="secondary" type="button" disabled={submitting} onClick={closeDialog}>取消</Button>
                <Button appearance="primary" type="submit" disabled={submitting}>{submitting ? "正在轮换" : "确认轮换"}</Button>
              </DialogActions>
            </form>
          </DialogBody>
        </DialogSurface>
      </Dialog>
    </div>
  );
}

function PageNavigation({
  label,
  page,
  total,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: {
  label: string;
  page: number;
  total: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return (
    <div className="task-actions" aria-label={`${label}分页`}>
      <Text>{label}：第 {page} 页，共 {total} 条</Text>
      <Button appearance="subtle" disabled={!hasPrevious} onClick={onPrevious}>上一页</Button>
      <Button appearance="subtle" disabled={!hasNext} onClick={onNext}>下一页</Button>
    </div>
  );
}

/** Fluent UI 的密码输入在成功、失败与关闭时均被清空，避免秘密留在浏览器状态。 */
function statusColor(status: ModelConfiguration["status"]): "success" | "warning" | "danger" {
  if (status === "ACTIVE") {
    return "success";
  }
  return status === "DRAFT" ? "warning" : "danger";
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
  return failure.message;
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
