import { PageHeading } from "@foundation/design-system";
import { Badge, Button, MessageBar, MessageBarBody, MessageBarTitle } from "@fluentui/react-components";
import { useEffect, useState } from "react";
import type { ControlPlaneClient } from "../../api/control-plane-client";
import type { ConsoleRoute } from "../../session/console-route";
import { currentTenantOverview, readTenantOverview, type TenantOverviewRead } from "./tenant-overview-loader";
import "./tenant-onboarding.css";

/** 新开户后及直接刷新均从真实读取接口恢复；仅展示该切片已经交付的事实。 */
export function TenantOverview({ client, tenantId, onRouteChange }: {
  client: ControlPlaneClient;
  tenantId: string;
  onRouteChange: (route: ConsoleRoute) => void;
}) {
  const [reading, setReading] = useState<TenantOverviewRead>();
  const [revision, setRevision] = useState(0);
  const owner = `${tenantId}:${revision}`;
  const { data, failure } = currentTenantOverview(reading, owner) ?? {};
  useEffect(() => {
    const cancel = readTenantOverview(client, tenantId, owner, setReading);
    const unsubscribe = client.subscribeSessionChange(active => { if (!active) { cancel(); setReading(undefined); } });
    return () => { cancel(); unsubscribe(); };
  }, [client, tenantId, owner]);
  const status = data?.tenant.status === "ACTIVE" ? "活动" : data?.tenant.status === "SUSPENDED" ? "已暂停" : "准备中";
  return <>
    <PageHeading title="租户概览" context="平台治理" summary={data?.tenant.displayName}
      status={data ? { label: status, tone: data.tenant.status === "ACTIVE" ? "success" : "warning" } : undefined}
      action={<Button className="tenant-overview__return" appearance="primary" onClick={() => onRouteChange({ navigationId: "platform", task: "tenants", page: 1 })}>返回租户目录</Button>} />
    <section className="tenant-onboarding tenant-overview">
      <nav className="tenant-overview__settings" aria-label="租户设置"><a href={`/platform/tenants/${encodeURIComponent(tenantId)}`} aria-current="page">概览</a></nav>
    {failure ? <><MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar><Button onClick={() => setRevision(value => value + 1)}>重新加载</Button></> : !data ? <p role="status">正在读取租户信息…</p> : <>
      <div className="tenant-overview__summary-grid">
        <div className="tenant-overview__identity">
          <div className="tenant-overview__identity-heading">
            <div className="tenant-overview__identity-copy"><span>基本信息</span><h2>{data.tenant.displayName}</h2></div>
            <Badge data-status={data.tenant.status} appearance="tint" color={data.tenant.status === "ACTIVE" ? "success" : "warning"}>
              <span aria-hidden="true">●</span>{status}
            </Badge>
          </div>
          <dl className="tenant-overview__identity-facts">
            <div><dt>租户编码</dt><dd className="tenant-overview__code">{data.tenant.tenantCode}</dd></div>
            <div><dt>租户管理员</dt><dd>{data.administrators.totalElements === 1 ? data.administrators.items[0]?.loginName : data.administrators.totalElements > 1 ? `${data.administrators.totalElements} 名` : "暂未分配"}</dd></div>
          </dl>
          <div className="tenant-overview__entry"><a href={`/tenant/login?tenantCode=${encodeURIComponent(data.tenant.tenantCode)}`}>租户管理员登录入口</a></div>
        </div>
        <aside className="tenant-overview__safety" aria-labelledby="tenant-safety-title"><h3 id="tenant-safety-title">安全保障</h3><p>基础保护已开启</p><ul><li>租户数据隔离</li><li>服务端权限校验</li><li>密码与密钥不回显</li></ul></aside>
      </div>
    </>}
  </section></>;
}
