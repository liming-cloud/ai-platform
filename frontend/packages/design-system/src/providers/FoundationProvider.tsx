import {
  FluentProvider,
  webDarkTheme,
  webLightTheme,
} from "@fluentui/react-components";
import type { PropsWithChildren } from "react";
import { useEffect, useState } from "react";
import "../styles/tokens.css";

const DARK_MODE_QUERY = "(prefers-color-scheme: dark)";

/**
 * 在应用根部统一 Fluent UI 主题，并跟随系统外观设置。
 *
 * 单个页面不得覆盖主题，否则会破坏跨页面的一致性和无障碍对比度。
 */
export function FoundationProvider({ children }: PropsWithChildren) {
  const [darkMode, setDarkMode] = useState(
    () => typeof window !== "undefined" && window.matchMedia(DARK_MODE_QUERY).matches,
  );

  useEffect(() => {
    const media = window.matchMedia(DARK_MODE_QUERY);
    const update = (event: MediaQueryListEvent) => setDarkMode(event.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  return (
    <FluentProvider
      className="foundation-provider"
      theme={darkMode ? webDarkTheme : webLightTheme}
    >
      {children}
    </FluentProvider>
  );
}
