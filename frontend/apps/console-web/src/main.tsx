import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { FoundationProvider } from "@foundation/design-system";
import { ConsoleApp } from "./ConsoleApp";
import "./styles.css";

const root = document.getElementById("root");
if (!root) {
  throw new Error("未找到前端挂载节点");
}

createRoot(root).render(
  <StrictMode>
    <FoundationProvider>
      <ConsoleApp />
    </FoundationProvider>
  </StrictMode>,
);
