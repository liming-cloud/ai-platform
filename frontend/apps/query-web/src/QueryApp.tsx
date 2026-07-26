import {
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { ProductShell } from "@foundation/design-system";
import { QueryWorkspace } from "./features/query/QueryWorkspace";

const NAVIGATION = [
  { id: "ask", label: "可信问答" },
] as const;

export function QueryApp() {
  return (
    <ProductShell
      productName="可信知识问答"
      pageTitle="从已授权资料中获得答案"
      pageSummary="答案必须附带可核验来源。没有足够证据或没有访问权限时，系统会明确拒绝回答。"
      navigation={NAVIGATION}
      activeNavigationId="ask"
      accountLabel="受限演示环境"
    >
      <MessageBar intent="info" className="query-usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点只使用合成数据，并限制查询次数、并发会话和回答长度。
        </MessageBarBody>
      </MessageBar>
      <QueryWorkspace />
    </ProductShell>
  );
}
