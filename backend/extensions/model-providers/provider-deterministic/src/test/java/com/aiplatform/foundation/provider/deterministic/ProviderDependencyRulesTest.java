package com.aiplatform.foundation.provider.deterministic;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 厂商扩展依赖门禁，扩展只能通过模型上下文公开契约接入。
 */
class ProviderDependencyRulesTest {

    @Test
    void 厂商扩展不得依赖模型上下文内部实现() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.provider.deterministic");

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.modelruntime.interfaces..",
                        "com.aiplatform.foundation.modelruntime.infrastructure..",
                        "com.aiplatform.foundation.modelruntime.application.provider..",
                        "com.aiplatform.foundation.modelruntime.application.deployment..",
                        "com.aiplatform.foundation.modelruntime.application.runtime.service..")
                .check(classes);
    }
}
