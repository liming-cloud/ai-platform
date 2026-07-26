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
  "complete",
];

const STEPS = [
  { stage: "login", label: "验证演示租户" },
  { stage: "space", label: "确定知识空间" },
  { stage: "content", label: "上传首份文本" },
  { stage: "publish", label: "核验并发布" },
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
        每一步都由服务端权限、幂等和版本事实校验。页面状态不能替代安全边界。
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
