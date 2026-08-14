import {
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Tab,
  TabList,
} from "@fluentui/react-components";
import {
  BrowserSessionExpirationMonitor,
  BrowserSessionInvalidationChannel,
  type BrowserSessionCapability,
} from "@foundation/browser-session";
import { StatePanel } from "@foundation/design-system";
import {
  type FormEvent,
  type ReactNode,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { ControlPlaneClient } from "../api/control-plane-client";
import {
  buildBrowserLoginCredentials,
  isLoginReady,
  type LoginWorkspace,
} from "./login-workspace-choice";

interface AuthenticatedBrowserSession {
  capability: BrowserSessionCapability;
  logout: () => Promise<void>;
}

/**
 * 唯一持有浏览器凭据输入的根级入口。子工作区只能消费已恢复的能力清单和受保护客户端。
 */
export function BrowserSessionGate({
  client,
  onAuthenticated,
  children,
}: {
  client: ControlPlaneClient;
  onAuthenticated?: (capability: BrowserSessionCapability) => void;
  children: (session: AuthenticatedBrowserSession) => ReactNode;
}) {
  const invalidationChannel = useMemo(
    () => new BrowserSessionInvalidationChannel(),
    [],
  );
  const expirationMonitor = useMemo(
    () => new BrowserSessionExpirationMonitor(),
    [],
  );
  const receivingInvalidation = useRef(false);
  const [capability, setCapability] = useState<BrowserSessionCapability>();
  const [state, setState] = useState<"restoring" | "anonymous" | "authenticating" | "failed">("restoring");
  const [workspace, setWorkspace] = useState<LoginWorkspace>("tenant");
  const [tenantCode, setTenantCode] = useState("");
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");

  useEffect(() => {
    let current = true;
    const unsubscribe = client.subscribeSessionChange((active) => {
      if (current && !active) {
        if (!receivingInvalidation.current) {
          invalidationChannel.publishInvalidation();
        }
        setCapability(undefined);
        setState("anonymous");
      }
    });
    const unsubscribeInvalidation = invalidationChannel.subscribe(() => {
      receivingInvalidation.current = true;
      client.discardSession();
      receivingInvalidation.current = false;
    });
    void client.restoreSession().then(
      (restored) => {
        if (!current) {
          return;
        }
        if (restored === null) {
          setCapability(undefined);
          setState("anonymous");
          return;
        }
        setCapability(restored);
        setState("restoring");
      },
      () => {
        if (current) {
          setCapability(undefined);
          setState("failed");
        }
      },
    );
    return () => {
      current = false;
      unsubscribe();
      unsubscribeInvalidation();
      invalidationChannel.close();
    };
  }, [client, invalidationChannel]);

  useEffect(() => {
    if (capability === undefined || !client.hasSession()) {
      return undefined;
    }
    let current = true;
    expirationMonitor.schedule(capability.maximumSessionAgeSeconds, () => {
      void client.restoreSession().then(
        (restored) => {
          if (current && restored !== null && client.hasSession()) {
            setCapability(restored);
          }
        },
        () => {
          if (current) {
            setCapability(undefined);
            setState("failed");
          }
        },
      );
    });
    return () => {
      current = false;
      expirationMonitor.close();
    };
  }, [capability, client, expirationMonitor]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState("authenticating");
    try {
      const restored = await client.login(buildBrowserLoginCredentials({
        workspace,
        tenantCode,
        loginName,
        password,
      }));
      setPassword("");
      setCapability(restored);
      setState("restoring");
      onAuthenticated?.(restored);
    } catch {
      setPassword("");
      setCapability(undefined);
      setState("failed");
    }
  }

  async function logout() {
    try {
      await client.logout();
    } catch {
      // 客户端已在 finally 中失活；根入口不保留旧能力或受保护内容。
    }
  }

  if (capability !== undefined && client.hasSession()) {
    return children({ capability, logout });
  }

  if (state === "restoring") {
    return (
      <StatePanel
        kind="loading"
        title="正在恢复登录状态"
        description="正在验证当前浏览器会话和可用工作区。"
      />
    );
  }

  return (
    <section className="governance-login" aria-live="polite">
      <aside className="governance-login__context" aria-label="产品说明">
        <p className="governance-login__product">可信知识平台</p>
        <h1>进入企业控制台</h1>
        <p className="governance-login__summary">
          使用同一浏览器入口进入已经获授权的平台管理或租户工作区。
        </p>
        <dl className="governance-login__workspace-guide">
          <div>
            <dt>平台管理</dt>
            <dd>租户、安全基线和平台模型供应链。</dd>
          </div>
          <div>
            <dt>租户工作区</dt>
            <dd>仅处理当前组织被授权的数据、成员和业务任务。</dd>
          </div>
        </dl>
      </aside>
      <div className="governance-login__panel">
        <div className="governance-login__card">
          <header className="governance-login__heading">
            <h2>选择登录目标</h2>
            <p>系统会在验证后仅展示您已获授权的工作区。</p>
          </header>
          <TabList
            className="governance-login__workspace-tabs"
            selectedValue={workspace}
            aria-label="登录目标"
            onTabSelect={(_, data) => setWorkspace(data.value as LoginWorkspace)}
          >
            <Tab value="tenant">租户工作区</Tab>
            <Tab value="platform">平台管理</Tab>
          </TabList>
          {state === "failed" ? (
            <MessageBar intent="error">
              <MessageBarBody>
                <MessageBarTitle>登录未成功</MessageBarTitle>
                请检查登录信息后重试。系统不会显示凭据或会话细节。
              </MessageBarBody>
            </MessageBar>
          ) : null}
          <form className="governance-login__form" onSubmit={(event) => void login(event)}>
            {workspace === "tenant" ? (
              <Field
                label="租户编码"
                required
                hint="仅用于建立本次租户会话。"
              >
                <Input
                  value={tenantCode}
                  autoComplete="organization"
                  disabled={state === "authenticating"}
                  onChange={(_, data) => setTenantCode(data.value)}
                />
              </Field>
            ) : (
              <p className="governance-login__platform-note">
                平台权限由服务端根据您的账户授权建立，不需要填写租户编码。
              </p>
            )}
            <Field label="登录名" required>
              <Input
                value={loginName}
                autoComplete="username"
                disabled={state === "authenticating"}
                onChange={(_, data) => setLoginName(data.value)}
              />
            </Field>
            <Field label="密码" required>
              <Input
                value={password}
                type="password"
                autoComplete="current-password"
                disabled={state === "authenticating"}
                onChange={(_, data) => setPassword(data.value)}
              />
            </Field>
            <Button
              className="governance-login__submit"
              appearance="primary"
              type="submit"
              disabled={state === "authenticating" || !isLoginReady({
                workspace,
                tenantCode,
                loginName,
                password,
              })}
            >
              {state === "authenticating"
                ? "正在验证身份"
                : workspace === "platform"
                  ? "进入平台管理"
                  : "进入租户工作区"}
            </Button>
          </form>
        </div>
      </div>
    </section>
  );
}
