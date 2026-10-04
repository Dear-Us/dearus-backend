package com.dearusback.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.dearusback";
    private static final List<String> BUSINESS_CONTEXTS = List.of(
            "identity",
            "family",
            "care",
            "voice",
            "growth",
            "insight"
    );

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
    }

    @Test
    void domainMustNotDependOnPresentation() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..presentation..")
                .because("domain code must stay independent of delivery details")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainMustNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("domain code must stay independent of technical details")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnPresentation() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..presentation..")
                .because("presentation depends on application, never the reverse")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void contextsMustNotDependOnAnotherContextsInfrastructure() {
        checkNoCrossContextDependencyOn("infrastructure");
    }

    @Test
    void contextsMustNotDependOnAnotherContextsPresentation() {
        checkNoCrossContextDependencyOn("presentation");
    }

    private void checkNoCrossContextDependencyOn(String targetLayer) {
        for (String sourceContext : BUSINESS_CONTEXTS) {
            String[] forbiddenPackages = BUSINESS_CONTEXTS.stream()
                    .filter(targetContext -> !targetContext.equals(sourceContext))
                    .map(targetContext -> BASE_PACKAGE + "." + targetContext + "." + targetLayer + "..")
                    .toArray(String[]::new);

            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + "." + sourceContext + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(forbiddenPackages)
                    .because("bounded contexts may communicate only through published application contracts")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }
}
