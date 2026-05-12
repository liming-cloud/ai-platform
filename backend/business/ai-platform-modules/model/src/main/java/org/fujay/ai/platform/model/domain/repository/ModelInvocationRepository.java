package org.fujay.ai.platform.model.domain.repository;

import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;

public interface ModelInvocationRepository {

    void append(ModelInvocation invocation);
}
