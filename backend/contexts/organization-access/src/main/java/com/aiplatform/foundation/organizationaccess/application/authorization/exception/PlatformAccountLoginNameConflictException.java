package com.aiplatform.foundation.organizationaccess.application.authorization.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/** 建立人员账户时发现全局登录名已经存在。 */
public final class PlatformAccountLoginNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public PlatformAccountLoginNameConflictException() {
        super(OrganizationAccessAuthorizationErrorCode.ACCOUNT_LOGIN_NAME_CONFLICT);
    }
}
