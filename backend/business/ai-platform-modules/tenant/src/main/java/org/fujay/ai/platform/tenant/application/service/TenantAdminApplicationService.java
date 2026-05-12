package org.fujay.ai.platform.tenant.application.service;

import java.util.List;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.tenant.application.command.CreateTenantCommand;
import org.fujay.ai.platform.tenant.application.command.UpdateTenantCommand;
import org.fujay.ai.platform.tenant.application.dto.TenantProfile;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.application.port.CurrentTenantPrincipal;
import org.fujay.ai.platform.tenant.application.port.TenantIdGeneratorPort;
import org.fujay.ai.platform.tenant.application.port.TenantPasswordHashPort;
import org.fujay.ai.platform.tenant.application.port.TenantSecuritySessionPort;
import org.fujay.ai.platform.tenant.application.support.TenantProfileMapper;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuditRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantProvisioningRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantRepository;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminApplicationService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningRepository provisioningRepository;
    private final TenantAuditRepository auditRepository;
    private final TenantPasswordHashPort passwordHashPort;
    private final TenantSecuritySessionPort sessionPort;
    private final TenantIdGeneratorPort idGenerator;

    public TenantAdminApplicationService(
            TenantRepository tenantRepository,
            TenantProvisioningRepository provisioningRepository,
            TenantAuditRepository auditRepository,
            TenantPasswordHashPort passwordHashPort,
            TenantSecuritySessionPort sessionPort,
            TenantIdGeneratorPort idGenerator) {
        this.tenantRepository = tenantRepository;
        this.provisioningRepository = provisioningRepository;
        this.auditRepository = auditRepository;
        this.passwordHashPort = passwordHashPort;
        this.sessionPort = sessionPort;
        this.idGenerator = idGenerator;
    }

    @Transactional(readOnly = true)
    public List<TenantProfile> listTenantsForAdmin() {
        sessionPort.currentPrincipal();
        return tenantRepository.list().stream().map(TenantProfileMapper::toTenantProfile).toList();
    }

    @Transactional
    public TenantProfile createTenantForAdmin(CreateTenantCommand command) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String tenantCode = TenantApplicationAccessSpec.normalizeRequired(command.tenantCode(), "租户编码不能为空");
        String tenantName = TenantApplicationAccessSpec.normalizeRequired(command.tenantName(), "租户名称不能为空");
        String adminUsername = TenantApplicationAccessSpec.normalizeRequired(command.adminUsername(), "管理员用户名不能为空");
        String adminDisplayName = TenantApplicationAccessSpec.normalizeRequired(command.adminDisplayName(), "管理员名称不能为空");
        String adminPassword = TenantApplicationAccessSpec.normalizeRequired(command.adminPassword(), "管理员密码不能为空");
        String status = TenantApplicationAccessSpec.normalizeStatus(TenantApplicationAccessSpec.defaultStatus(command.status()));
        if (tenantRepository.findByCode(tenantCode).isPresent()) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }

        Long newTenantId = idGenerator.nextId();
        Long adminUserId = idGenerator.nextId();
        Tenant tenant = new Tenant(newTenantId, tenantCode, tenantName, status);
        TenantUser adminUser = TenantUser.standard(adminUserId, newTenantId, adminUsername, adminDisplayName, "ENABLED");
        UserCredential credential = new UserCredential(
                idGenerator.nextId(), newTenantId, adminUserId, passwordHashPort.hash(adminPassword));
        provisioningRepository.provisionTenant(tenant, adminUser, credential);
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "TENANT_CREATE", "SUCCESS", "租户 " + tenantCode + " 已创建"));
        return TenantProfileMapper.toTenantProfile(tenant);
    }

    @Transactional
    public TenantProfile updateTenantForAdmin(String tenantId, UpdateTenantCommand command) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String tenantName = TenantApplicationAccessSpec.normalizeRequired(command.tenantName(), "租户名称不能为空");
        String status = TenantApplicationAccessSpec.normalizeStatus(TenantApplicationAccessSpec.defaultStatus(command.status()));
        Tenant tenant = tenantRepository.findById(TenantApplicationAccessSpec.parseId(tenantId))
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST))
                .renameAndChangeStatus(tenantName, status);
        TenantProfile profile = TenantProfileMapper.toTenantProfile(tenantRepository.save(tenant));
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "TENANT_UPDATE", "SUCCESS", "租户基础信息已更新"));
        return profile;
    }

    @Transactional
    public TenantProfile updateTenantStatusForAdmin(String tenantId, String status) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String normalizedStatus = TenantApplicationAccessSpec.normalizeStatus(status);
        Tenant tenant = tenantRepository.findById(TenantApplicationAccessSpec.parseId(tenantId))
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST))
                .changeStatus(normalizedStatus);
        TenantProfile profile = TenantProfileMapper.toTenantProfile(tenantRepository.save(tenant));
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "TENANT_STATUS_UPDATE", "SUCCESS", "租户状态更新为 " + normalizedStatus));
        return profile;
    }
}
