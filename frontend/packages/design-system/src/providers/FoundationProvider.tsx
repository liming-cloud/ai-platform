import {
  FluentProvider,
  webLightTheme,
} from "@fluentui/react-components";
import type { PropsWithChildren } from "react";
import "../styles/tokens.css";

/**
 * 在应用根部统一 M6 浅色 Fluent UI 主题。
 *
 * 单个页面不得覆盖主题，否则会破坏跨页面的一致性和无障碍对比度。
 */
export function FoundationProvider({ children }: PropsWithChildren) {
  return (
    <FluentProvider
      className="foundation-provider"
      theme={webLightTheme}
    >
      {children}
    </FluentProvider>
  );
}
