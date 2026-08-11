package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;

/** 平台账户的服务端分页读取端口。 */
public interface PlatformAccountDirectory {

    PageResult<PlatformAccountSummary> list(PageQuery query);
}
