package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 由平台发布的厂家、端点模板和模型编码组合，配置方只能选择已发布条目。
 */
public final class ControlledModelCatalogEntry
        implements AggregateRoot<ControlledModelCatalogEntryId> {

    private static final int MAX_MODEL_CODE_CODE_POINTS = 160;

    private final ControlledModelCatalogEntryId id;
    private final ProviderKind providerKind;
    private final URI endpoint;
    private final String modelCode;
    private final ModelType modelType;
    private final ControlledModelCatalogEntryStatus status;
    private final long version;
    private final Instant createdAt;

    private ControlledModelCatalogEntry(
            ControlledModelCatalogEntryId id,
            ProviderKind providerKind,
            URI endpoint,
            String modelCode,
            ModelType modelType,
            ControlledModelCatalogEntryStatus status,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "受控模型目录条目标识不能为空");
        this.providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        this.endpoint = requireEndpoint(endpoint);
        this.modelCode = requireModelCode(modelCode);
        this.modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        this.status = Objects.requireNonNull(status, "受控模型目录条目状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("受控模型目录条目版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static ControlledModelCatalogEntry create(
            ControlledModelCatalogEntryId id,
            ProviderKind providerKind,
            URI endpoint,
            String modelCode,
            ModelType modelType,
            Instant createdAt) {
        return new ControlledModelCatalogEntry(
                id,
                providerKind,
                endpoint,
                modelCode,
                modelType,
                ControlledModelCatalogEntryStatus.DRAFT,
                0,
                createdAt);
    }

    public static ControlledModelCatalogEntry reconstitute(
            ControlledModelCatalogEntryId id,
            ProviderKind providerKind,
            URI endpoint,
            String modelCode,
            ModelType modelType,
            ControlledModelCatalogEntryStatus status,
            long version,
            Instant createdAt) {
        return new ControlledModelCatalogEntry(
                id, providerKind, endpoint, modelCode, modelType, status, version, createdAt);
    }

    public ControlledModelCatalogEntry publish() {
        if (status != ControlledModelCatalogEntryStatus.DRAFT) {
            throw new IllegalStateException("只有草稿受控模型目录条目可以发布");
        }
        return new ControlledModelCatalogEntry(
                id,
                providerKind,
                endpoint,
                modelCode,
                modelType,
                ControlledModelCatalogEntryStatus.PUBLISHED,
                version + 1,
                createdAt);
    }

    @Override
    public ControlledModelCatalogEntryId id() {
        return id;
    }

    public ProviderKind providerKind() {
        return providerKind;
    }

    public URI endpoint() {
        return endpoint;
    }

    public String modelCode() {
        return modelCode;
    }

    public ModelType modelType() {
        return modelType;
    }

    public ControlledModelCatalogEntryStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static URI requireEndpoint(URI value) {
        Objects.requireNonNull(value, "受控模型目录端点不能为空");
        if (!value.isAbsolute()
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("受控模型目录端点格式不正确");
        }
        return value;
    }

    private static String requireModelCode(String value) {
        Objects.requireNonNull(value, "模型编码不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_MODEL_CODE_CODE_POINTS) {
            throw new IllegalArgumentException("模型编码格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))
                    || Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("模型编码不能包含空白或控制字符");
            }
        }
        return value;
    }
}
