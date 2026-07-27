package com.aiplatform.foundation.organizationaccess.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class OrganizationAccessDependencyRulesTest {

    @Test
    void 领域层只能依赖JDK共享内核和自身领域() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiplatform.foundation.organizationaccess");

        classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "com.aiplatform.foundation.organizationaccess.domain..",
                        "com.aiplatform.foundation.shared.kernel..")
                .check(classes);
    }

    @Test
    void 应用层不得依赖接口层和基础设施层() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiplatform.foundation.organizationaccess");

        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..interfaces..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void 接口层不得依赖领域和基础设施实现() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiplatform.foundation.organizationaccess");

        noClasses()
                .that()
                .resideInAPackage("..interfaces..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..domain..", "..infrastructure..")
                .check(classes);
    }
}
