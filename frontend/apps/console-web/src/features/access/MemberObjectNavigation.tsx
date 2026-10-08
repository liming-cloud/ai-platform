import type { ConsoleRoute } from "../../session/console-route";

/** 成员对象页独立保存，通过同一对象导航进入各自的真实配置。 */
export function MemberObjectNavigation({ membershipId, current, onRouteChange }: {
  membershipId: string; current: "member-account" | "member-features" | "member-assistants"; onRouteChange: (route: ConsoleRoute) => void;
}) {
  return <nav className="member-object-navigation" aria-label="成员工作区">
    <button aria-current={current === "member-account" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "access", task: "member-account", membershipId })}>账号信息</button>
    <button aria-current={current === "member-features" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "access", task: "member-features", membershipId })}>对话功能</button>
    <button aria-current={current === "member-assistants" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "access", task: "member-assistants", membershipId })}>可用助手</button>
  </nav>;
}
