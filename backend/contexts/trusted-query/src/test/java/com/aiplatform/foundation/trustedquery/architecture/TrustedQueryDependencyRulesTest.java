package com.aiplatform.foundation.trustedquery.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 可信查询核心依赖门禁，禁止框架和持久化类型进入公共业务边界。
 */
class TrustedQueryDependencyRulesTest {

    @Test
    void 公共契约应用核心和领域不得依赖基础设施框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.trustedquery");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.trustedquery.api..",
                        "com.aiplatform.foundation.trustedquery.application..",
                        "com.aiplatform.foundation.trustedquery.domain..")
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
