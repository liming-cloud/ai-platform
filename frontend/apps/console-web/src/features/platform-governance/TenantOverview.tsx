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
  return <section className="tenant-onboarding tenant-overview">
    <header className="tenant-onboarding__heading"><h2>租户概览</h2><Button onClick={() => onRouteChange({ navigationId: "platform", task: "tenants", page: 1 })}>返回租户目录</Button></header>
    {failure ? <><MessageBar intent="error"><MessageBarBody><MessageBarTitle>{failure.title}</MessageBarTitle>{failure.description}</MessageBarBody></MessageBar><Button onClick={() => setRevision(value => value + 1)}>重新加载</Button></> : !data ? <p role="status">正在读取租户信息与配额…</p> : <>
      <div className="tenant-overview__identity"><span className="tenant-overview__monogram" aria-hidden="true">{Array.from(data.tenant.displayName)[0]}</span><div className="tenant-overview__identity-copy"><h3>{data.tenant.displayName}</h3><p>{data.tenant.tenantCode}</p></div><Badge appearance="tint" color={data.tenant.status === "ACTIVE" ? "success" : "warning"}>{data.tenant.status === "ACTIVE" ? "活动" : data.tenant.status === "SUSPENDED" ? "已暂停" : "准备中"}</Badge></div>
      <section aria-labelledby="tenant-quota-title"><h3 id="tenant-quota-title">资源配额</h3><dl className="tenant-overview__facts">
        <div><dt>知识库数量</dt><dd>{data.quotas.knowledgeBases} 个</dd></div>
        <div><dt>每库文档数量</dt><dd>{data.quotas.documentsPerKnowledgeBase} 个</dd></div>
        <div><dt>每库内容容量</dt><dd>{decimalBytes(data.quotas.bytesPerKnowledgeBase, 1_000_000_000n)} GB</dd></div>
        <div><dt>单文件大小</dt><dd>{decimalBytes(data.quotas.maxFileBytes, 1_000_000n)} MB</dd></div>
        <div><dt>粘贴文本大小</dt><dd>{decimalBytes(data.quotas.maxTextBytes, 1_048_576n)} MiB</dd></div>
      </dl></section>
      <div className="tenant-overview__entry"><div><h3>管理员访问</h3><p>需先退出当前平台身份，再使用创建时设置的管理员账号与密码登录。</p></div><a href={`/tenant/login?tenantCode=${encodeURIComponent(data.tenant.tenantCode)}`}>租户管理员登录入口</a></div>
    </>}
  </section>;
}

/** 展示整数字节的精确十进制值，不把大容量转换成浮点数。 */
function decimalBytes(value: string, unit: bigint): string {
  let remainder = BigInt(value) % unit;
  const whole = BigInt(value) / unit;
  let fraction = "";
  while (remainder > 0n && fraction.length < 20) {
    remainder *= 10n;
    fraction += (remainder / unit).toString();
    remainder %= unit;
  }
  return whole.toString() + (fraction ? `.${fraction}` : "");
}
