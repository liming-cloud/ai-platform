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
  DataTrending20Regular,
  Database20Regular,
  ShieldTask20Regular,
} from "@fluentui/react-icons";
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
      <LoginFrame stateful>
        <StatePanel
          kind="loading"
          title="正在恢复登录状态"
          description="正在验证当前浏览器会话和可用工作区。"
        />
      </LoginFrame>
    );
  }

  return (
    <LoginFrame>
      <header className="governance-login__heading">
        <h1>登录企业控制台</h1>
        <p>选择工作区并使用已分配的企业账户登录。</p>
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
              className="governance-login__input"
              value={tenantCode}
              placeholder="例如：demo-store"
              autoComplete="organization"
              disabled={state === "authenticating"}
              onChange={(_, data) => setTenantCode(data.value)}
            />
          </Field>
        ) : (
          <p className="governance-login__platform-note">
            平台权限会根据您的账户授权自动建立，不需要填写租户编码。
          </p>
        )}
        <Field label="登录名" required>
          <Input
            className="governance-login__input"
            value={loginName}
            placeholder={workspace === "platform" ? "例如：platform.root" : "请输入登录名"}
            autoComplete="username"
            disabled={state === "authenticating"}
            onChange={(_, data) => setLoginName(data.value)}
          />
        </Field>
        <Field label="密码" required>
          <Input
            className="governance-login__input"
            value={password}
            type="password"
            autoComplete="current-password"
            placeholder="请输入密码"
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
    </LoginFrame>
  );
}

function LoginFrame({ children, stateful = false }: {
  children: ReactNode;
  stateful?: boolean;
}) {
  return (
    <section className="governance-login" aria-live="polite">
      <div className="governance-login__panel">
        <div className="governance-login__brand" aria-label="可信知识平台">
          <span aria-hidden="true">知</span>
          <strong>可信知识平台</strong>
        </div>
        <div className="governance-login__layout">
          <aside className="governance-login__brief" aria-label="产品能力">
            <span className="governance-login__context">企业知识基础设施</span>
            <p className="governance-login__statement">让企业答案有据可查。</p>
            <p className="governance-login__description">
              把授权数据、模型和助手放进同一条可审计链路，面向真实业务稳定交付。
            </p>
            <ul className="governance-login__principles">
              <li>
                <ShieldTask20Regular aria-hidden="true" />
                <span><strong>边界先于答案</strong><small>身份、租户和资源权限在检索前生效。</small></span>
              </li>
              <li>
                <Database20Regular aria-hidden="true" />
                <span><strong>证据随答案交付</strong><small>引用、拒答和反馈保留在同一条任务链路。</small></span>
              </li>
              <li>
                <DataTrending20Regular aria-hidden="true" />
                <span><strong>运营状态可恢复</strong><small>连接、数据、模型和问答风险都有明确下一步。</small></span>
              </li>
            </ul>
          </aside>
          <div className={stateful ? "governance-login__card governance-login__card--state" : "governance-login__card"}>
            {children}
          </div>
        </div>
      </div>
    </section>
  );
}
