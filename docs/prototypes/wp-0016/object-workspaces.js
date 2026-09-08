import { directoryPage, directoryPaginationMarkup } from './directory-pagination.js';
const e=value=>String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
export const sourceStageName=stage=>({RECEIVED:'已接收',SCANNING:'安全检查',EXTRACTING:'提取内容',EXTRACTED:'提取完成',CHUNKING:'内容分段',INDEXING:'建立索引',READY:'已就绪',FAILED:'处理失败'})[stage] || '待处理';
export const syntheticExtract='合成演示文档\n员工办理年假结转时，应先核对本人剩余额度，再向直属负责人提交申请。审批通过后由人事登记。本演示资料中的年假最多结转 5 天。\n此段文字是原型专用合成提取样本，不是上传文件的真实解析结果，也不是 PDF 页面渲染。';

/** 记录按事件序号读取一个窗口，替换旧窗口，不把完整记录铺入 DOM。 */
export function recordWindow(records, before=Infinity, limit=20) {
  return records.filter(item=>item.sequence<before).sort((a,b)=>a.sequence-b.sequence).slice(-Math.min(100,limit));
}
export function pagedTable(rows, columns, params=new URLSearchParams(), rowAction=()=> '') {
  const query=params.get('q')||'', matches=rows.filter(row=>String(row.name||'').includes(query));
  const page=directoryPage(matches.length,params.get('page'),params.get('size'));
  return `<div class="table-shell" data-object-directory><table class="data-table"><thead><tr>${columns.map(c=>`<th>${e(c.label)}</th>`).join('')}<th>操作</th></tr></thead><tbody>${matches.slice(page.start,page.end).map(row=>`<tr>${columns.map(c=>`<td data-label="${e(c.label)}">${e(row[c.key])}</td>`).join('')}<td data-label="操作">${rowAction(row)}</td></tr>`).join('')}</tbody></table><div class="pagination" data-object-pagination>${directoryPaginationMarkup(page,matches.length,'object')}</div></div>`;
}

/** 旧目录在进入 DOM 前切片，复用分页计算；行操作仍由现有绑定在本页行上接线。 */
export function boundedDirectoryMarkup(html, params=new URLSearchParams()) {
  const table=html.match(/<tbody>([\s\S]*?)<\/tbody>/);
  if(!table || !html.includes('class="pagination"'))return html;
  const query=(params.get('q')||'').toLowerCase();
  // 只处理原型模板的平面表格；错误详情附行与来源作为同一分页单元。
  const units=[];
  for(const row of table[1].match(/<tr\b[\s\S]*?<\/tr>/g)||[]) {
    if(row.includes('data-directory-row-detail') && units.length)units[units.length-1]+=row;
    else units.push(row);
  }
  const rows=units.filter(row=>!query||row.replace(/<[^>]*>/g,'').toLowerCase().includes(query));
  const page=directoryPage(rows.length,params.get('page'),params.get('size'));
  const barStart=html.indexOf('<div class="pagination"');
  let depth=0,barEnd=barStart;
  const tags=html.slice(barStart).matchAll(/<\/?div\b[^>]*>/g);
  for(const tag of tags){depth+=tag[0].startsWith('</')?-1:1;if(depth===0){barEnd=barStart+tag.index+tag[0].length;break;}}
  const pagination=`<div class="pagination" data-object-pagination>${directoryPaginationMarkup(page,rows.length,'object')}</div>`;
  return (html.slice(0,barStart)+pagination+html.slice(barEnd)).replace(table[0],`<tbody>${rows.slice(page.start,page.end).join('')}</tbody>`);
}
export function documentWorkspace(source, view='preview', params=new URLSearchParams()) {
  const nav=`<nav class="object-navigation" aria-label="文档工作区">${[['preview','内容预览'],['records','处理记录'],['versions','版本']].map(([key,label])=>`<button class="button secondary" data-document-view="${key}"${view===key?' aria-current="page"':''}>${label}</button>`).join('')}</nav>`;
  if(view==='records') {
    const records=source.processingRecords || [{sequence:1,stage:source.stage,detail:'初始合成状态快照；此前没有记录操作。'}];
    const visible=recordWindow(records,Number(params.get('before'))||Infinity);
    return nav+`<section class="section-block"><h2>处理记录</h2><p>仅记录原型中的状态变更，不代表后台运行日志。</p><ol class="timeline-list">${visible.map(item=>`<li data-event-sequence="${item.sequence}"><strong>序号 ${item.sequence} · ${sourceStageName(item.stage)}</strong><p>${e(item.detail)}</p></li>`).join('')}</ol><div class="form-actions"><button class="button secondary" data-record-before="${visible[0]?.sequence||1}"${!records.some(item=>item.sequence<(visible[0]?.sequence||1))?' disabled':''}>加载更早记录</button><button class="button secondary" data-record-before="">返回最新记录</button></div></section>`;
  }
  if(view==='versions') {
    const versions=[...(source.previousRevisions||[]),{revision:source.revision,name:source.name,sizeMb:source.sizeMb}].reverse().map(item=>({...item,version:`版本 ${String(item.revision).replace('rev-','')}`,size:`${item.sizeMb||0} MB`}));
    return nav+`<section class="section-block"><h2>文档版本</h2><p>更新文档生成新版本；重新处理不更换内容版本。</p>${pagedTable(versions,[{key:'version',label:'版本'},{key:'name',label:'文档名称'},{key:'size',label:'大小'}],params,item=>`<button class="button secondary" data-document-version="${e(item.revision)}">查看内容</button>`)}</section>`;
  }
  const revision=params.get('documentVersion');
  if(!revision && !['EXTRACTED','CHUNKING','INDEXING','READY'].includes(source.stage))return nav+'<section class="section-block"><h2>内容尚未就绪</h2><p>请在处理记录中推进或重试处理，提取完成后查看合成文本。</p><button class="button secondary" data-document-view="records">查看处理记录</button></section>';
  const selected=revision && revision!==source.revision ? source.previousRevisions?.find(item=>item.revision===revision) : source;
  return nav+`<section class="section-block document-preview" data-od-id="document-content-preview"><h2>${e(selected?.name||source.name)}</h2><p>合成提取文本 · ${e(selected?.revision||source.revision)}</p>${selected?`<div class="document-text">${e(selected.extractedText||syntheticExtract).split('\n').map(line=>`<p>${line}</p>`).join('')}</div>`:'<p role="alert">此版本不可用，请返回版本目录。</p>'}<details><summary>技术详情</summary><dl><dt>文档标识</dt><dd>${e(source.id)}</dd><dt>处理阶段</dt><dd>${e(source.stage)}</dd><dt>格式</dt><dd>${e(source.kind)}</dd></dl></details></section>`;
}
