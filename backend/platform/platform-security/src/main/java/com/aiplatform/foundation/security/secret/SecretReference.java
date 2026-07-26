package com.aiplatform.foundation.security.secret;

import java.net.URI;
import java.util.Objects;

/**
 * 指向外部密钥存储条目的稳定引用。
 *
 * <p>该值对象只描述密钥位置，不包含密钥明文。</p>
 */
public record SecretReference(String value) {

    private static final int MAX_LENGTH = 512;

    public SecretReference {
        Objects.requireNonNull(value, "密钥引用不能为空");
        if (value.isBlank() || value.length() > MAX_LENGTH || !value.equals(value.trim())) {
            throw new IllegalArgumentException("密钥引用格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))
                    || Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("密钥引用不能包含空白或控制字符");
            }
        }

        var uri = URI.create(value);
        if (!"secret".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getPath() == null
                || uri.getPath().equals("/")
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("密钥引用必须使用 secret://存储/路径 格式");
        }
    }
}
