package com.aiplatform.foundation.security.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记租户范围 HTTP 入口所需的平台功能上限；不替代成员与对象授权。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresTenantFeature {
    String value();
}
