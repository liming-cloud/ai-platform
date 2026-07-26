import { BookInformation24Regular } from "@fluentui/react-icons";
import { ProductShell, StatePanel } from "@foundation/design-system";

const NAVIGATION = [
  { id: "ask", label: "开始提问" },
  { id: "history", label: "历史记录" },
  { id: "help", label: "使用说明" },
] as const;

export function QueryApp() {
  return (
    <ProductShell
      productName="可信知识问答"
      pageTitle="从已授权资料中获得答案"
      pageSummary="答案必须附带可核验来源。没有足够证据或没有访问权限时，系统会明确拒绝回答。"
      navigation={NAVIGATION}
      activeNavigationId="ask"
      accountLabel="演示访客"
    >
      <div className="query-empty-state">
        <StatePanel
          kind="empty"
          icon={<BookInformation24Regular aria-hidden />}
          title="当前没有已发布助手"
          description="管理员完成模型连接、知识空间索引和评测后，这里才会开放提问入口。"
        />
      </div>
    </ProductShell>
  );
}
