package com.aiplatform.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SharedKernelContractTest {
    @Test
    void errorCodeSupportsFrozenMultiSegmentApiContracts() {
        ErrorCode code = ErrorCode.of("AIP-IAM-RESOURCE-5004", "资源不可见");

        assertThat(code.code()).isEqualTo("AIP-IAM-RESOURCE-5004");
    }

    @Test
    void errorCodeKeepsStableCodeAndMessage() {
        ErrorCode errorCode = ErrorCode.of(" AIP-COMMON-0001 ", " 参数不合法 ");

        assertThat(errorCode.code()).isEqualTo("AIP-COMMON-0001");
        assertThat(errorCode.message()).isEqualTo("参数不合法");
        assertThat(errorCode.toString()).isEqualTo("AIP-COMMON-0001 参数不合法");
    }

    @Test
    void domainAndApplicationExceptionsExposeErrorCodeAndDetails() {
        ErrorCode errorCode = ErrorCode.of("AIP-KB-0404", "知识库不存在");

        DomainException domainException = new DomainException(errorCode, " kb-001 ");
        ApplicationException applicationException = new ApplicationException(errorCode, " app-001 ");

        assertThat(domainException.errorCode()).isEqualTo(errorCode);
        assertThat(domainException.detail()).isEqualTo("kb-001");
        assertThat(domainException.getMessage()).isEqualTo("AIP-KB-0404 知识库不存在: kb-001");
        assertThat(applicationException.errorCode()).isEqualTo(errorCode);
        assertThat(applicationException.detail()).isEqualTo("app-001");
    }

    @Test
    void domainBaseTypesExposeTypedIdentity() {
        TestAggregate aggregate = new TestAggregate("agg-001");
        TestEntity entity = new TestEntity("entity-001");
        TestValueObject valueObject = new TestValueObject("value-001");

        assertThat(aggregate.id()).isEqualTo("agg-001");
        assertThat(entity.id()).isEqualTo("entity-001");
        assertThat(valueObject.value()).isEqualTo("value-001");
    }

    @Test
    void errorCodeRejectsInvalidCodeFormat() {
        assertThatThrownBy(() -> ErrorCode.of("bad-code", "错误"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("错误码必须匹配 AIP-<MODULE>-<CODE>");
    }

    @Test
    void sharedKernelFactsRejectBlankValuesInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> TenantId.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> ErrorCode.of("AIP-COMMON-0001", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("错误消息不能为空");
        assertThatThrownBy(() -> new ApplicationException(ErrorCode.of("AIP-COMMON-0001", "参数不合法"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("应用异常详情不能为空");
        assertThatThrownBy(() -> new DomainException(ErrorCode.of("AIP-COMMON-0001", "参数不合法"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("领域异常详情不能为空");
        assertThatThrownBy(() -> new ApplicationException(null, "参数不合法"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("错误码不能为空");
        assertThatThrownBy(() -> new DomainException(null, "参数不合法"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("错误码不能为空");
    }

    @Test
    void tenantIdNormalizesRequiredValueBeforeUse() {
        TenantId tenantId = TenantId.of(" 2001 ");

        assertThat(tenantId.value()).isEqualTo("2001");
        assertThat(tenantId).isEqualTo(TenantId.of("2001"));
    }

    @Test
    void databaseIdParserNormalizesRequiredNumericIdBeforeUse() {
        assertThat(DatabaseIdParser.parseRequiredLong(" 2001 ", "租户 ID")).isEqualTo(2001L);
    }

    @Test
    void databaseIdParserRejectsMissingOrMalformedRequiredId() {
        assertThatThrownBy(() -> DatabaseIdParser.parseRequiredLong(null, "租户 ID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> DatabaseIdParser.parseRequiredLong(" ", "租户 ID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> DatabaseIdParser.parseRequiredLong("bad", "租户 ID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID 必须是数字型数据库主键");
    }

    @Test
    void tenantContextRequiresAuthenticatedTenantSource() {
        TenantContext.clear();

        assertThatThrownBy(TenantContext::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户上下文未初始化");

        TenantContext.set(TenantId.of("2001"));
        assertThat(TenantContext.current()).isEqualTo(TenantId.of("2001"));

        TenantContext.clear();
    }

    @Test
    void modelRuntimeContractsNormalizeSharedRuntimeFacts() {
        Set<String> capabilities = ModelRuntimeContracts.normalizeTextSet(
                Set.of(" chat ", "tool-calling"),
                "模型能力"
        );
        Map<String, Object> options = ModelRuntimeContracts.copyRequiredMap(
                Map.of("temperature", 0.2),
                "模型 options"
        );

        assertThat(ModelRuntimeContracts.requireModelConfigId(" 9001 ")).isEqualTo("9001");
        assertThat(ModelRuntimeContracts.requireProviderCode(" openai ")).isEqualTo("openai");
        assertThat(ModelRuntimeContracts.requireProviderKind(" openai-compatible ")).isEqualTo("openai-compatible");
        assertThat(ModelRuntimeContracts.requireModelCode(" gpt-4.1-mini ")).isEqualTo("gpt-4.1-mini");
        assertThat(ModelRuntimeContracts.requirePolicyVersion(" policy-v1 ")).isEqualTo("policy-v1");
        assertThat(capabilities).containsExactlyInAnyOrder("chat", "tool-calling");
        assertThat(options).containsEntry("temperature", 0.2);
    }

    @Test
    void modelRuntimeContractsRejectDamagedCollectionsInsteadOfJdkNullPointerException() {
        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.add(null);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", null);

        assertThatThrownBy(() -> ModelRuntimeContracts.normalizeTextSet(Set.of(" "), "模型能力"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型能力不能为空");
        assertThatThrownBy(() -> ModelRuntimeContracts.copyRequiredSet(capabilities, "模型能力"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型能力不能为空");
        assertThatThrownBy(() -> ModelRuntimeContracts.copyRequiredMap(options, "模型 options"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }

}

record TestAggregate(String id) implements AggregateRoot<String> {
}

record TestEntity(String id) implements DomainEntity<String> {
}

record TestValueObject(String value) implements ValueObject {
}
