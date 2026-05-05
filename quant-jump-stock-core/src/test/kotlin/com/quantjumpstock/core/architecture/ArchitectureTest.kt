package com.quantjumpstock.core.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

/**
 * Spring Boot AOT 가 생성하는 `__TestContext..._BeanDefinitions` /
 * `__TestContext..._BeanFactoryRegistrations` 클래스들을 ArchUnit 분석에서 제외한다.
 *
 * 이 클래스들은 build/classes/java/aotTest/... 경로에 자동 생성되어 모든 Bean 을 직접
 * 호출하므로, 첫 번째 컨텍스트가 생성되는 패키지(테스트 클래스의 패키지)가 어디냐에 따라
 * Layered Architecture 위반이 무작위로 발생함. 운영 코드 룰 검증과는 무관한 노이즈이므로
 * 일괄 제외.
 */
private class DoNotIncludeAotGeneratedTestSources : ImportOption {
    override fun includes(location: Location): Boolean {
        // /aotTest/ — Spring Boot AOT 테스트 출력 디렉터리.
        return !location.contains("/aotTest/")
    }
}

@AnalyzeClasses(
    packages = ["com.quantjumpstock.core"],
    importOptions = [
        ImportOption.DoNotIncludeTests::class,
        DoNotIncludeAotGeneratedTestSources::class
    ]
)
class ArchitectureTest {

    /**
     * Layer Check (Hexagonal Architecture)
     *
     * - Domain: 순수 비즈니스 로직, 외부 의존성 없음
     * - Application: 유스케이스, Domain과 cross-cutting concerns에만 의존
     * - Adapter: 외부 시스템 연동 (REST, JPA, Kafka 등)
     * - Config: 설정 클래스 (cross-cutting)
     * - Infrastructure: 인프라 유틸리티 (cross-cutting)
     * - Scheduler: Cloud Scheduler HTTP 엔드포인트
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
