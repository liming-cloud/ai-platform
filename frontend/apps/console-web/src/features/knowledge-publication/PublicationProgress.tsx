import { Text } from "@fluentui/react-components";
import {
  CheckmarkCircle20Filled,
  Circle20Regular,
  LockClosed20Regular,
} from "@fluentui/react-icons";
import type { PublicationStage } from "./publication-workflow";

const STEP_ORDER: PublicationStage[] = [
  "login",
  "space",
  "content",
  "publish",
  "index",
  "complete",
];

const STEPS = [
  { stage: "login", label: "验证租户会话" },
  { stage: "space", label: "选择知识库" },
  { stage: "content", label: "上传首份文本" },
  { stage: "publish", label: "发布文本数据" },
  { stage: "index", label: "构建检索索引" },
] as const;

export function PublicationProgress({ stage }: { stage: PublicationStage }) {
  return (
    <aside className="publication-progress" aria-label="发布进度">
      <div className="progress-heading">
        <LockClosed20Regular aria-hidden />
        <Text weight="semibold">可信发布路径</Text>
      </div>
      <ol>
        {STEPS.map((step) => {
          const done = isStageDone(stage, step.stage);
          const current = stage === step.stage;
          return (
            <li
              key={step.stage}
              className={current ? "progress-step current" : "progress-step"}
              aria-current={current ? "step" : undefined}
            >
              {done ? (
                <CheckmarkCircle20Filled aria-hidden />
              ) : (
                <Circle20Regular aria-hidden />
              )}
              <Text weight={current ? "semibold" : "regular"}>
                {step.label}
              </Text>
            </li>
          );
        })}
      </ol>
      <Text size={200} className="progress-note">
        每一步都会核验当前权限和提交状态；刷新页面不会重复执行已完成的操作。
      </Text>
    </aside>
  );
}

function isStageDone(
  current: PublicationStage,
  candidate: PublicationStage,
): boolean {
  return STEP_ORDER.indexOf(current) > STEP_ORDER.indexOf(candidate);
}
