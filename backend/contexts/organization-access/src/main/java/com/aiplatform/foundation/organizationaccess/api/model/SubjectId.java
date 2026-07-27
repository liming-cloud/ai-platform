package com.aiplatform.foundation.organizationaccess.api.model;

/**
 * 人员、服务或设备主体的不可变内部标识。
 */
public record SubjectId(long value) {

    public SubjectId {
        if (value <= 0) {
            throw new IllegalArgumentException("主体标识必须为正数");
        }
    }

    public static SubjectId parse(String value) {
        try {
            return new SubjectId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("主体标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
