package com.aiplatform.infra.springai.model.strategy.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 模型 options 的基础取值和校验工具，避免各 provider 策略重复解析弱类型 Map。
 */
public final class ModelOptionValues {
    private ModelOptionValues() {
    }

    public static Optional<Double> doubleValue(Map<String, Object> options, String key) {
        Optional<Object> option = optionValue(options, key);
        if (option.isEmpty()) {
            return Optional.empty();
        }
        Object value = option.get();
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException(key + " 必须是数字");
            }
            return Optional.of(parsed);
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (!Double.isFinite(parsed)) {
                    throw new IllegalArgumentException(key + " 必须是数字");
                }
                return Optional.of(parsed);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " 必须是数字", exception);
            }
        }
        throw new IllegalArgumentException(key + " 必须是数字");
    }

    public static Optional<Integer> integerValue(Map<String, Object> options, String key) {
        Optional<Object> option = optionValue(options, key);
        if (option.isEmpty()) {
            return Optional.empty();
        }
        Object value = option.get();
        if (value instanceof Integer number) {
            return Optional.of(number);
        }
        if (value instanceof Byte || value instanceof Short) {
            return Optional.of(((Number) value).intValue());
        }
        if (value instanceof Long number) {
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(key + " 必须是整数");
            }
            return Optional.of(number.intValue());
        }
        if (value instanceof BigInteger number) {
            try {
                return Optional.of(number.intValueExact());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(key + " 必须是整数", exception);
            }
        }
        if (value instanceof BigDecimal number) {
            try {
                return Optional.of(number.intValueExact());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(key + " 必须是整数", exception);
            }
        }
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            if (Double.isFinite(parsed)
                    && Math.rint(parsed) == parsed
                    && parsed >= Integer.MIN_VALUE
                    && parsed <= Integer.MAX_VALUE) {
                return Optional.of((int) parsed);
            }
            throw new IllegalArgumentException(key + " 必须是整数");
        }
        if (value instanceof String text) {
            try {
                return Optional.of(Integer.parseInt(text.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " 必须是整数", exception);
            }
        }
        throw new IllegalArgumentException(key + " 必须是整数");
    }

    public static Optional<String> stringValue(Map<String, Object> options, String key) {
        Optional<Object> option = optionValue(options, key);
        if (option.isEmpty()) {
            return Optional.empty();
        }
        Object value = option.get();
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }
        throw new IllegalArgumentException(key + " 必须是非空字符串");
    }

    public static Optional<Boolean> booleanValue(Map<String, Object> options, String key) {
        Optional<Object> option = optionValue(options, key);
        if (option.isEmpty()) {
            return Optional.empty();
        }
        Object value = option.get();
        if (value instanceof Boolean flag) {
            return Optional.of(flag);
        }
        if (value instanceof String text && !text.isBlank()) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return Optional.of(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return Optional.of(Boolean.FALSE);
            }
        }
        throw new IllegalArgumentException(key + " 必须是布尔值");
    }

    public static Optional<List<String>> stringListValue(Map<String, Object> options, String key) {
        Optional<Object> option = optionValue(options, key);
        if (option.isEmpty()) {
            return Optional.empty();
        }
        Object value = option.get();
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(key + " 必须是字符串数组");
        }
        List<String> normalized = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(key + " 必须是字符串数组且元素不能为空");
            }
            normalized.add(text.trim());
        }
        return Optional.of(List.copyOf(normalized));
    }

    public static void validateDoubleRange(
            Map<String, Object> options,
            String key,
            double min,
            double max,
            List<String> violations
    ) {
        if (!options.containsKey(key)) {
            return;
        }
        Optional<Double> value = validatedValue(() -> doubleValue(options, key), violations);
        if (value.isEmpty()) {
            return;
        }
        if (value.get() < min || value.get() > max) {
            violations.add(key + " 必须在 " + min + " 到 " + max + " 之间");
        }
    }

    public static void validateIntegerMin(
            Map<String, Object> options,
            String key,
            int min,
            List<String> violations
    ) {
        if (!options.containsKey(key)) {
            return;
        }
        Optional<Integer> value = validatedValue(() -> integerValue(options, key), violations);
        if (value.isEmpty()) {
            return;
        }
        if (value.get() < min) {
            violations.add(key + " 必须大于等于 " + min);
        }
    }

    public static void validateStringOption(Map<String, Object> options, String key, List<String> violations) {
        validateOptionalValue(() -> stringValue(options, key), options, key, violations);
    }

    public static void validateBooleanOption(Map<String, Object> options, String key, List<String> violations) {
        validateOptionalValue(() -> booleanValue(options, key), options, key, violations);
    }

    public static void validateStringList(Map<String, Object> options, String key, List<String> violations) {
        validateOptionalValue(() -> stringListValue(options, key), options, key, violations);
    }

    public static void validateRequiredStringOption(
            Map<String, Object> options,
            String key,
            String optionName,
            List<String> violations
    ) {
        if (!options.containsKey(key)) {
            violations.add(optionName + " 不能为空");
            return;
        }
        validateOptionalValue(() -> stringValue(options, key), options, key, violations);
    }

    private static Optional<Object> optionValue(Map<String, Object> options, String key) {
        Objects.requireNonNull(options, "模型 options 不能为空");
        Objects.requireNonNull(key, "模型 option key 不能为空");
        if (!options.containsKey(key)) {
            return Optional.empty();
        }
        Object value = options.get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return Optional.of(value);
    }

    private static <T> Optional<T> validatedValue(Supplier<Optional<T>> reader, List<String> violations) {
        try {
            return reader.get();
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
            return Optional.empty();
        }
    }

    private static <T> void validateOptionalValue(
            Supplier<Optional<T>> reader,
            Map<String, Object> options,
            String key,
            List<String> violations
    ) {
        if (!options.containsKey(key)) {
            return;
        }
        validatedValue(reader, violations);
    }
}
