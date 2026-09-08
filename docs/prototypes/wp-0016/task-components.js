/** 只调整原型呈现，不持有权限或提交状态；字段仍归原来的表单所有。 */
export function enhanceTaskComponents(root) {
  // 仅增强明确的授权选择组，不改写包裹整页任务的 fieldset。
  root.querySelectorAll('fieldset.assistant-scope, fieldset.access-policy-members').forEach((fieldset,index)=>{
    const controls=[...fieldset.querySelectorAll('input[type="checkbox"][name]')];
    if(controls.length<=20 || new Set(controls.map(input=>input.name)).size!==1)return;
    const name=controls[0].name, options=controls.map(input=>({value:input.value,label:input.closest('label')?.textContent.trim()||input.value,input})), selected=new Set(controls.filter(input=>input.checked).map(input=>input.value));
    const legend=fieldset.querySelector('legend');fieldset.replaceChildren(...(legend?[legend]:[]));
    const search=document.createElement('input');search.type='search';search.className='input';search.setAttribute('aria-label',`搜索${legend?.textContent||'授权对象'}`);
    const list=document.createElement('div');list.className='bounded-choices';const values=document.createElement('div'), hint=document.createElement('p');hint.id=`scope-hint-${index}`;hint.setAttribute('role','status');search.setAttribute('aria-describedby',hint.id);fieldset.append(search,list,values,hint);
    const paint=()=>{
      const matches=options.filter(item=>item.label.includes(search.value.trim())), visible=matches.slice(0,20);
      // 复用原控件，保留业务属性、禁用标记与表单归属，不重新解释授权。
      list.replaceChildren(...visible.map(item=>{
        const label=document.createElement('label');label.className='check-line';
        item.input.checked=selected.has(item.value);label.append(item.input,document.createTextNode(item.label));return label;
      }));
      values.replaceChildren(...options.filter(item=>selected.has(item.value)&&!visible.includes(item)).map(item=>{
        const input=document.createElement('input');input.type='hidden';input.name=name;input.value=item.value;input.disabled=item.input.disabled;return input;
      }));
      hint.textContent=`匹配 ${matches.length} 项 · 已选 ${selected.size} 项 · 每次显示最多 20 项`;
    };
    search.addEventListener('input',paint);list.addEventListener('change',event=>{const input=event.target;if(input.checked)selected.add(input.value);else selected.delete(input.value);hint.textContent=`已选 ${selected.size} 项；搜索不会清除已选对象`;});paint();
  });
  root.querySelectorAll('dialog.dialog-surface').forEach(dialog=>{
    const host=dialog.querySelector(':scope > form')||dialog;
    if(host.querySelector(':scope > .dialog-body'))return;
    const heading=host.querySelector(':scope > .dialog-heading, :scope > .dialog-header');
    const title=heading||host.querySelector(':scope > h2');
    if(!title)return;
    const header=document.createElement('header');header.className='dialog-header';
    const kicker=host.querySelector(':scope > .section-kicker');if(kicker)header.append(kicker);
    header.append(title);
    const actions=host.querySelector(':scope > .release-actions, :scope > .form-actions, :scope > .dialog-actions');
    const body=document.createElement('div');body.className='dialog-body';
    [...host.childNodes].filter(node=>node!==actions).forEach(node=>body.append(node));
    host.append(header,body);if(actions){actions.classList.add('dialog-actions');host.append(actions);}
    const h2=header.querySelector('h2');if(h2){h2.id ||= `${dialog.id}-heading`;dialog.setAttribute('aria-labelledby',h2.id);}
  });
  root.querySelectorAll('.field').forEach((field,index)=>{
    const control=field.querySelector('input,select,textarea');if(!control)return;
    const helps=[...field.querySelectorAll('small,.field-hint,.field-help')];
    if(helps.length){const ids=helps.map((help,i)=>{help.id ||= `task-help-${index}-${i}`;return help.id;});control.setAttribute('aria-describedby',[control.getAttribute('aria-describedby'),...ids].filter(Boolean).join(' '));}
  });
  // 大量关联项用搜索选择；保留当前值，不另设目录分页，也不保存选择器的临时搜索。
  root.querySelectorAll('select:not([multiple])').forEach((select,index)=>{
    if(select.options.length<=20 || select.closest('#prototype-reviewer'))return;
    const options=[...select.options].map(option=>({value:option.value,label:option.textContent,disabled:option.disabled}));
    let value=select.value;
    if(!select.getAttribute('aria-label')&&!select.getAttribute('aria-labelledby'))select.setAttribute('aria-label',select.closest('label')?.querySelector('span')?.textContent||'关联对象');
    const search=document.createElement('input');search.type='search';search.className='input';search.placeholder='搜索可选对象';search.setAttribute('aria-label','搜索'+(select.closest('label')?.querySelector('span')?.textContent||'关联对象'));search.dataset.objectChoiceSearch='';
    const hint=document.createElement('small');hint.id=`choice-hint-${index}`;hint.className='field-hint';
    search.setAttribute('aria-describedby',hint.id);select.before(search);select.after(hint);
    const paint=()=>{const matches=options.filter(item=>item.label.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()));const visible=matches.slice(0,20);const selected=options.find(item=>item.value===value);if(selected&&!visible.some(item=>item.value===value))visible.unshift(selected);select.replaceChildren(...visible.map(item=>{const option=new Option(item.label,item.value);option.disabled=item.disabled;return option;}));select.value=value;hint.textContent=`匹配 ${matches.length} 项 · 最多展示 20 项，已选项保留`;};
    search.addEventListener('input',paint);select.addEventListener('change',()=>{value=select.value;});paint();
  });
}
