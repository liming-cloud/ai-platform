import { Button, Select } from '@fluentui/react-components';
import { ChevronLeft20Regular, ChevronRight20Regular } from '@fluentui/react-icons';

export const PAGE_SIZE = 10;

export function Pagination({ page, pageSize = PAGE_SIZE, total, totalPages, onChange }: { page: number; pageSize?: number; total: number; totalPages?: number; onChange: (page: number, pageSize: number) => void }) {
  const pages = totalPages ?? (total === 0 ? 0 : Math.ceil(total / pageSize));
  return <nav className="pagination" aria-label="列表分页"><span>共 {total} 条 · 第 {total === 0 ? 0 : page} / {pages} 页</span><div><label>每页 <Select aria-label="每页条数" value={String(pageSize)} onChange={(_, data) => onChange(1, Number(data.value))}><option value="10">10</option><option value="20">20</option><option value="50">50</option></Select> 条</label><Button appearance="subtle" icon={<ChevronLeft20Regular />} aria-label="上一页" disabled={page <= 1} onClick={() => onChange(page - 1, pageSize)}>上一页</Button><Button appearance="subtle" icon={<ChevronRight20Regular />} aria-label="下一页" disabled={pages === 0 || page >= pages} onClick={() => onChange(page + 1, pageSize)}>下一页</Button></div></nav>;
}
