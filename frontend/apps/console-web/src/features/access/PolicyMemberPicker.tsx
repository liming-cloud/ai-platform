import { useEffect, useState } from "react";
import { Button } from "@fluentui/react-components";
import { presentApiError, type AccessPolicyMemberChoice, type ControlPlaneClient, type PageResult } from "../../api/control-plane-client";

/** 普通成员候选由服务端分页；跨搜索已选集合保留，管理员永远不是候选。 */
export function PolicyMemberPicker({ client, selected, disabled, onChange, compact = false }: {
  client: ControlPlaneClient; selected: string[]; disabled: boolean; onChange: (ids: string[]) => void; compact?: boolean;
}) {
  const [query, setQuery] = useState(""); const [page, setPage] = useState(1);
  const [loaded, setLoaded] = useState<{ key: string; value: PageResult<AccessPolicyMemberChoice> }>(); const [error, setError] = useState<{ key: string; message: string }>();
  const [reload, setReload] = useState(0); const epoch = client.currentSessionRevision();
  const key = `${epoch}:${query}:${page}:${reload}`;
  const result = loaded?.key === key ? loaded.value : undefined;
  const failure = error?.key === key ? error.message : "";
  useEffect(() => {
    let mounted = true;
    client.listAccessPolicyMembers(query, page, 20).then(value => {
      if (mounted && client.hasSession() && client.currentSessionRevision() === epoch) setLoaded({ key, value });
    }).catch(error => { if (mounted && client.currentSessionRevision() === epoch) setError({ key, message: presentApiError(error, "读取普通成员").description }); });
    return () => { mounted = false; };
  }, [client, epoch, query, page, reload, key]);
  function change(id: string, checked: boolean) {
    if (disabled || !client.hasSession() || client.currentSessionRevision() !== epoch) return;
    onChange(checked ? [...new Set([...selected, id])] : selected.filter(value => value !== id));
  }
  return <fieldset className={compact ? "policy-member-options compact" : "policy-member-options"}>
    <legend>{compact ? "普通成员" : "授权普通成员"}</legend>
    {(result?.totalElements ?? 0) > 20 || query ? <label className="member-native-field"><span>搜索普通成员</span><input type="search" value={query} maxLength={80} disabled={disabled}
      onChange={event => { setQuery(event.target.value); setPage(1); }} /></label> : null}
    <div className="policy-member-grid">{result?.items.map(member => <label key={member.membershipId}>
      <input type="checkbox" checked={selected.includes(member.membershipId)} disabled={disabled} onChange={event => change(member.membershipId, event.target.checked)} />
      <span>{compact ? member.loginName : <><strong>{member.loginName}</strong><small>普通成员 · 只获得所选助手的对话与授权引用</small></>}</span>
    </label>)}</div>
    {failure ? <p role="alert">{failure} <Button onClick={() => setReload(value => value + 1)}>重新读取成员</Button></p> : !result ? <p role="status">正在读取普通成员…</p> : !result.items.length ? <p>没有匹配的普通成员。</p> : null}
    {result && (result.totalElements > 20 || query) ? <div className="member-pagination" aria-label="成员候选分页">
      <span>匹配 {result.totalElements} 个，已选 {selected.length} 个</span><Button disabled={disabled || page <= 1} onClick={() => setPage(value => value - 1)}>上一页</Button>
      <span>第 {page} / {Math.max(1, result.totalPages)} 页</span><Button disabled={disabled || !result.hasNext} onClick={() => setPage(value => value + 1)}>下一页</Button>
    </div> : null}
  </fieldset>;
}
