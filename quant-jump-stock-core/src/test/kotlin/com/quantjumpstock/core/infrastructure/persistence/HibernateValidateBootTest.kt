package com.quantjumpstock.core.infrastructure.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * `application-prod.yml` 의 `spring.jpa.hibernate.ddl-auto=validate` 가 prod 부팅 시
 * entity-table 매핑 mismatch 를 catch 하는지 회귀 보호.
 *
 * Local DB schema (V60 까지 적용) 와 prod schema 가 동일하다고 가정, 통합 테스트
 * 환경에서 `ddl-auto=validate` 부팅 시도 → 부팅 성공 = 모든 JPA entity 가 현 schema 와
 * 일치 = prod 부팅도 안전.
 *
 * 본 테스트가 fail 하면:
 *  - 어떤 entity 의 @Column 이 DB 컬럼 이름/타입과 mismatch
 *  - 또는 entity 가 매핑하는 테이블이 schema 에 없음
 *  - 로그에 정확한 mismatch 위치 출력 → 머지 전 entity 또는 schema 보정 필요
 */
@SpringBootTest(properties = [
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
])
class HibernateValidateBootTest {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `Spring Boot 가 ddl-auto=validate 모드로 부팅 성공 (entity-table 매핑 일치)`() {
        // SpringBootTest 컨텍스트 생성 자체가 부팅 시도. context 주입 성공 = entity-table validate 통과.
        assertThat(context).isNotNull
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0)
    }
}
