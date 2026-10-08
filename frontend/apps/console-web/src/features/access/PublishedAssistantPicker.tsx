import { useEffect, useState } from "react";
import { Button } from "@fluentui/react-components";
import { presentApiError, type ControlPlaneClient, type MemberAssistantChoice, type PageResult } from "../../api/control-plane-client";

/** 搜索及翻页只改变候选视图，不能清除不在当前页的已选授权。 */
export function PublishedAssistantPicker({ client, selected, disabled, onChange, single = false, selectedName, selectFirst = false }: {
  client: ControlPlaneClient; selected: string[]; disabled: boolean; onChange: (ids: string[]) => void; single?: boolean; selectedName?: string; selectFirst?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [loaded, setLoaded] = useState<{ key: string; value: PageResult<MemberAssistantChoice> }>();
  const [error, setError] = useState<{ key: string; message: string }>();
  const [reload, setReload] = useState(0);
  const epoch = client.currentSessionRevision();
  const key = `${epoch}:${query}:${page}:${reload}`;
  const result = loaded?.key === key ? loaded.value : undefined;
  const failure = error?.key === key ? error.message : "";
  useEffect(() => {
    let mounted = true;
    client.listMemberAssistants(query, page, 20).then(value => {
      if (mounted && client.hasSession() && client.currentSessionRevision() === epoch) setLoaded({ key, value });
    }).catch(error => { if (mounted && client.currentSessionRevision() === epoch) setError({ key, message: presentApiError(error, "读取已发布助手").description }); });
    return () => { mounted = false; };
  }, [client, epoch, query, page, reload, key]);
  useEffect(() => {
    // 仅新建策略沿用原型的首项默认选择；成员多选和已有策略不得自动新增授权对象。
    if (selectFirst && single && !selected.length && result?.items[0] && !disabled
      && client.hasSession() && client.currentSessionRevision() === epoch) onChange([result.items[0].assistantId]);
  }, [client, epoch, selectFirst, single, selected, result, disabled, onChange]);
  const current = () => client.hasSession() && client.currentSessionRevision() === epoch && !disabled;
  const change = (id: string, checked: boolean) => {
    if (!current()) return;
    onChange(checked ? [...new Set([...selected, id])] : selected.filter(value => value !== id));
  };
  return <div className="published-assistant-picker">
    {single ? <label className="member-native-field"><span>已发布助手</span><select disabled={disabled || !result} value={selected[0] ?? ""}
      onChange={event => { if (current() && event.target.value) onChange([event.target.value]); }}>
      {selected[0] && !result?.items.some(item => item.assistantId === selected[0]) ? <option value={selected[0]}>{selectedName ?? "当前所选助手"}</option> : null}
      {result?.items.map(item => <option value={item.assistantId} key={item.assistantId}>{item.displayName}</option>)}
    </select></label> : null}
    {!single || (result?.totalElements ?? 0) > 20 || query ? <label className="member-native-field"><span>搜索已发布助手</span><input type="search" value={query} maxLength={80} placeholder="输入助手名称" disabled={disabled}
      onChange={event => { setQuery(event.target.value); setPage(1); }} /></label> : null}
    {!single ? <div className="assistant-choice-list">{result?.items.map(item => <label key={item.assistantId}>
      <input type="checkbox" checked={selected.includes(item.assistantId)} disabled={disabled} onChange={event => change(item.assistantId, event.target.checked)} /><span>{item.displayName}</span>
    </label>)}</div> : null}
    {!single || failure ? <p role="status">{result ? `匹配 ${result.totalElements} 个，已选 ${selected.length} 个${result.totalElements > 20 ? "；请输入更完整名称缩小范围" : ""}` : failure || "正在读取已发布助手…"}</p> : null}
    {failure ? <Button disabled={disabled} onClick={() => setReload(value => value + 1)}>重新读取助手</Button> : null}
    {result && result.totalPages > 1 ? <div className="member-pagination" aria-label="助手候选分页">
      <Button disabled={disabled || page <= 1} onClick={() => setPage(value => value - 1)}>上一页</Button><span>第 {page} / {result.totalPages} 页</span>
      <Button disabled={disabled || page >= result.totalPages} onClick={() => setPage(value => value + 1)}>下一页</Button>
    </div> : null}
  </div>;
}
