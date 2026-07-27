package com.aiplatform.foundation.knowledgedata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class KnowledgeDataDependencyRulesTest {

    @Test
    void 领域和应用层不得依赖技术框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.knowledgedata");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "..api..",
                        "..application..",
                        "..domain..")
                // package-info 仅使用 Spring Modulith 声明跨上下文命名接口，
                // 不参与业务执行，也不构成应用服务对技术框架的运行时依赖。
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "cn.dev33..",
                        "com.baomidou..",
                        "cn.hutool..",
                        "org.springframework..")
                .check(classes);
    }

    @Test
    void 领域层只能依赖自身JDK与共享内核() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.knowledgedata");

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
                        "com.aiplatform.foundation.modelruntime..",
                        "com.aiplatform.foundation.knowledgedata.application..",
                        "com.aiplatform.foundation.knowledgedata.interfaces..",
                        "com.aiplatform.foundation.knowledgedata.infrastructure..")
                .check(classes);
    }
}
