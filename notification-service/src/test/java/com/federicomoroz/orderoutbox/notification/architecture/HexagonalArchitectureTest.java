package com.federicomoroz.orderoutbox.notification.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Same five rules as {@code order-service}'s {@code HexagonalArchitectureTest}, replicated here
 * rather than shared, because the two modules deliberately share no code (see README
 * "Decisiones puntuales" on why there is no common JAR between the two services).
 */
class HexagonalArchitectureTest {

    private static final String BASE_PACKAGE = "com.federicomoroz.orderoutbox.notification";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    void domainDoesNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE_PACKAGE + ".application..", BASE_PACKAGE + ".adapter..");

        rule.check(importedClasses);
    }

    @Test
    void domainAndApplicationDoNotDependOnSpringJpaOrHibernate_noExceptions() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(BASE_PACKAGE + ".domain..", BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..");

        rule.check(importedClasses);
    }

    @Test
    void adapterDoesNotDependOnApplicationService_onlyOnApplicationPort() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".adapter..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE_PACKAGE + ".application.service..");

        rule.check(importedClasses);
    }

    @Test
    void respectsLayeredArchitecture_domainThenApplicationThenAdapterConfig() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy(BASE_PACKAGE + ".domain..")
                .layer("Application").definedBy(BASE_PACKAGE + ".application..")
                .layer("Adapter").definedBy(BASE_PACKAGE + ".adapter..")
                .layer("Config").definedBy(BASE_PACKAGE + ".config..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Config").mayNotBeAccessedByAnyLayer();

        rule.check(importedClasses);
    }

    @Test
    void isFreeOfPackageCycles() {
        ArchRule rule = slices().matching(BASE_PACKAGE + ".(*)..").should().beFreeOfCycles();

        rule.check(importedClasses);
    }
}
