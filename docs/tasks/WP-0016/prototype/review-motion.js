/** 审查器中的实际交互样本不修改业务数据，也不等待动画结束才更新结果。 */
export function installMotionReview(container) {
  if(!container)return;
  const section=document.createElement('section');section.className='motion-samples';section.dataset.odId='motion-samples';
  section.innerHTML='<h2>组件与动效样板</h2><label><input type="checkbox" data-motion-reduce>减少动态效果</label><div class="form-actions"><button class="button secondary" data-motion-button aria-pressed="false">切换按钮状态</button><button class="button secondary" data-motion-fast>快速连续操作</button></div><nav class="object-navigation" aria-label="内容切换样板"><button class="button secondary" data-motion-tab="overview">概览</button><button class="button secondary" data-motion-tab="result">结果</button></nav><div data-motion-content><h3>知识已准备</h3><p>可以开始版本配置。</p></div><section class="section-block"><h3>分步交互</h3><p data-motion-step aria-live="polite">步骤 1 / 3 · 基本信息</p><div class="form-actions"><button class="button secondary" data-motion-back disabled>上一步</button><button class="button secondary" data-motion-next>下一步</button></div></section><button class="button secondary" data-motion-dialog>打开确认弹窗</button><p role="status" data-motion-status></p>';
  const dialog=document.createElement('dialog');dialog.className='dialog-surface';dialog.setAttribute('aria-labelledby','motion-dialog-title');
  dialog.innerHTML='<header class="dialog-title"><h2 id="motion-dialog-title">确认样板操作？</h2></header><div class="dialog-body"><p>只更新审查器样板结果，不保存业务配置。</p></div><footer class="dialog-actions"><button class="button secondary" data-motion-cancel>取消</button><button class="button primary" data-motion-confirm>确认</button></footer>';
  container.append(section);document.body.append(dialog);let step=1;
  const reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches||section.querySelector('[data-motion-reduce]').checked;
  const animate=(element,duration=200,scale=false)=>{element.getAnimations().forEach(item=>item.cancel());if(!reduced())element.animate([{opacity:.5,transform:scale?'scale(.98)':'translateY(6px)'},{opacity:1,transform:'none'}],{duration,easing:'cubic-bezier(0.2,0,0,1)'});};
  const switchContent=key=>{const target=section.querySelector('[data-motion-content]');target.innerHTML=key==='result'?'<h3>结果可继续</h3><p>质量检查通过，可以请求发布。</p>':'<h3>知识已准备</h3><p>可以开始版本配置。</p>';section.querySelectorAll('[data-motion-tab]').forEach(button=>{if(button.dataset.motionTab===key)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});animate(target);};
  section.querySelectorAll('[data-motion-tab]').forEach(button=>button.onclick=()=>switchContent(button.dataset.motionTab));
  section.querySelector('[data-motion-button]').onclick=event=>{const button=event.currentTarget,on=button.getAttribute('aria-pressed')!=='true';button.setAttribute('aria-pressed',String(on));button.textContent=on?'已选中':'切换按钮状态';animate(button,150);};
  section.querySelector('[data-motion-fast]').onclick=()=>{switchContent('result');switchContent('overview');switchContent('result');section.querySelector('[data-motion-status]').textContent='已收敛到最后一次选择，没有排队等待。';};
  const paint=()=>{const target=section.querySelector('[data-motion-step]');target.textContent='步骤 '+step+' / 3 · '+['基本信息','使用权限','确认'][step-1];section.querySelector('[data-motion-back]').disabled=step===1;section.querySelector('[data-motion-next]').textContent=step===3?'重新开始':'下一步';animate(target);};
  section.querySelector('[data-motion-back]').onclick=()=>{step=Math.max(1,step-1);paint();};section.querySelector('[data-motion-next]').onclick=()=>{step=step===3?1:step+1;paint();};
  const trigger=section.querySelector('[data-motion-dialog]');trigger.onclick=()=>{dialog.showModal();animate(dialog,240,true);};
  dialog.querySelector('[data-motion-cancel]').onclick=()=>dialog.close();dialog.querySelector('[data-motion-confirm]').onclick=()=>{section.querySelector('[data-motion-status]').textContent='样板操作已确认。';dialog.close();};
  dialog.onclose=()=>{dialog.getAnimations().forEach(item=>item.cancel());trigger.focus();};
  section.querySelector('[data-motion-reduce]').onchange=event=>{
    section.classList.toggle('motion-reduced',event.target.checked);
    dialog.classList.toggle('motion-reduced',event.target.checked);
    section.getAnimations({subtree:true}).forEach(item=>item.cancel());dialog.getAnimations().forEach(item=>item.cancel());
  };
}
