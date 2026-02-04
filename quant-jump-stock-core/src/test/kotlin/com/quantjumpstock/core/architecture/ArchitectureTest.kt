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
     * Layer Check (Hexagonal Architecture)
     *
     * - Domain: 순수 비즈니스 로직, 외부 의존성 없음
     * - Application: 유스케이스, Domain과 cross-cutting concerns에만 의존
     * - Adapter: 외부 시스템 연동 (REST, JPA, Kafka 등)
     * - Config: 설정 클래스 (cross-cutting)
     * - Infrastructure: 인프라 유틸리티 (cross-cutting)
     * - Scheduler: Quartz 스케줄러
     *
     * Note: Adapter 내부의 input ↔ output 의존성은 허용됩니다.
     */
    @ArchTest
    val layeredArchitecture: ArchRule = layeredArchitecture()
        .consideringOnlyDependenciesInAnyPackage("com.quantjumpstock.core..")
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Adapter").definedBy("..adapter..")
        .layer("Config").definedBy("..config..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .layer("Scheduler").definedBy("..scheduler..")

        // Domain is the core, depends on NOTHING (except pure java/kotlin)
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config", "Infrastructure", "Scheduler")

        // Application depends on Domain + cross-cutting concerns (Config, Infrastructure)
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config", "Scheduler")
        .whereLayer("Application").mayOnlyAccessLayers("Domain", "Config", "Infrastructure")

        // Adapter 내부 의존성 무시 (input adapter ↔ output adapter 허용)
        .ignoreDependency("..adapter..", "..adapter..")

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
     * This is the most important rule - Application uses Ports, not concrete implementations
     */
    @ArchTest
    val applicationShouldNotDependOnPersistence: ArchRule = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..persistence..")
        .because("The Application layer must use Ports, not concrete Repositories or Entities")
}
