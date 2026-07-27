package com.aiplatform.foundation.persistence.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class PlatformPersistenceDependencyRulesTest {

    @Test
    void 平台持久化不得依赖业务上下文运行模块或厂商扩展() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.persistence");

        noClasses()
                .that()
                .resideInAPackage("com.aiplatform.foundation.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.organizationaccess..",
                        "com.aiplatform.foundation.knowledgedata..",
                        "com.aiplatform.foundation.modelruntime..",
                        "com.aiplatform.foundation.assistantdelivery..",
                        "com.aiplatform.foundation.trustedquery..",
                        "com.aiplatform.foundation.evaluationoperations..",
                        "com.aiplatform.foundation.controlplane..",
                        "com.aiplatform.foundation.queryruntime..",
                        "com.aiplatform.foundation.ingestionworker..",
                        "cn.dev33..",
                        "org.springframework.ai..",
                        "cn.hutool..")
                .check(classes);
    }
}
