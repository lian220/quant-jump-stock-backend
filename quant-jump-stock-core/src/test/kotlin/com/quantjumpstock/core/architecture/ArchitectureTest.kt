package com.quantjumpstock.core.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

@AnalyzeClasses(packages = ["com.quantjumpstock.core"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    /**
     * Layer Check
     */
    @ArchTest
    val layeredArchitecture: ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.quantjumpstock.core..")
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Adapter").definedBy("..adapter..")
        .layer("Config").definedBy("..config..")
        
        // Domain is the core, depends on NOTHING (except pure java/kotlin)
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config")
        
        // Application depends on Domain, used by Adapter
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
        .whereLayer("Application").mayOnlyAccessLayers("Domain")

    /**
     * Critical Rule: Domain should NEVER depend on Adapter (Infrastructure)
     */
    @ArchTest
    val domainShouldNotDependOnAdapter: ArchRule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..adapter..")
        .because("The Domain layer must be independent of external adapters (Hexagonal Architecture Base Rule)")

    /**
     * Critical Rule: Application should NOT depend on JPA/MongoDB details
     */
    @ArchTest
    val applicationShouldNotDependOnPersistence: ArchRule = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..persistence..")
        .because("The Application layer must use Ports, not concrete Repositories or Entities")
}
