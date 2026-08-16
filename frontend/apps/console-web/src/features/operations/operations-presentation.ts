const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "活动",
  ANSWERED: "已回答",
  COMPLETED: "已完成",
  DISABLED: "已停用",
  DRAFT: "草稿",
  ENABLED: "已启用",
  FAILED: "失败",
  HEALTHY: "健康",
  INDEXING: "处理中",
  PENDING: "待处理",
  PENDING_ACTIVATION: "准备中",
  PROCESSING: "处理中",
  PUBLISHED: "已发布",
  READY: "就绪",
  REFUSED: "已拒答",
  RUNNING: "运行中",
  SUSPENDED: "暂停",
  UNAVAILABLE: "不可用",
  VERIFIED: "已验证",
};

export function isOverviewStale(staleAfter: string, now = Date.now()): boolean {
  return now >= Date.parse(staleAfter);
}

export function percentLabel(value: number): string {
  return new Intl.NumberFormat("zh-CN", {
    style: "percent",
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value);
}

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? "其他";
}

export function numberLabel(value: number): string {
  return new Intl.NumberFormat("zh-CN").format(value);
}
