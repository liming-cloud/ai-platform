package com.aiplatform.foundation.modelruntime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 模型运行时核心依赖门禁，保证公共契约和应用编排不泄漏基础设施框架。
 */
class ModelRuntimeDependencyRulesTest {

    @Test
    void 公共契约和应用核心不得依赖基础设施框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.modelruntime");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.modelruntime.api..",
                        "com.aiplatform.foundation.modelruntime.application..")
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
