package com.aiplatform.foundation.modelruntime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModelRuntimeDependencyRulesTest {

    @Test
    void 领域和应用层不得依赖技术框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.modelruntime");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.modelruntime.application..",
                        "com.aiplatform.foundation.modelruntime.domain..")
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
                .importPackages("com.aiplatform.foundation.modelruntime");

        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.shared.contract..",
                        "com.aiplatform.foundation.platform..",
                        "com.aiplatform.foundation.security..",
                        "com.aiplatform.foundation.organizationaccess..",
                        "com.aiplatform.foundation.knowledgedata..",
                        "com.aiplatform.foundation.assistantdelivery..",
                        "com.aiplatform.foundation.trustedquery..",
                        "com.aiplatform.foundation.modelruntime.application..",
                        "com.aiplatform.foundation.modelruntime.interfaces..",
                        "com.aiplatform.foundation.modelruntime.infrastructure..")
                .check(classes);
    }
}
