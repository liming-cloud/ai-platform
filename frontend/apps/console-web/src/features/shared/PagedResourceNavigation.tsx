import { Button, Text } from "@fluentui/react-components";

/**
 * 受控资源选择器的最小分页操作栏。
 *
 * <p>调用方拥有数据读取、权限与路由状态；本组件只呈现服务端分页事实及前后页操作，避免把全量资源平铺到页面。</p>
 */
export function PagedResourceNavigation({
  label,
  page,
  total,
  hasPrevious,
  hasNext,
  disabled,
  onPrevious,
  onNext,
}: {
  label: string;
  page: number;
  total: number;
  hasPrevious: boolean;
  hasNext: boolean;
  disabled: boolean;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return (
    <div className="task-actions" aria-label={`${label}分页`}>
      <Text size={200}>{label}：第 {page} 页，共 {total} 条</Text>
      <Button appearance="subtle" disabled={disabled || !hasPrevious} onClick={onPrevious}>上一页</Button>
      <Button appearance="subtle" disabled={disabled || !hasNext} onClick={onNext}>下一页</Button>
    </div>
  );
}
