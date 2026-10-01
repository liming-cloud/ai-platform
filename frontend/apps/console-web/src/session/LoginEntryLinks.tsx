import type { LoginWorkspace } from "./login-workspace-choice";

/** 问答入口沿用既有租户认证；入口名称不能授予角色或替换当前身份。 */
export type LoginEntry = LoginWorkspace | "query";

const entries = [
  { id: "platform", href: "/login", label: "平台管理登录" },
  { id: "tenant", href: "/tenant/login", label: "租户管理员登录" },
  { id: "query", href: "/ask", label: "普通用户问答登录" },
] as const;

/** 保留原生导航语义；调用方在离开页面前销毁当前密码输入。 */
export function LoginEntryLinks({ entry, onNavigate }: {
  entry: LoginEntry;
  onNavigate: () => void;
}) {
  return <nav className="governance-login__entries" aria-label="其他登录入口">
    {entries.filter(item => item.id !== entry).map(item =>
      <a key={item.id} href={item.href} onClick={onNavigate}>{item.label}</a>)}
  </nav>;
}
