import { QueryClient, QueryWorkspace } from "@foundation/query-workspace";
import { StatePanel } from "@foundation/design-system";
import { useEffect, useMemo, useState } from "react";

type ViewState = "restoring" | "ready" | "stale" | "failed";

/**
 * 复用控制台已经签发的浏览器会话加载问答工作区，不在问答页建立第二个登录入口。
 */
export function ConsoleQueryWorkspace({ onSessionExpired }: { onSessionExpired: () => void }) {
  const client = useMemo(() => new QueryClient(), []);
  const [viewState, setViewState] = useState<ViewState>("restoring");

  useEffect(() => {
    let current = true;
    const timer = window.setTimeout(() => {
      void client.restoreSession().then(
        (capability) => {
          if (!current) {
            return;
          }
          setViewState(capability?.workspaceCategories.includes("TENANT") ? "ready" : "stale");
        },
        () => {
          if (current) {
            setViewState("failed");
          }
        },
      );
    }, 0);
    const unsubscribe = client.subscribeSessionChange((active) => {
      if (!active && current) {
        onSessionExpired();
      }
    });
    return () => {
      current = false;
      window.clearTimeout(timer);
      unsubscribe();
    };
  }, [client, onSessionExpired]);

  if (viewState === "restoring") {
    return (
      <StatePanel
        kind="loading"
        title="正在恢复问答工作区"
        description="正在确认当前统一浏览器会话具有租户问答权限。"
      />
    );
  }

  if (viewState === "stale") {
    return (
      <StatePanel
        kind="stale"
        title="当前浏览器会话不可用于问答"
        description="未加载助手目录或查询记录；请返回已授权工作区，或联系管理员分配问答权限。"
      />
    );
  }

  if (viewState === "failed") {
    return (
      <StatePanel
        kind="error"
        title="暂时无法恢复问答工作区"
        description="未展示任何受保护数据，请稍后重新加载。"
        actionLabel="重新加载"
        onAction={() => window.location.reload()}
      />
    );
  }

  return <QueryWorkspace client={client} />;
}
