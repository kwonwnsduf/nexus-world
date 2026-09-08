package com.nexusworld;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {
    private final JavaClasses classesUnderTest = new ClassFileImporter()
            .importPackages("com.nexusworld");

    @Test
    void restControllersBelongToApiPackages() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..api..")
                .allowEmptyShould(true)
                .check(classesUnderTest);
    }

    @Test
    void apiDoesNotDependOnInfrastructureAdapters() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .check(classesUnderTest);
    }

    @Test
    void applicationDoesNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..")
                .allowEmptyShould(true)
                .check(classesUnderTest);
    }
}
