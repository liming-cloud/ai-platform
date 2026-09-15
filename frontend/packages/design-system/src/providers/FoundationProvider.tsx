import {
  FluentProvider,
  webLightTheme,
  type Theme,
} from "@fluentui/react-components";
import type { PropsWithChildren } from "react";
import "../styles/tokens.css";

const foundationLightTheme: Theme = {
  ...webLightTheme,
  colorBrandBackground: "var(--foundation-accent)",
  colorBrandBackgroundHover: "var(--foundation-accent-hover)",
  colorBrandBackgroundPressed: "var(--foundation-accent-pressed)",
  colorBrandForeground1: "var(--foundation-accent)",
  colorBrandStroke1: "var(--foundation-accent)",
  colorCompoundBrandForeground1: "var(--foundation-accent)",
  colorCompoundBrandForeground1Hover: "var(--foundation-accent-hover)",
  colorCompoundBrandBackground: "var(--foundation-accent)",
  colorCompoundBrandBackgroundHover: "var(--foundation-accent-hover)",
  colorCompoundBrandBackgroundPressed: "var(--foundation-accent-pressed)",
  colorCompoundBrandStroke: "var(--foundation-accent)",
  colorNeutralForeground1: "var(--foundation-text)",
  colorNeutralForeground2: "var(--foundation-muted-text)",
  colorNeutralBackground1: "var(--foundation-surface)",
  colorNeutralBackground2: "var(--foundation-subtle-surface)",
  colorNeutralBackground1Hover: "var(--foundation-hover-surface)",
  colorNeutralStroke1: "var(--foundation-border-strong)",
  fontFamilyBase: "var(--foundation-font-body)",
  fontSizeBase300: "16px",
  fontSizeBase200: "14px",
  lineHeightBase300: "1.6",
  lineHeightBase200: "1.4",
};

/**
 * 在应用根部把已验收原型语义令牌映射为 Fluent UI 主题。
 *
 * 单个页面不得覆盖主题，否则会破坏跨页面的一致性和无障碍对比度。
 */
export function FoundationProvider({ children }: PropsWithChildren) {
  return (
    <FluentProvider
      className="foundation-provider"
      theme={foundationLightTheme}
    >
      <div className="foundation-app-root">{children}</div>
    </FluentProvider>
  );
}
