package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;

/** 账户目录必须由框架执行分页，避免恢复独立 count 和手工 offset 分支。 */
class PlatformAccountPagingContractTest {

    @Test
    void 账户目录必须通过基础设施分页契约返回结果和总数() throws NoSuchMethodException {
        var method = PlatformAccountMapper.class.getMethod("list", IPage.class);

        assertEquals(IPage.class, method.getReturnType());
    }
}
