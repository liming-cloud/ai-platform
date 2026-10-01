import {
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
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
import { LoginEntryLinks } from "./LoginEntryLinks";
import {
  buildBrowserLoginCredentials,
  canEnterLoginWorkspace,
  isConsoleLoginPath,
  isLoginReady,
  loginWorkspaceFromPath,
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
  // 失效或卸载后，旧的异步认证结果不得恢复界面或触发回跳。
  const requestEpoch = useRef(0);
  const [capability, setCapability] = useState<BrowserSessionCapability>();
  const [state, setState] = useState<"restoring" | "anonymous" | "authenticating" | "failed">("restoring");
  const workspace = loginWorkspaceFromPath(window.location.pathname);
  const explicitLoginEntry = isConsoleLoginPath(window.location.pathname);
  const existingQueryEntry = window.location.pathname === "/ask"
    || new URLSearchParams(window.location.search).get("next") === "/ask";
  const [tenantCode, setTenantCode] = useState(() => workspace === "tenant"
    ? new URLSearchParams(window.location.search).get("tenantCode") ?? "" : "");
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");

  useEffect(() => {
    let current = true;
    const unsubscribe = client.subscribeSessionChange((active) => {
      if (current && !active) {
        requestEpoch.current += 1;
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
    const restoreEpoch = requestEpoch.current;
    void client.restoreSession().then(
      (restored) => {
        if (!current || restoreEpoch !== requestEpoch.current) {
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
        if (current && restoreEpoch === requestEpoch.current) {
          setCapability(undefined);
          setState("failed");
        }
      },
    );
    return () => {
      current = false;
      requestEpoch.current += 1;
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
      const restoreEpoch = requestEpoch.current;
      void client.restoreSession().then(
        (restored) => {
          if (current && restoreEpoch === requestEpoch.current && restored !== null && client.hasSession()) {
            setCapability(restored);
          }
        },
        () => {
          if (current && restoreEpoch === requestEpoch.current) {
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
    const authentication = client.login(buildBrowserLoginCredentials({ workspace, tenantCode, loginName, password }));
    const loginEpoch = ++requestEpoch.current;
    try {
      const restored = await authentication;
      if (loginEpoch !== requestEpoch.current || !client.hasSession()) return;
      setPassword("");
      // 错误角色的新会话立即退出；入口不能把普通会员提升为租户管理员。
      if (explicitLoginEntry && !canEnterLoginWorkspace(workspace, restored)) {
        await client.logout();
        setState("failed");
        return;
      }
      setCapability(restored);
      setState("restoring");
      onAuthenticated?.(restored);
    } catch {
      if (loginEpoch !== requestEpoch.current) return;
      setPassword("");
      setCapability(undefined);
      setState("failed");
    }
  }

  async function logout(stayAtEntry = false) {
    const entry = capability?.displayRoles.includes("PLATFORM_ADMINISTRATOR") ? "/login"
      : capability?.displayRoles.includes("TENANT_USER") ? "/ask" : "/tenant/login";
    try {
      await client.logout();
    } catch {
      // 客户端已在 finally 中失活；根入口不保留旧能力或受保护内容。
    } finally {
      setPassword("");
      if (!stayAtEntry) window.location.replace(entry);
    }
  }

  if (capability !== undefined && client.hasSession()) {
    if (explicitLoginEntry && !canEnterLoginWorkspace(workspace, capability)) {
      return <LoginFrame workspace={workspace}>
        <header className="governance-login__heading">
          <h1>当前登录身份与入口不匹配</h1>
          <p>可返回当前工作区，或退出后使用此入口对应的管理员账户登录。</p>
        </header>
        <a className="governance-login__link" href="/">返回当前工作区</a>
        <Button appearance="secondary" onClick={() => void logout(true)}>退出并切换账户</Button>
      </LoginFrame>;
    }
    return children({ capability, logout });
  }

  if (state === "restoring") {
    return (
      <LoginFrame workspace={workspace} stateful>
        <StatePanel
          kind="loading"
          title="正在恢复登录状态"
          description="正在验证当前浏览器会话和可用工作区。"
        />
      </LoginFrame>
    );
  }

  return (
    <LoginFrame workspace={workspace}>
      <header className="governance-login__heading">
        <span className="governance-login__context">身份验证</span>
        <h1>{existingQueryEntry && !explicitLoginEntry ? "知识问答登录"
          : workspace === "platform" ? "平台管理登录" : "租户管理员登录"}</h1>
        <p>使用已授权账户继续。</p>
      </header>
      {state === "failed" ? (
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>登录未成功</MessageBarTitle>
            登录信息不正确，或当前账户不能进入此工作区。请核对后重试。
          </MessageBarBody>
        </MessageBar>
      ) : null}
      <form className="governance-login__form" onSubmit={(event) => void login(event)}>
        {workspace === "tenant" ? (
          <Field
            label="租户编码"
            required
          >
            <Input
              className="governance-login__input"
              aria-label="租户编码"
              value={tenantCode}
              placeholder="请输入租户编码"
              autoComplete="organization"
              disabled={state === "authenticating"}
              onChange={(_, data) => setTenantCode(data.value)}
            />
          </Field>
        ) : null}
        <Field label="登录名" required>
          <Input
            className="governance-login__input"
            aria-label="登录名"
            value={loginName}
            placeholder={workspace === "platform" ? "请输入平台登录名" : "请输入登录名"}
            autoComplete="username"
            disabled={state === "authenticating"}
            onChange={(_, data) => setLoginName(data.value)}
          />
        </Field>
        <Field label="密码" required>
          <Input
            className="governance-login__input"
            aria-label="密码"
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
          {state === "authenticating" ? "正在登录" : "登录"}
        </Button>
      </form>
      <LoginEntryLinks entry={existingQueryEntry && !explicitLoginEntry ? "query" : workspace}
        onNavigate={() => setPassword("")} />
    </LoginFrame>
  );
}

function LoginFrame({ children, workspace, stateful = false }: {
  children: ReactNode;
  workspace: LoginWorkspace;
  stateful?: boolean;
}) {
  return (
    <main className="governance-login" aria-live="polite">
      <div className="governance-login__layout">
        <aside className="governance-login__brief" aria-label="工作区说明">
          <div className="governance-login__brand" aria-label="可信 RAG">
            <span aria-hidden="true">R</span>
            <span><strong>可信 RAG</strong><small>企业控制台</small></span>
          </div>
          <div className="governance-login__introduction">
            <span className="governance-login__context">{workspace === "platform" ? "平台管理" : "组织工作区"}</span>
            <h2 className="governance-login__statement">{workspace === "platform" ? "管理租户与模型供应" : "管理组织知识与助手"}</h2>
            <p className="governance-login__description">{workspace === "platform"
              ? "使用平台管理员账户，管理租户开通、资源与模型供应。"
              : "使用当前组织的管理员账户，继续知识、助手与成员任务。"}</p>
          </div>
        </aside>
        <section className="governance-login__task" aria-label="登录">
          <div className={stateful ? "governance-login__card governance-login__card--state" : "governance-login__card"}>
            {children}
          </div>
        </section>
      </div>
    </main>
  );
}
