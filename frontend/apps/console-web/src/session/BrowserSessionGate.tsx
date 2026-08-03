import {
  Button,
  Field,
  Input,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import {
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
  const receivingInvalidation = useRef(false);
  const [capability, setCapability] = useState<BrowserSessionCapability>();
  const [state, setState] = useState<"restoring" | "anonymous" | "authenticating" | "failed">("restoring");
  const [tenantCode, setTenantCode] = useState("demo-store");
  const [loginName, setLoginName] = useState("admin");
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

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState("authenticating");
    try {
      const restored = await client.login({
        tenantCode: tenantCode.trim() || undefined,
        loginName: loginName.trim(),
        password,
      });
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
      {state === "failed" ? (
        <MessageBar intent="error">
          <MessageBarBody>
            <MessageBarTitle>登录未成功</MessageBarTitle>
            请检查登录信息后重试。系统不会显示凭据或会话细节。
          </MessageBarBody>
        </MessageBar>
      ) : null}
      <form className="task-form" onSubmit={(event) => void login(event)}>
        <Field label="租户编码（需要租户工作区时填写）">
          <Input
            value={tenantCode}
            autoComplete="organization"
            disabled={state === "authenticating"}
            onChange={(_, data) => setTenantCode(data.value)}
          />
        </Field>
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
          appearance="primary"
          type="submit"
          disabled={state === "authenticating" || loginName.trim().length === 0 || password.length === 0}
        >
          {state === "authenticating" ? "正在验证身份" : "登录并进入工作区"}
        </Button>
      </form>
    </section>
  );
}
