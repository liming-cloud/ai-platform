import type { ReactNode } from "react";

/** 页面任务标题由页面拥有；动态对象摘要必须来自实际读取结果，不能放入外壳模拟。 */
export function PageHeading({ title, context, summary, status, action }: {
  title: string;
  context: string;
  summary?: ReactNode;
  status?: { label: string; tone: "warning" | "success" | "info" };
  action?: ReactNode;
}) {
  return <>
    <nav className="page-context" aria-label="当前位置">{context}</nav>
    <header className="page-heading">
      <div className="page-heading__copy"><h1 id="page-title">{title}</h1>
        {summary || status ? <div className="page-summary">{summary}{status ? <span className="page-status" data-tone={status.tone}>{status.label}</span> : null}</div> : null}
      </div>
      {action}
    </header>
  </>;
}
