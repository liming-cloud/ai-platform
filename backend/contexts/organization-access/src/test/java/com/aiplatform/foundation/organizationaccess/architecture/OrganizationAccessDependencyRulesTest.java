package com.aiplatform.foundation.organizationaccess.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class OrganizationAccessDependencyRulesTest {

    @Test
    void 领域应用和公共契约不得依赖SaToken或持久化框架() {
        var classes = new ClassFileImporter()
                .importPackages("com.aiplatform.foundation.organizationaccess");

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
                        "org.springframework..")
                .check(classes);
    }
}
