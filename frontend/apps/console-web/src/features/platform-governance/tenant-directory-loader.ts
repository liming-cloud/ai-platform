import { presentApiError, type ControlPlaneClient, type PageResult,
  type TenantLifecycleSummary, type UserFacingError } from "../../api/control-plane-client";

/** 一次查询归属的只读结果；不同筛选、页码或刷新修订不能共用旧事实。 */
export interface TenantDirectoryRead {
  owner: string;
  page?: PageResult<TenantLifecycleSummary>;
  error?: UserFacingError;
}

/**
 * 取消、筛选切换和会话失效后丢弃迟到响应；会话通知不传递身份或凭据。
 * 返回的清理函数必须由当前页面的读取生命周期调用。
 */
export function readTenantDirectory(
  client: Pick<ControlPlaneClient, "listTenantLifecycles" | "hasSession" | "subscribeSessionChange">,
  owner: string,
  parameters: Readonly<Parameters<ControlPlaneClient["listTenantLifecycles"]>>,
  publish: (result: TenantDirectoryRead) => void,
): () => void {
  let active = client.hasSession();
  const unsubscribe = client.subscribeSessionChange(isActive => {
    if (active && !isActive) { active = false; publish({ owner }); }
  });
  if (active) {
    void client.listTenantLifecycles(...parameters).then(page => {
      if (active && client.hasSession()) publish({ owner, page });
    }).catch(error => {
      if (active && client.hasSession()) publish({ owner, error: presentApiError(error, "加载租户目录") });
    });
  }
  return () => { active = false; unsubscribe(); };
}

/** 只展示当前查询的结果，切换开始即隐藏旧页，不等待新请求完成。 */
export function currentTenantDirectory(read: TenantDirectoryRead | undefined, owner: string): TenantDirectoryRead | undefined {
  return read?.owner === owner ? read : undefined;
}
