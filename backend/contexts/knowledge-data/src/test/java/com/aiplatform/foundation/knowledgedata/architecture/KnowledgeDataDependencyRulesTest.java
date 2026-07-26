package com.aiplatform.foundation.knowledgedata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class KnowledgeDataDependencyRulesTest {

    @Test
    void 领域应用和公共契约不得依赖框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.knowledgedata");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "..api..",
                        "..application..",
                        "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..",
                        "org.springframework..")
                .check(classes);
    }
}
