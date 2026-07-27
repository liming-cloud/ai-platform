package com.aiplatform.foundation.evaluationoperations.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * 评测运维依赖门禁，保证领域规则和应用编排不泄漏技术框架。
 */
class EvaluationOperationsDependencyRulesTest {

    @Test
    void 领域和应用层不得依赖技术框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.evaluationoperations");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.aiplatform.foundation.evaluationoperations.application..",
                        "com.aiplatform.foundation.evaluationoperations.domain..")
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
                .importPackages("com.aiplatform.foundation.evaluationoperations");

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
                        "com.aiplatform.foundation.trustedquery..",
                        "com.aiplatform.foundation.evaluationoperations.application..",
                        "com.aiplatform.foundation.evaluationoperations.interfaces..",
                        "com.aiplatform.foundation.evaluationoperations.infrastructure..")
                .check(classes);
    }
}
