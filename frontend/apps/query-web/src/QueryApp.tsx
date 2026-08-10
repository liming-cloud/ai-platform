import {
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { StatePanel, ProductShell } from "@foundation/design-system";
import {
  BrowserSessionExpirationMonitor,
  BrowserSessionInvalidationChannel,
  type BrowserSessionCapability,
} from "@foundation/browser-session";
import { useEffect, useMemo, useRef, useState } from "react";
import { QueryClient, QueryWorkspace } from "@foundation/query-workspace";

const NAVIGATION = [
  { id: "ask", label: "可信问答" },
] as const;

export function QueryApp() {
  const client = useMemo(() => new QueryClient(), []);
  const invalidationChannel = useMemo(
    () => new BrowserSessionInvalidationChannel(),
    [],
  );
  const expirationMonitor = useMemo(
    () => new BrowserSessionExpirationMonitor(),
    [],
  );
  const receivingInvalidation = useRef(false);
  const [state, setState] = useState<"restoring" | "ready" | "returning" | "failed">("restoring");
  const [capability, setCapability] = useState<BrowserSessionCapability>();

  useEffect(() => {
    let current = true;
    const unsubscribe = client.subscribeSessionChange((active) => {
      if (current && !active) {
        if (!receivingInvalidation.current) {
          invalidationChannel.publishInvalidation();
        }
        setCapability(undefined);
        setState("returning");
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
        if (restored === null || !restored.workspaceCategories.includes("TENANT")) {
          setCapability(undefined);
          setState("returning");
          return;
        }
        setCapability(restored);
        setState("ready");
      },
      () => {
        if (current) {
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
          if (!current) {
            return;
          }
          if (restored === null || !restored.workspaceCategories.includes("TENANT")) {
            setCapability(undefined);
            setState("returning");
            return;
          }
          if (client.hasSession()) {
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

  useEffect(() => {
    if (state === "returning") {
      window.location.replace("/?next=%2Fask");
    }
  }, [state]);

  if (state === "restoring") {
    return (
      <StatePanel
        kind="loading"
        title="正在恢复登录状态"
        description="正在验证当前浏览器是否有可用的查询工作区。"
      />
    );
  }

  if (state === "failed") {
    return (
      <StatePanel
        kind="error"
        title="暂时无法恢复登录状态"
        description="未展示任何受保护数据，请稍后重新加载。"
        actionLabel="重新加载"
        onAction={() => window.location.reload()}
      />
    );
  }

  if (state === "returning" || capability === undefined) {
    return (
      <StatePanel
        kind="stale"
        title="正在返回统一登录入口"
        description="查询页不保存凭据，也不提供局部登录入口。"
      />
    );
  }

  return (
    <ProductShell
      productName="可信知识问答"
      pageTitle="从已授权资料中获得答案"
      pageSummary="答案必须附带可核验来源。没有足够证据或没有访问权限时，系统会明确拒绝回答。"
      navigation={NAVIGATION}
      activeNavigationId="ask"
      accountLabel="已验证浏览器会话"
    >
      <MessageBar intent="info" className="query-usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点只使用合成数据，并限制查询次数、并发会话和回答长度。
        </MessageBarBody>
      </MessageBar>
      <QueryWorkspace client={client} />
    </ProductShell>
  );
}
