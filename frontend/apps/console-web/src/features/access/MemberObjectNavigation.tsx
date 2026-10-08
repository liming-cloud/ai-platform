import type { ConsoleRoute } from "../../session/console-route";

/** 已交付的对象页共享导航，尚未交付的助手页不提供伪造入口。 */
export function MemberObjectNavigation({ membershipId, current, onRouteChange }: {
  membershipId: string; current: "member-account" | "member-features"; onRouteChange: (route: ConsoleRoute) => void;
}) {
  return <nav className="member-object-navigation" aria-label="成员工作区">
    <button aria-current={current === "member-account" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "access", task: "member-account", membershipId })}>账号信息</button>
    <button aria-current={current === "member-features" ? "page" : undefined}
      onClick={() => onRouteChange({ navigationId: "access", task: "member-features", membershipId })}>对话功能</button>
    <button disabled>可用助手</button>
  </nav>;
}
