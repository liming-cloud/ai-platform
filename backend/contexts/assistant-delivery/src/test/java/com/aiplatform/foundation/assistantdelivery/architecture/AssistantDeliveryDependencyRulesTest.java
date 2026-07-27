package com.aiplatform.foundation.assistantdelivery.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 助手交付核心依赖门禁，保证公共契约和业务规则不泄漏基础设施框架。
 */
class AssistantDeliveryDependencyRulesTest {

    @Test
    void 领域和应用层不得依赖技术框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.assistantdelivery");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.assistantdelivery.application..",
                        "com.aiplatform.foundation.assistantdelivery.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.ai..",
                        "org.springframework.boot..",
                        "org.springframework.context..",
                        "org.springframework.data..",
                        "org.springframework.stereotype..",
                        "org.springframework.transaction..",
                        "org.springframework.web..",
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..")
                .check(classes);
    }

    @Test
    void 领域层只能依赖自身JDK与共享内核() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.assistantdelivery");

        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.security..",
                        "com.aiplatform.foundation.shared.contract..",
                        "com.aiplatform.foundation.organizationaccess..",
                        "com.aiplatform.foundation.knowledgedata..",
                        "com.aiplatform.foundation.modelruntime..",
                        "com.aiplatform.foundation.trustedquery..",
                        "com.aiplatform.foundation.assistantdelivery.application..",
                        "com.aiplatform.foundation.assistantdelivery.interfaces..",
                        "com.aiplatform.foundation.assistantdelivery.infrastructure..")
                .check(classes);
    }
}
