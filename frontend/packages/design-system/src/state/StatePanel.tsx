import {
  Button,
  MessageBar,
  MessageBarBody,
  MessageBarTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import type { ReactNode } from "react";
import "../styles/state-panel.css";

export interface StatePanelProps {
  kind: "empty" | "loading" | "error" | "denied" | "partial" | "stale";
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: ReactNode;
}

/**
 * 统一呈现产品的非成功状态，避免页面只设计理想路径。
 */
export function StatePanel({
  kind,
  title,
  description,
  actionLabel,
  onAction,
  icon,
}: StatePanelProps) {
  if (kind === "error" || kind === "partial" || kind === "stale") {
    return (
      <MessageBar intent={kind === "error" ? "error" : "warning"}>
        <MessageBarBody>
          <MessageBarTitle>{title}</MessageBarTitle>
          {description}
        </MessageBarBody>
      </MessageBar>
    );
  }

  return (
    <section className={`state-panel state-panel-${kind}`} aria-live="polite">
      {icon}
      <Title3>{title}</Title3>
      <Text className="state-description">{description}</Text>
      {actionLabel && onAction ? (
        <Button appearance="primary" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </section>
  );
}
