import { useState, type FormEvent } from "react";
import { Button } from "@fluentui/react-components";
import { directoryPageNumbers } from "@foundation/design-system";

/** 策略目录使用数据库总数；跳页越界反馈不改变当前页面。 */
export function AccessPagination({ total, page, size, disabled = false, label, onChange }: {
  total: number; page: number; size: 10 | 20 | 50 | 100; disabled?: boolean; label: string;
  onChange: (page: number, size: 10 | 20 | 50 | 100) => void;
}) {
  const [failure, setFailure] = useState(""); const pages = Math.max(1, Math.ceil(total / size));
  function jump(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const value = new FormData(event.currentTarget).get("page");
    const number = typeof value === "string" && /^[1-9]\d*$/.test(value) ? Number(value) : 0;
    if (!Number.isSafeInteger(number) || number < 1 || number > pages || (number - 1) * size > 100_000) { setFailure("请输入有效页码。"); return; }
    setFailure(""); onChange(number, size);
  }
  return <nav className="member-pagination access-pagination" aria-label={label}>
    <span className="member-pagination-summary" role="status">共 {total} 条 · {total ? (page - 1) * size + 1 : 0}–{Math.min(total, page * size)} 条</span>
    <label className="member-pagination-size">每页<select aria-label="每页条数" disabled={disabled} value={size} onChange={event => { setFailure(""); onChange(1, Number(event.target.value) as 10 | 20 | 50 | 100); }}>
      {[10, 20, 50, 100].map(value => <option key={value} value={value}>{value} 条</option>)}</select></label>
    <div className="member-pagination-pages"><Button disabled={disabled || page <= 1} onClick={() => onChange(page - 1, size)}>上一页</Button>
      {directoryPageNumbers(page, pages).map((number, index) => number === "…" ? <span key={`gap-${index}`} aria-hidden="true">…</span>
        : <Button key={number} aria-label={`第 ${number} 页`} aria-current={number === page ? "page" : undefined} appearance={number === page ? "primary" : "secondary"} disabled={disabled} onClick={() => onChange(number, size)}>{number}</Button>)}
      <Button disabled={disabled || page >= pages} onClick={() => onChange(page + 1, size)}>下一页</Button></div>
    <form key={`${page}:${size}`} className="member-pagination-jump" onSubmit={jump} noValidate><label>跳至<input name="page" type="number" aria-label="跳转页码" min={1} max={pages} defaultValue={String(page)} disabled={disabled} /></label><span>页</span><Button type="submit" disabled={disabled}>跳转</Button></form>
    {failure ? <span className="member-pagination-error" role="alert">{failure}</span> : null}
  </nav>;
}
