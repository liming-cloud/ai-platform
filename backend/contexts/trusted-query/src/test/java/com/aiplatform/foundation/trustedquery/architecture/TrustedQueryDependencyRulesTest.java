package com.aiplatform.foundation.trustedquery.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 可信查询核心依赖门禁，禁止框架和持久化类型进入公共业务边界。
 */
class TrustedQueryDependencyRulesTest {

    @Test
    void 领域和应用层不得依赖技术框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.trustedquery");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.trustedquery.application..",
                        "com.aiplatform.foundation.trustedquery.domain..")
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
                .importPackages("com.aiplatform.foundation.trustedquery");

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
                        "com.aiplatform.foundation.assistantdelivery..",
                        "com.aiplatform.foundation.evaluationoperations..",
                        "com.aiplatform.foundation.trustedquery.application..",
                        "com.aiplatform.foundation.trustedquery.interfaces..",
                        "com.aiplatform.foundation.trustedquery.infrastructure..")
                .check(classes);
    }
}
