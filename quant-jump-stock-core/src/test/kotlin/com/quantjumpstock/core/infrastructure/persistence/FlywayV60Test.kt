package com.quantjumpstock.core.infrastructure.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(properties = ["spring.flyway.enabled=true"])
class FlywayV60Test {
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `app_secret_encrypted_v2 컬럼이 존재한다`() {
        val cols = jdbcTemplate.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema='public'
              AND table_name='user_kis_accounts'
              AND column_name='app_secret_encrypted_v2'
            """.trimIndent(),
            String::class.java
        )
        assertThat(cols)
            .`as`("user_kis_accounts 테이블에 app_secret_encrypted_v2 컬럼이 정확히 1개 존재해야 함")
            .containsExactly("app_secret_encrypted_v2")
    }
}
