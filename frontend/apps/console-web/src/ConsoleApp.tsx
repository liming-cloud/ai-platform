import {
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import {
  ArrowRight20Regular,
  CheckmarkCircle20Regular,
  Circle20Regular,
  ShieldLock20Regular,
} from "@fluentui/react-icons";
import { ProductShell, StatePanel } from "@foundation/design-system";

const NAVIGATION = [
  { id: "overview", label: "概览" },
  { id: "data", label: "数据" },
  { id: "assistants", label: "助手" },
  { id: "evaluation", label: "评测" },
  { id: "governance", label: "治理" },
  { id: "operations", label: "运维" },
] as const;

const FOUNDATION_STEPS = [
  {
    title: "建立租户与访问边界",
    description: "先确认管理员、演示用户和设备凭据能够访问哪些资源。",
    ready: false,
  },
  {
    title: "连接模型供应商",
    description: "保存密钥引用，探测聊天与向量能力后再允许发布。",
    ready: true,
  },
  {
    title: "发布知识空间",
    description: "数据完成解析、索引和权限校验后才能被助手使用。",
    ready: false,
  },
  {
    title: "评测并发布助手",
    description: "准确性、引用和权限评测通过后再开放问答入口。",
    ready: false,
  },
] as const;

export function ConsoleApp() {
  return (
    <ProductShell
      productName="可信知识平台"
      pageTitle="完成首个可信助手"
      pageSummary="控制台只展示当前发布链路所需的任务。未满足权限、模型、数据和评测门禁时，助手不会进入可用状态。"
      navigation={NAVIGATION}
      activeNavigationId="overview"
    >
      <MessageBar intent="info" className="usage-notice">
        <MessageBarBody>
          <MessageBarTitle>演示环境限制</MessageBarTitle>
          当前站点使用合成数据，并限制模型调用次数、文件大小和并发会话。
        </MessageBarBody>
      </MessageBar>

      <div className="console-workspace">
        <section className="release-path" aria-labelledby="release-path-title">
          <div className="section-heading">
            <ShieldLock20Regular aria-hidden />
            <Title3 id="release-path-title">发布路径</Title3>
          </div>
          <ol className="release-steps">
            {FOUNDATION_STEPS.map((step) => (
              <li key={step.title} className="release-step">
                {step.ready ? (
                  <CheckmarkCircle20Regular className="step-ready" aria-hidden />
                ) : (
                  <Circle20Regular className="step-pending" aria-hidden />
                )}
                <div>
                  <Text weight="semibold">{step.title}</Text>
                  <Text className="step-description">{step.description}</Text>
                </div>
              </li>
            ))}
          </ol>
          <Button
            appearance="primary"
            as="a"
            href="#model-governance"
            icon={<ArrowRight20Regular />}
            iconPosition="after"
          >
            配置模型连接
          </Button>
        </section>

        <aside id="model-governance" aria-label="模型治理状态">
          <StatePanel
            kind="partial"
            title="模型契约已就绪"
            description="本地确定性供应商探测已通过。真实供应商连接、密钥存储和出站地址策略仍在建设中。"
          />
        </aside>
      </div>
    </ProductShell>
  );
}
