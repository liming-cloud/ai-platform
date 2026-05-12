package org.fujay.ai.platform.model.infrastructure.persistence.repository;

import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toModelOption;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toPlatformModelOptionPO;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toProviderTypeOption;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toProviderTypePO;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;
import org.fujay.ai.platform.model.domain.repository.PlatformModelCatalogRepository;
import org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformModelOptionMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformProviderTypeMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformModelOptionPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformProviderTypePO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusPlatformModelCatalogRepository implements PlatformModelCatalogRepository {

    private final PlatformProviderTypeMapper providerTypeMapper;
    private final PlatformModelOptionMapper modelOptionMapper;

    public MybatisPlusPlatformModelCatalogRepository(
            PlatformProviderTypeMapper providerTypeMapper, PlatformModelOptionMapper modelOptionMapper) {
        this.providerTypeMapper = providerTypeMapper;
        this.modelOptionMapper = modelOptionMapper;
    }

    @Override
    public List<PlatformProviderTypeOption> listEnabledProviderTypes() {
        return providerTypeMapper
                .selectList(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                        .eq(PlatformProviderTypePO::getStatus, "ENABLED")
                        .orderByAsc(PlatformProviderTypePO::getSortOrder))
                .stream()
                .map(ModelPersistenceConverter::toProviderTypeOption)
                .toList();
    }

    @Override
    public List<PlatformModelOption> listEnabledModelOptions() {
        return modelOptionMapper
                .selectList(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                        .eq(PlatformModelOptionPO::getStatus, "ENABLED")
                        .orderByAsc(PlatformModelOptionPO::getSortOrder))
                .stream()
                .map(ModelPersistenceConverter::toModelOption)
                .toList();
    }

    @Override
    public List<PlatformProviderTypeOption> listAllProviderTypes() {
        return providerTypeMapper
                .selectList(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                        .orderByAsc(PlatformProviderTypePO::getSortOrder))
                .stream()
                .map(ModelPersistenceConverter::toProviderTypeOption)
                .toList();
    }

    @Override
    public List<PlatformModelOption> listAllModelOptions() {
        return modelOptionMapper
                .selectList(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                        .orderByAsc(PlatformModelOptionPO::getSortOrder))
                .stream()
                .map(ModelPersistenceConverter::toModelOption)
                .toList();
    }

    @Override
    public void saveProviderType(PlatformProviderTypeOption option) {
        LocalDateTime now = LocalDateTime.now();
        PlatformProviderTypePO existing = providerTypeMapper.selectById(option.id());
        if (existing == null) {
            existing = providerTypeMapper.selectOne(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                    .eq(PlatformProviderTypePO::getProviderType, option.providerType().name()));
        }
        PlatformProviderTypePO po = toProviderTypePO(option);
        po.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        po.setUpdatedAt(now);
        if (existing == null) {
            providerTypeMapper.insert(po);
        } else {
            po.setId(existing.getId());
            providerTypeMapper.updateById(po);
        }
    }

    @Override
    public void saveModelOption(PlatformModelOption option) {
        LocalDateTime now = LocalDateTime.now();
        PlatformModelOptionPO existing = modelOptionMapper.selectById(option.id());
        if (existing == null) {
            existing = modelOptionMapper.selectOne(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                    .eq(PlatformModelOptionPO::getProviderTypeId, option.providerTypeId())
                    .eq(PlatformModelOptionPO::getModelCode, option.modelCode())
                    .eq(PlatformModelOptionPO::getCapabilityType, option.capabilityType().name()));
        }
        PlatformModelOptionPO po = toPlatformModelOptionPO(option);
        po.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        po.setUpdatedAt(now);
        if (existing == null) {
            modelOptionMapper.insert(po);
        } else {
            po.setId(existing.getId());
            modelOptionMapper.updateById(po);
        }
    }
}
