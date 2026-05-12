package org.fujay.ai.platform.model.infrastructure.persistence.repository;

import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toInvocationPO;

import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;
import org.fujay.ai.platform.model.domain.repository.ModelInvocationRepository;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelInvocationRecordMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusModelInvocationRepository implements ModelInvocationRepository {

    private final ModelInvocationRecordMapper invocationMapper;

    public MybatisPlusModelInvocationRepository(ModelInvocationRecordMapper invocationMapper) {
        this.invocationMapper = invocationMapper;
    }

    @Override
    public void append(ModelInvocation invocation) {
        invocationMapper.insert(toInvocationPO(invocation));
    }
}
