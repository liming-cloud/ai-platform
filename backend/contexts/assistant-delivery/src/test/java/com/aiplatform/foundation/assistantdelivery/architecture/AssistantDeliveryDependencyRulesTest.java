package com.aiplatform.foundation.assistantdelivery.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 助手交付核心依赖门禁，保证公共契约和业务规则不泄漏基础设施框架。
 */
class AssistantDeliveryDependencyRulesTest {

    @Test
    void 公共契约和应用核心不得依赖基础设施框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.assistantdelivery");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.assistantdelivery.api..",
                        "com.aiplatform.foundation.assistantdelivery.application..",
                        "com.aiplatform.foundation.assistantdelivery.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.ai..",
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..")
                .check(classes);
    }
}
