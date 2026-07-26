package com.aiplatform.foundation.security.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class PlatformSecurityDependencyRulesTest {

    @Test
    void 安全平台模块不得依赖业务或框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.security");

        noClasses()
                .that()
                .resideInAPackage("com.aiplatform.foundation.security..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.ai..",
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..",
                        "com.aiplatform.foundation.modelruntime..")
                .check(classes);
    }
}
