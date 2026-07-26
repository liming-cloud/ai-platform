package com.aiplatform.foundation.provider.openaicompatible;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleDependencyRulesTest {

    @Test
    void 供应商扩展不得依赖控制器和持久化实现() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.provider.openaicompatible");

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..controller..",
                        "..persistence..",
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..")
                .check(classes);
    }
}
