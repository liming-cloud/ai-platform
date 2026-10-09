import { Button } from "@fluentui/react-components";
import { PageHeading } from "@foundation/design-system";
import "./platform-role-directory.css";
import "./tenant-role-directory.css";

interface RoleCatalogEntry {
  code: "PLATFORM_ADMINISTRATOR" | "TENANT_ADMINISTRATOR" | "TENANT_USER";
  label: string;
  scopeLabel: string;
  purpose: string;
  capabilities: readonly string[];
  boundary: string;
}

const platformAdministratorRole: RoleCatalogEntry = {
  code: "PLATFORM_ADMINISTRATOR",
  label: "平台管理员",
  scopeLabel: "平台范围",
  purpose: "建立平台账户和租户，维护模型供应链，并读取脱敏运营摘要。",
  capabilities: [
    "平台账户治理",
    "租户建立及生命周期管理",
    "平台模型连接与部署供应",
    "脱敏平台运营摘要",
    "平台管理会话",
  ],
  boundary: "不能读取租户问题、回答、引用正文、知识正文、成员明细或凭据。",
};

export const tenantRoleCatalog: readonly RoleCatalogEntry[] = [
  {
    code: "TENANT_ADMINISTRATOR",
    label: "租户管理员",
    scopeLabel: "当前租户",
    purpose: "管理本组织知识、模型、助手与普通成员",
    capabilities: [
      "普通成员管理",
      "知识数据读取和写入",
      "模型配置读取和写入",
      "助手配置读取和写入",
      "评测、用量与审计读取",
      "可信问答任务",
    ],
    boundary: "权限只在当前租户生效，不能取得平台治理能力或访问其他租户资源。",
  },
  {
    code: "TENANT_USER",
    label: "普通成员",
    scopeLabel: "当前租户",
    purpose: "仅使用已授权助手、本人历史与引用",
    capabilities: [
      "可信问答任务",
      "已授权知识引用读取",
      "问答反馈提交",
    ],
    boundary: "不能管理成员、数据、模型、助手、评测、用量或审计。",
  },
] as const;

/** 平台目录只解释已发布的平台模板，不把租户角色展示为平台授权选项。 */
export const platformRoleCatalog: readonly RoleCatalogEntry[] = [
  platformAdministratorRole,
] as const;

/**
 * 分别解释平台与租户的固定职责；租户页只导航普通成员治理，不开放角色编辑。
 */
export function RoleDirectoryWorkspace({
  scope,
  onManageMembers,
}: {
  scope: "PLATFORM" | "TENANT";
  onManageMembers: () => void;
}) {
  if (scope === "PLATFORM") return <>
    <PageHeading title="平台固定角色" context="平台治理" summary="平台管理员"
      status={{ label: "固定模板", tone: "success" }}
      action={<Button appearance="primary" onClick={onManageMembers}>管理账户</Button>} />
    <section className="platform-role-page" aria-label="平台固定角色职责">
      <div className="platform-role-page__workspace">
        <section className="platform-role-page__facts">
          <div className="platform-role-page__heading">
            <div><span className="platform-role-page__kicker">当前对象</span><h2>平台管理员</h2></div>
            <span className="page-status" data-tone="success">固定模板</span>
          </div>
          <dl className="platform-role-page__fact-list">
            <div><dt>平台治理</dt><dd>租户生命周期与模型供应链</dd></div>
            <div><dt>运营读取</dt><dd>仅脱敏聚合</dd></div>
            <div><dt>租户正文</dt><dd>禁止访问</dd></div>
            <div><dt>成员明细</dt><dd>禁止访问</dd></div>
          </dl>
        </section>
        <aside className="platform-role-page__attention">
          <span className="platform-role-page__kicker">需要关注</span>
          <h2>平台管理员只管理租户和模型供应，不读取租户知识或会话正文。</h2>
        </aside>
      </div>
    </section>
  </>;

  const facts = [
    ...tenantRoleCatalog.map((role) => [role.label, role.purpose]),
    ["自定义角色", "不属于当前产品契约"],
    ["授权依据", "服务端能力投影"],
  ];
  return <>
    <PageHeading title="租户固定角色" context="租户访问与知识" summary="租户角色模板"
      status={{ label: "2 个固定模板", tone: "success" }}
      action={<Button appearance="primary" onClick={onManageMembers}>管理成员</Button>} />
    <section className="tenant-role-page" aria-label="租户固定角色职责">
      <div className="tenant-role-page__workspace">
        <section className="tenant-role-page__facts">
          <div className="tenant-role-page__heading">
            <div><span className="tenant-role-page__kicker">当前对象</span><h2>租户角色模板</h2></div>
            <span className="page-status" data-tone="success">2 个固定模板</span>
          </div>
          <dl className="tenant-role-page__fact-list">
            {facts.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
          </dl>
        </section>
        <aside className="tenant-role-page__attention">
          <span className="tenant-role-page__kicker">需要关注</span>
          <h2>管理员与普通成员职责固定；配置成员功能不会改变角色。</h2>
        </aside>
      </div>
    </section>
  </>;
}
