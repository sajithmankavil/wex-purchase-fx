package com.example.purchaseconversion.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit fitness functions per component-design.md §6 (intent fixed; this is the M1 realisation).
 *
 * <p>Enforces:
 * <ul>
 *   <li>{@code domain} is framework-free (no Spring / JPA imports).</li>
 *   <li>{@code domain} only depends on itself, the JDK, and the explicitly-approved
 *       {@code uuid-creator} library for v7 UUID generation (ADR-0001 D-7).</li>
 *   <li>{@code application} only depends on itself, {@code domain}, and JDK
 *       (now actively enforced — Chunk A2 introduced the {@code application} package).</li>
 *   <li>{@code application} contains no Spring stereotypes (added in Chunk A2 per the
 *       A2 prompt; ports + services + exceptions stay pure POJOs until {@code config}
 *       wires them in Chunk C).</li>
 *   <li>Money safety: no {@code double} / {@code float} / wrappers as fields or method return types
 *       in {@code domain} or {@code application} (NFR-030; ADR-0001 D-2).</li>
 *   <li>Controllers (when they land in M4) reside in {@code api.controller}.</li>
 * </ul>
 *
 * <p>Rules whose target packages don't exist yet (controllers) are vacuously
 * satisfied and activate as those packages land in later chunks.
 */
class ArchitectureTests {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.purchaseconversion");
    }

    @Test
    @DisplayName("domain is framework-free (no Spring or JPA imports)")
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..");
        rule.check(classes);
    }

    @Test
    @DisplayName("domain only depends on itself, JDK, and the approved uuid-creator library")
    void domainOnlyDependsOnJdkAndDomainAndUuidCreator() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.github.f4b6a3.uuid..");
        rule.check(classes);
    }

    @Test
    @DisplayName("application only depends on itself, domain, and JDK")
    void applicationOnlyDependsOnDomainAndJdk() {
        // Activated in Chunk A2 — the application package now exists. Any application
        // class depending on Spring, JPA, infrastructure adapters, or any non-domain
        // non-JDK package will fail this rule.
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("..application..", "..domain..", "java..");
        rule.check(classes);
    }

    @Test
    @DisplayName("no Spring stereotypes in application (Chunk A2; ports/services stay POJOs)")
    void noSpringStereotypesInApplication() {
        // The application layer must remain framework-free until config (Chunk C) wires
        // it via constructor injection in a @Configuration class outside this package.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
                .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.ControllerAdvice")
                .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                .orShould().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
        rule.check(classes);
    }

    @Test
    @DisplayName("no double or float fields in domain or application (NFR-030; ADR-0001 D-2)")
    void noDoubleOrFloatFieldsInDomainOrApplication() {
        ArchRule rule = noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..")
                .should().haveRawType(double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(Float.class)
                .as("money/rate fields must be BigDecimal — never double, float, Double, or Float");
        rule.check(classes);
    }

    @Test
    @DisplayName("no double or float method return types in domain or application")
    void noDoubleOrFloatMethodReturnTypesInDomainOrApplication() {
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..")
                .should().haveRawReturnType(double.class)
                .orShould().haveRawReturnType(float.class)
                .orShould().haveRawReturnType(Double.class)
                .orShould().haveRawReturnType(Float.class)
                .as("method return types in domain/application must not be double/float");
        rule.check(classes);
    }

    @Test
    @DisplayName("controllers (when they land in M4) reside in api.controller")
    void controllersResideInApiControllerPackage() {
        // Vacuous in M1 (no Controller-suffix classes yet). Activates at M4.
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..api.controller..");
        rule.check(classes);
    }
}
