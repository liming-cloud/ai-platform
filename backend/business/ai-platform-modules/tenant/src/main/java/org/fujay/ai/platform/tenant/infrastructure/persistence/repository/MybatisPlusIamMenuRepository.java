package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.repository.IamMenuRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamMenuPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusIamMenuRepository implements IamMenuRepository {

    private final IamMenuMapper menuMapper;

    public MybatisPlusIamMenuRepository(IamMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @Override
    public List<IamMenu> list(Long tenantId) {
        return menuMapper.selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(TenantPersistenceConverter::toIamMenu)
                .toList();
    }

    @Override
    public Optional<IamMenu> findByCode(Long tenantId, String menuCode) {
        return Optional.ofNullable(menuByCode(tenantId, menuCode)).map(TenantPersistenceConverter::toIamMenu);
    }

    @Override
    public List<IamMenu> findByCodes(Long tenantId, List<String> menuCodes) {
        if (menuCodes == null || menuCodes.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .in(IamMenuPO::getMenuCode, menuCodes)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(TenantPersistenceConverter::toIamMenu)
                .toList();
    }

    @Override
    public Optional<IamMenu> save(IamMenu menu) {
        if (menu.parentCode() != null && menuByCode(menu.tenantId(), menu.parentCode()) == null) {
            return Optional.empty();
        }
        IamMenuPO po = menuById(menu.tenantId(), menu.id());
        if (po == null) {
            if (codeUsedByOtherMenu(menu.tenantId(), menu.menuCode(), menu.id())) {
                return Optional.empty();
            }
            po = new IamMenuPO();
            po.setId(menu.id());
            po.setTenantId(menu.tenantId());
            po.setMenuCode(menu.menuCode());
            po.setCreatedAt(LocalDateTime.now());
        }
        applyMenu(po, menu.menuCode(), menu.menuName(), menu.parentCode(), menu.routePath(), menu.componentKey(),
                menu.platformScope(), menu.sortOrder(), menu.status(), LocalDateTime.now());
        persistMenu(po);
        return Optional.of(TenantPersistenceConverter.toIamMenu(po));
    }

    private IamMenuPO menuById(Long tenantId, Long menuId) {
        return menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .eq(IamMenuPO::getId, menuId));
    }

    private IamMenuPO menuByCode(Long tenantId, String menuCode) {
        return menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .eq(IamMenuPO::getMenuCode, menuCode));
    }

    private boolean codeUsedByOtherMenu(Long tenantId, String menuCode, Long menuId) {
        IamMenuPO same = menuByCode(tenantId, menuCode);
        return same != null && (menuId == null || !same.getId().equals(menuId));
    }

    private void applyMenu(IamMenuPO menu, String code, String name, String parent, String route,
            String component, String scope, Integer sortOrder, String status, LocalDateTime now) {
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setParentCode(parent);
        menu.setRoutePath(route);
        menu.setComponentKey(component);
        menu.setPlatformScope(scope);
        menu.setSortOrder(sortOrder);
        menu.setStatus(status);
        menu.setUpdatedAt(now);
    }

    private void persistMenu(IamMenuPO menu) {
        if (menuMapper.selectById(menu.getId()) == null) {
            menuMapper.insert(menu);
        } else {
            menuMapper.updateById(menu);
        }
    }
}
