import {
  Badge,
  Button,
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
import { LoginForm } from "../knowledge-publication/PublicationViews";

interface Failure {
  message: string;
  traceId: string;
}

type ViewState = "unauthenticated" | "loading" | "ready" | "error";

/**
 * 模型配置控制台只展示目录和配置状态；API Key 只通过未受控表单进入一次写请求，页面不回显也不保留。
 */
export function ModelConfigurationWorkspace({
  client,
  sessionRevision,
  onSessionChanged,
}: {
  client: ControlPlaneClient;
  sessionRevision: number;
  onSessionChanged: () => void;
}) {
  const [viewState, setViewState] = useState<ViewState>("unauthenticated");
  const [configurations, setConfigurations] = useState<ModelConfiguration[]>([]);
  const [catalog, setCatalog] = useState<ControlledModelCatalogEntry[]>([]);
  const [hasNextConfigurationPage, setHasNextConfigurationPage] = useState(false);
  const [hasNextCatalogPage, setHasNextCatalogPage] = useState(false);
  const [configurationPageNumber, setConfigurationPageNumber] = useState(1);
  const [catalogPageNumber, setCatalogPageNumber] = useState(1);
  const [failure, setFailure] = useState<Failure>();
  const [loggingIn, setLoggingIn] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

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
        client.listModelConfigurations(),
        client.listControlledModelCatalog(),
      ]);
      setConfigurations(configurationPage.items);
      setConfigurationPageNumber(configurationPage.pageNumber);
      setHasNextConfigurationPage(configurationPage.hasNext);
      setCatalog(catalogPage.items);
      setCatalogPageNumber(catalogPage.pageNumber);
      setHasNextCatalogPage(catalogPage.hasNext);
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

  async function createConfiguration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const apiKey = String(formData.get("apiKey") ?? "");
    const displayName = String(formData.get("displayName") ?? "");
    const scope = String(formData.get("scope") ?? "PERSONAL") as ModelConfiguration["scope"];
    const catalogEntryId = String(formData.get("catalogEntryId") ?? "");
    form.reset();
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
      const created = await client.createModelConfiguration({
        scope,
        displayName,
        catalogEntryId,
        apiKey,
      });
      setConfigurations((current) => [created, ...current]);
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function activate(modelConfigurationId: string) {
    setSubmitting(true);
    setFailure(undefined);
    try {
      const updated = await client.activateModelConfiguration(modelConfigurationId);
      replaceConfiguration(updated, setConfigurations);
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function rotateCredential(
    event: FormEvent<HTMLFormElement>,
    modelConfigurationId: string,
  ) {
    event.preventDefault();
    const form = event.currentTarget;
    const apiKey = String(new FormData(form).get("apiKey") ?? "");
    form.reset();
    if (apiKey.trim().length === 0) {
      setFailure({ message: "请输入新的 API Key。", traceId: "" });
      return;
    }
    setSubmitting(true);
    setFailure(undefined);
    try {
      const updated = await client.rotateModelConfigurationCredential(
        modelConfigurationId,
        apiKey,
      );
      replaceConfiguration(updated, setConfigurations);
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function loadMoreConfigurations() {
    if (!hasNextConfigurationPage || loadingMore) {
      return;
    }
    setLoadingMore(true);
    try {
      const page = await client.listModelConfigurations(configurationPageNumber + 1);
      setConfigurations((current) => [...current, ...page.items]);
      setConfigurationPageNumber(page.pageNumber);
      setHasNextConfigurationPage(page.hasNext);
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setLoadingMore(false);
    }
  }

  async function loadMoreCatalog() {
    if (!hasNextCatalogPage || loadingMore) {
      return;
    }
    setLoadingMore(true);
    try {
      const page = await client.listControlledModelCatalog(catalogPageNumber + 1);
      setCatalog((current) => [...current, ...page.items]);
      setCatalogPageNumber(page.pageNumber);
      setHasNextCatalogPage(page.hasNext);
    } catch (error) {
      setFailure(toFailure(error));
    } finally {
      setLoadingMore(false);
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
    return (
      <StatePanel
        kind="loading"
        title="正在加载模型配置"
        description="正在读取可选模型目录和当前可用配置。"
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
        <Text>
          选择平台发布的模型目录创建个人、租户或平台配置。API Key 只在创建和轮换时写入，之后不可查看。
        </Text>
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
          <Title3>创建模型配置</Title3>
          <Text>模型厂家和能力由平台目录控制；提交后仅显示凭据已配置状态。</Text>
        </header>
        <form className="model-configuration-form" onSubmit={createConfiguration}>
          <Field label="使用范围" required>
            <Select name="scope" defaultValue="PERSONAL" disabled={submitting}>
              <option value="PERSONAL">个人</option>
              <option value="TENANT">租户</option>
              <option value="PLATFORM">平台</option>
            </Select>
          </Field>
          <Field label="配置名称" required>
            <Input name="displayName" autoComplete="off" disabled={submitting} />
          </Field>
          <Field label="平台模型目录" required>
            <Select name="catalogEntryId" disabled={submitting || catalog.length === 0}>
              <option value="">请选择已发布模型</option>
              {catalog.map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {entry.modelCode} · {entry.modelType}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="API Key" required>
            <Input
              name="apiKey"
              type="password"
              autoComplete="new-password"
              disabled={submitting}
            />
          </Field>
          <Button appearance="primary" type="submit" disabled={submitting || catalog.length === 0}>
            {submitting ? "正在保存" : "保存为草稿"}
          </Button>
        </form>
        {hasNextCatalogPage ? (
          <Button appearance="subtle" disabled={loadingMore} onClick={() => void loadMoreCatalog()}>
            {loadingMore ? "正在加载" : "加载更多可选模型"}
          </Button>
        ) : null}
      </section>

      <section className="model-configuration-section">
        <header className="task-heading">
          <Title3>当前模型配置</Title3>
          <Text>仅展示当前成员在所属租户内可使用的配置及安全状态。</Text>
        </header>
        <div className="model-configuration-list">
          {configurations.length === 0 ? (
            <Text>尚无可用模型配置。</Text>
          ) : configurations.map((configuration) => (
            <article className="model-configuration-card" key={configuration.id}>
              <div>
                <Title3>{configuration.displayName}</Title3>
                <Text>{configuration.scope} · {configuration.modelType}</Text>
              </div>
              <div className="model-configuration-status">
                <Badge appearance="tint" color={statusColor(configuration.status)}>
                  {configuration.status}
                </Badge>
                <Text>{configuration.credentialConfigured ? "凭据已配置" : "未配置凭据"}</Text>
              </div>
              <div className="task-actions">
                {configuration.status === "DRAFT" && configuration.credentialConfigured ? (
                  <Button appearance="primary" disabled={submitting} onClick={() => void activate(configuration.id)}>
                    启用
                  </Button>
                ) : null}
                <form
                  className="credential-rotation-form"
                  onSubmit={(event) => void rotateCredential(event, configuration.id)}
                >
                  <Input
                    name="apiKey"
                    type="password"
                    autoComplete="new-password"
                    aria-label={`${configuration.displayName} 的新 API Key`}
                    placeholder="输入新 API Key"
                    disabled={submitting}
                  />
                  <Button type="submit" disabled={submitting}>
                    轮换凭据
                  </Button>
                </form>
              </div>
            </article>
          ))}
        </div>
        {hasNextConfigurationPage ? (
          <Button appearance="subtle" disabled={loadingMore} onClick={() => void loadMoreConfigurations()}>
            {loadingMore ? "正在加载" : "加载更多配置"}
          </Button>
        ) : null}
      </section>
    </div>
  );
}

function replaceConfiguration(
  updated: ModelConfiguration,
  setConfigurations: React.Dispatch<React.SetStateAction<ModelConfiguration[]>>,
) {
  setConfigurations((current) => current.map((item) => item.id === updated.id ? updated : item));
}

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
  return failure.traceId.length === 0
    ? failure.message
    : `${failure.message}，追踪标识：${failure.traceId}`;
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
