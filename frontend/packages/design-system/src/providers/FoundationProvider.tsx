import {
  FluentProvider,
  webLightTheme,
  type Theme,
} from "@fluentui/react-components";
import type { PropsWithChildren } from "react";
import "../styles/tokens.css";

const foundationLightTheme: Theme = {
  ...webLightTheme,
  colorBrandBackground: "#171a1c",
  colorBrandBackgroundHover: "#2b3033",
  colorBrandBackgroundPressed: "#090b0c",
  colorBrandForeground1: "#126b5c",
  colorBrandStroke1: "#126b5c",
  colorCompoundBrandForeground1: "#126b5c",
  colorCompoundBrandForeground1Hover: "#0c594d",
};

/**
 * 在应用根部统一 M6 浅色 Fluent UI 主题。
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
