import { Button } from "@fluentui/react-components";
import { directoryPageNumbers } from "@foundation/design-system";
import { type FormEvent, useEffect, useRef, useState } from "react";

type PageSize = 10 | 20 | 50 | 100;

/** 搜索只提交已接受的有界词组；实际匹配与总数由服务端负责，不过滤当前页。 */
export function PlatformConnectionSearch({ query, disabled = false, onChange }: {
  query: string; disabled?: boolean; onChange: (query: string) => void;
}) {
  const [failure, setFailure] = useState("");
  return <div className="platform-connection-search"><label><span>搜索当前目录</span>
    <input type="search" placeholder="按名称或摘要搜索" value={query} disabled={disabled} aria-invalid={failure ? true : undefined}
      onChange={event => {
        if (Array.from(event.target.value).length > 64) { setFailure("目录搜索词不能超过六十四个字符。"); return; }
        setFailure(""); onChange(event.target.value);
      }} /></label>{failure ? <p role="alert">{failure}</p> : null}</div>;
}

/** 连接与发现目录共用原型分页；只导航服务端页，不缓存全量数据或截断当前响应。 */
export function PlatformConnectionPagination({ total, page, size, disabled, label, onChange }: {
  total: number; page: number; size: PageSize; disabled: boolean; label: string;
  onChange: (page: number, size: PageSize) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const maximum = Math.min(pages, Math.floor(100_000 / size) + 1);
  const [failure, setFailure] = useState("");
  const root = useRef<HTMLElement>(null);
  const pendingFocus = useRef<string>(undefined);
  useEffect(() => {
    if (disabled || !pendingFocus.current) return;
    root.current?.querySelector<HTMLElement>(pendingFocus.current)?.focus();
    pendingFocus.current = undefined;
  }, [page, size, disabled]);

  function change(next: number, nextSize: PageSize, focus: string) {
    if (disabled) return;
    setFailure(""); pendingFocus.current = focus; onChange(next, nextSize);
  }
  function jump(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled) return;
    const input = event.currentTarget.elements.namedItem("page") as HTMLInputElement;
    const target = /^[1-9]\d*$/.test(input.value) ? Number(input.value) : 0;
    if (!Number.isSafeInteger(target) || target < 1 || target > maximum) {
      setFailure(`请输入 1–${maximum} 之间的整数页码。`); input.focus(); return;
    }
    change(target, size, '[aria-label="跳转页码"]');
  }
  return <nav ref={root} className="platform-connection-pagination" aria-label={label}>
    <span className="platform-connection-pagination__summary" role="status">共 {total} 条 · {total && page <= pages ? (page - 1) * size + 1 : 0}–{total && page <= pages ? Math.min(total, page * size) : 0} 条</span>
    <label className="platform-connection-pagination__size">每页<select aria-label="每页条数" value={size} disabled={disabled}
      onChange={event => change(1, Number(event.target.value) as PageSize, '[aria-label="每页条数"]')}>
      {[10, 20, 50, 100].map(value => <option key={value} value={value}>{value} 条</option>)}</select></label>
    <div className="platform-connection-pagination__actions">
      <Button disabled={disabled || page <= 1} onClick={() => change(page - 1, size, '[aria-current="page"]')}>上一页</Button>
      {directoryPageNumbers(page, pages).map((number, index) => number === "…" ? <span key={`gap-${index}`} aria-hidden="true">…</span>
        : <Button key={number} appearance={number === page ? "primary" : "secondary"} aria-label={`第 ${number} 页`}
          aria-current={number === page ? "page" : undefined} disabled={disabled || number > maximum}
          onClick={() => change(number, size, '[aria-current="page"]')}>{number}</Button>)}
      <Button disabled={disabled || page >= maximum} onClick={() => change(page + 1, size, '[aria-current="page"]')}>下一页</Button>
    </div>
    <form key={`${page}:${size}`} className="platform-connection-pagination__jump" onSubmit={jump} noValidate>
      <label>跳至<input name="page" type="number" aria-label="跳转页码" aria-invalid={failure ? true : undefined}
        min={1} max={maximum} defaultValue={page} disabled={disabled} onInput={() => setFailure("")} /></label><span>页</span>
      <Button type="submit" disabled={disabled}>跳转</Button>
    </form>
    {failure ? <span className="platform-connection-pagination__error" role="alert">{failure}</span> : null}
  </nav>;
}
