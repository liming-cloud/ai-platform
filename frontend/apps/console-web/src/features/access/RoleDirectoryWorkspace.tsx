import {
  Badge,
  Button,
  Drawer,
  DrawerBody,
  DrawerHeader,
  DrawerHeaderTitle,
  Text,
  Title3,
} from "@fluentui/react-components";
import {
  ArrowRight20Regular,
  CheckmarkCircle20Regular,
  Dismiss20Regular,
  PeopleTeam20Regular,
  Person20Regular,
  ShieldLock20Regular,
} from "@fluentui/react-icons";
import { useState } from "react";

interface RoleCatalogEntry {
  code: "PLATFORM_ADMINISTRATOR" | "TENANT_ADMINISTRATOR" | "TENANT_USER";
  label: string;
  scopeLabel: string;
  purpose: string;
  capabilities: readonly string[];
  boundary: string;
}

export const platformRoleCatalog: readonly RoleCatalogEntry[] = [
  {
    code: "PLATFORM_ADMINISTRATOR",
    label: "平台管理员",
    scopeLabel: "平台范围",
    purpose: "建立平台账户和租户，维护模型供应链，并读取脱敏运营摘要。",
    capabilities: [
      "平台账户与管理员治理",
      "租户建立及生命周期管理",
      "平台模型连接与部署供应",
      "脱敏平台运营摘要",
      "平台管理会话",
    ],
    boundary: "不能读取租户问题、回答、引用正文、知识正文、成员明细或凭据。",
  },
] as const;

export const tenantRoleCatalog: readonly RoleCatalogEntry[] = [
  {
    code: "TENANT_ADMINISTRATOR",
    label: "租户管理员",
    scopeLabel: "当前租户",
    purpose: "完成成员、知识数据、模型、助手、评测与可信问答的租户内治理。",
    capabilities: [
      "成员与角色绑定管理",
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
    label: "普通用户",
    scopeLabel: "当前租户",
    purpose: "从已授权资料中发起可信问答、核验引用并提交反馈。",
    capabilities: [
      "可信问答任务",
      "已授权知识引用读取",
      "问答反馈提交",
    ],
    boundary: "不能管理成员、数据、模型、助手、评测、用量或审计。",
  },
] as const;

/**
 * 三角色目录只解释已接受的固定模板和安全边界；成员绑定仍由对应目录完成。
 */
export function RoleDirectoryWorkspace({
  scope,
  onManageMembers,
}: {
  scope: "PLATFORM" | "TENANT";
  onManageMembers: () => void;
}) {
  const catalog = scope === "PLATFORM" ? platformRoleCatalog : tenantRoleCatalog;
  const [selectedRole, setSelectedRole] = useState<RoleCatalogEntry>();

  return (
    <section className="role-directory" aria-label={scope === "PLATFORM" ? "平台角色目录" : "租户角色目录"}>
      <header className="task-heading role-directory__heading">
        <div>
          <span className="role-directory__eyebrow">固定角色 · 能力投影</span>
          <Title3>{scope === "PLATFORM" ? "平台角色" : "租户角色"}</Title3>
        </div>
        <Text>
          角色目录解释每个角色的任务、能力和边界。角色分配在成员目录完成，服务端授权始终是最终事实。
        </Text>
      </header>

      <div className="role-directory__trust-line">
        <ShieldLock20Regular aria-hidden="true" />
        <div>
          <strong>最小权限正在生效</strong>
          <Text>产品只发布三种人员角色；页面不会展示或接受内部权限标识。</Text>
        </div>
      </div>

      <div className="role-directory__list" role="list">
        {catalog.map((role) => (
          <article className="role-directory__row" role="listitem" key={role.code}>
            <div className="role-directory__identity">
              <span className="role-directory__icon" aria-hidden="true">
                {role.code === "PLATFORM_ADMINISTRATOR"
                  ? <ShieldLock20Regular />
                  : role.code === "TENANT_ADMINISTRATOR"
                    ? <PeopleTeam20Regular />
                    : <Person20Regular />}
              </span>
              <div>
                <div className="role-directory__name">
                  <strong>{role.label}</strong>
                  <Badge appearance="tint" color={role.code === "TENANT_USER" ? "informative" : "brand"}>
                    {role.scopeLabel}
                  </Badge>
                </div>
                <Text>{role.purpose}</Text>
              </div>
            </div>
            <div className="role-directory__capability-preview" aria-label={`${role.label}的主要能力`}>
              {role.capabilities.slice(0, 3).map((capability) => (
                <span key={capability}><CheckmarkCircle20Regular aria-hidden="true" />{capability}</span>
              ))}
              {role.capabilities.length > 3 ? <Text>另有 {role.capabilities.length - 3} 项能力</Text> : null}
            </div>
            <div className="task-actions role-directory__actions">
              <Button appearance="secondary" onClick={() => setSelectedRole(role)}>查看详情</Button>
              <Button appearance="subtle" icon={<ArrowRight20Regular />} iconPosition="after" onClick={onManageMembers}>
                管理成员
              </Button>
            </div>
          </article>
        ))}
      </div>

      <Drawer
        className="foundation-detail-drawer"
        type="overlay"
        position="end"
        size="medium"
        open={selectedRole !== undefined}
        onOpenChange={(_, data) => { if (!data.open) setSelectedRole(undefined); }}
      >
        <DrawerHeader>
          <DrawerHeaderTitle
            action={<Button appearance="subtle" aria-label="关闭角色详情" icon={<Dismiss20Regular />} onClick={() => setSelectedRole(undefined)} />}
          >
            角色详情
          </DrawerHeaderTitle>
        </DrawerHeader>
        <DrawerBody>
          {selectedRole === undefined ? null : (
            <div className="role-detail">
              <div className="role-detail__heading">
                <span className="role-detail__scope">{selectedRole.scopeLabel}</span>
                <Title3>{selectedRole.label}</Title3>
                <Text>{selectedRole.purpose}</Text>
              </div>
              <section aria-labelledby="role-capability-heading">
                <h3 id="role-capability-heading">已发布能力</h3>
                <ul>
                  {selectedRole.capabilities.map((capability) => (
                    <li key={capability}><CheckmarkCircle20Regular aria-hidden="true" /><span>{capability}</span></li>
                  ))}
                </ul>
              </section>
              <section className="role-detail__boundary" aria-labelledby="role-boundary-heading">
                <h3 id="role-boundary-heading">安全边界</h3>
                <Text>{selectedRole.boundary}</Text>
              </section>
              <section className="role-detail__configuration" aria-labelledby="role-configuration-heading">
                <h3 id="role-configuration-heading">权限配置状态</h3>
                <Text>当前版本由固定模板派生。修改能力集合需要版本化角色模板、受影响会话撤销和独立安全验收。</Text>
              </section>
              <Button appearance="primary" onClick={() => { setSelectedRole(undefined); onManageMembers(); }}>
                前往成员目录
              </Button>
            </div>
          )}
        </DrawerBody>
      </Drawer>
    </section>
  );
}
