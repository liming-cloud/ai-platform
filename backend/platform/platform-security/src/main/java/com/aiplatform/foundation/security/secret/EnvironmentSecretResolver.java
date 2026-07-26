package com.aiplatform.foundation.security.secret;

import java.net.URI;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 仅解析 {@code secret://environment/变量名} 的进程环境密钥适配器。
 */
public final class EnvironmentSecretResolver implements SecretResolver {

    private static final Pattern VARIABLE_NAME =
            Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private final Function<String, String> environment;

    public EnvironmentSecretResolver() {
        this(System::getenv);
    }

    EnvironmentSecretResolver(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "环境变量读取器不能为空");
    }

    @Override
    public SecretValue resolve(SecretReference reference) {
        Objects.requireNonNull(reference, "密钥引用不能为空");
        var uri = URI.create(reference.value());
        if (!"environment".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("当前密钥适配器只支持环境变量存储");
        }
        var variable = uri.getPath().substring(1);
        if (!VARIABLE_NAME.matcher(variable).matches()) {
            throw new IllegalArgumentException("环境变量密钥名称格式不正确");
        }
        var value = environment.apply(variable);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("环境变量密钥不存在或为空");
        }
        return SecretValue.copyOf(value.toCharArray());
    }
}
