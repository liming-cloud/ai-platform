import { Button } from "@fluentui/react-components";
import type { ConsoleRoute } from "../../session/console-route";

/** 复用原型对象工作区页签；路由继续使用同一个已授权平台模型入口。 */
export function PlatformModelSupplyNavigation({ active, onRouteChange }: {
  active: "connections" | "deployments"; onRouteChange: (route: ConsoleRoute) => void;
}) {
  return <nav className="platform-model-object-navigation" aria-label="对象工作区">
    <Button appearance="secondary" aria-current={active === "connections" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "connections", page: 1 })}>模型连接</Button>
    <Button appearance="secondary" aria-current={active === "deployments" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "platform-model-supply", task: "deployments", candidatePage: 1, deploymentPage: 1 })}>平台部署</Button>
  </nav>;
}
