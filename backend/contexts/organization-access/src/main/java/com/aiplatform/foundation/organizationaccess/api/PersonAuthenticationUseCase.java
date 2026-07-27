package com.aiplatform.foundation.organizationaccess.api;

import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.organizationaccess.api.model.PersonLoginCommand;

/**
 * 人员凭据认证的公开应用契约。
 */
public interface PersonAuthenticationUseCase {

    IssuedSession authenticate(PersonLoginCommand command);
}
