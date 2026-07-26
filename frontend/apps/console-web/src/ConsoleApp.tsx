import {
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
} from "@fluentui/react-components";
import { ProductShell } from "@foundation/design-system";
import { KnowledgePublicationFlow } from "./features/knowledge-publication/KnowledgePublicationFlow";

const NAVIGATION = [
  { id: "overview", label: "概览" },
  { id: "data", label: "数据" },
  { id: "assistants", label: "助手" },
  { id: "evaluation", label: "评测" },
  { id: "governance", label: "治理" },
  { id: "operations", label: "运维" },
] as const;

export function ConsoleApp() {
  return (
    <ProductShell
      productName="可信知识平台"
      pageTitle="发布首份知识数据"
      pageSummary="用一个受控任务完成租户验证、知识空间创建、文本上传和发布核验。"
      navigation={NAVIGATION}
      activeNavigationId="data"
      accountLabel="受限演示环境"
    >
      <MessageBar intent="info" className="usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点使用合成数据，并限制模型调用次数、文件大小和并发会话。
        </MessageBarBody>
      </MessageBar>

      <KnowledgePublicationFlow />
    </ProductShell>
  );
}
