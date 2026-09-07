const SIZES = [10, 20, 50, 100];

/** 原型目录使用真实夹具行切页；生产实现仍须由服务端返回分页结果与总数。 */
export function directoryPage(total, requestedPage = 1, requestedSize = 20) {
  const size = SIZES.includes(Number(requestedSize)) ? Number(requestedSize) : 20;
  const pages = Math.max(1, Math.ceil(total / size));
  const page = Math.min(pages, Math.max(1, Number.isSafeInteger(Number(requestedPage)) ? Number(requestedPage) : 1));
  const selected = pages <= 7 ? Array.from({ length: pages }, (_, i) => i + 1)
    : [...new Set([1, ...Array.from({ length: 3 }, (_, i) => Math.min(pages - 1, Math.max(2, page - 1 + i))), pages])];
  const numbers = selected.flatMap((number, i) => i && number - selected[i - 1] > 1 ? ['…', number] : [number]);
  return { page, size, pages, start: (page - 1) * size, end: Math.min(total, page * size), numbers };
}

/** 筛选与分页各自维护可见性，避免换页后把上一页误认为筛选结果。 */
export function bindDirectoryPagination(root) {
  const bar = root.querySelector('.pagination');
  if (!bar) return;
  const rows = [...(bar.closest('.table-shell')?.querySelectorAll('tbody > tr') || root.querySelectorAll('#assistant-list > [data-assistant-id]'))]
    .filter(row => !row.querySelector('td[colspan]'));
  const search = root.querySelector('.toolbar input[type="search"], #assistant-search');
  const filter = root.querySelector('[data-account-status-filter], [data-directory-status]');
  const initial = new URL(location.href);
  let requestedPage = initial.searchParams.get('page') || 1;
  let requestedSize = initial.searchParams.get('size') || 20;
  for (const [control, key] of [[search, 'q'], [filter, 'status']]) {
    if (!control || !initial.searchParams.has(key)) continue;
    control.value = initial.searchParams.get(key);
    control.dispatchEvent(new Event(control === search ? 'input' : 'change', { bubbles: true }));
  }
  const paint = (historyMode = 'replaceState', focus = '') => {
    const matches = rows.filter(row => !row.hidden);
    const state = directoryPage(matches.length, requestedPage, requestedSize);
    requestedPage = state.page;
    requestedSize = state.size;
    rows.forEach(row => { row.dataset.pageHidden = 'true'; });
    matches.slice(state.start, state.end).forEach(row => { delete row.dataset.pageHidden; });
    root.querySelectorAll('[data-directory-row-detail]').forEach(detail => {
      const owner = detail.previousElementSibling;
      detail.dataset.pageHidden = String(!owner || owner.hidden || owner.dataset.pageHidden === 'true');
    });
    const button = (label, page, disabled, extra = '') => `<button class="button secondary compact" type="button" data-directory-page="${page}"${disabled ? ' disabled' : ''} ${extra}>${label}</button>`;
    bar.innerHTML = `<span class="pagination-summary" role="status">共 ${matches.length} 条 · ${matches.length ? state.start + 1 : 0}–${state.end} 条</span>
      <label class="pagination-size">每页<select class="select" aria-label="每页条数" data-page-size>${SIZES.map(size => `<option value="${size}"${size === state.size ? ' selected' : ''}>${size} 条</option>`).join('')}</select></label>
      <div class="pagination-actions">${button('上一页', state.page - 1, state.page === 1)}${state.numbers.map(number => number === '…' ? '<span aria-hidden="true">…</span>' : button(number, number, false, `aria-label="第 ${number} 页"${number === state.page ? ' aria-current="page"' : ''}`)).join('')}${button('下一页', state.page + 1, state.page === state.pages)}</div>
      <div class="pagination-jump"><label>跳至<input class="input" type="number" min="1" max="${state.pages}" value="${state.page}" aria-label="跳转页码" data-page-jump></label><span>页</span><button class="button secondary compact" type="button" data-jump-page>跳转</button></div><span class="pagination-error" role="alert"></span>`;
    bar.querySelectorAll('button, input, select').forEach((control, index) => {
      control.dataset.odId = `${root.querySelector('main')?.dataset.screen}-pagination-${index + 1}`;
      control.dataset.controlStatus = 'implemented';
    });
    const next = new URL(location.href);
    next.searchParams.set('page', String(state.page));
    next.searchParams.set('size', String(state.size));
    for (const [control, key] of [[search, 'q'], [filter, 'status']]) {
      if (control?.value) next.searchParams.set(key, control.value); else next.searchParams.delete(key);
    }
    if (next.href !== location.href) history[historyMode]({}, '', next);
    if (focus) bar.querySelector(focus)?.focus();
  };
  const jump = () => {
    const input = bar.querySelector('[data-page-jump]');
    const value = Number(input.value);
    if (!Number.isSafeInteger(value) || value < 1 || value > Number(input.max)) {
      bar.querySelector('[role="alert"]').textContent = `请输入 1–${input.max} 之间的整数页码。`;
      input.setAttribute('aria-invalid', 'true');
      input.focus();
      return;
    }
    requestedPage = value;
    paint('pushState', '[data-page-jump]');
  };
  bar.addEventListener('click', event => {
    const button = event.target.closest('[data-directory-page]');
    if (button && !button.disabled) { requestedPage = Number(button.dataset.directoryPage); paint('pushState', '[aria-current="page"]'); }
    if (event.target.closest('[data-jump-page]')) jump();
  });
  bar.addEventListener('change', event => {
    if (!event.target.matches('[data-page-size]')) return;
    requestedSize = Number(event.target.value); requestedPage = 1; paint('pushState', '[data-page-size]');
  });
  bar.addEventListener('keydown', event => {
    if (event.key === 'Enter' && event.target.matches('[data-page-jump]')) { event.preventDefault(); jump(); }
  });
  const reset = () => { requestedPage = 1; paint(); };
  search?.addEventListener('input', reset);
  filter?.addEventListener('change', reset);
  root.querySelector('[data-clear-assistant-search]')?.addEventListener('click', reset);
  paint();
}
