package team.startup.expo.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import team.startup.expo.support.IntegrationTestSupport
import java.nio.charset.StandardCharsets

class OccupationMigrationTests : IntegrationTestSupport() {
    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @Test
    fun `옛 직업 값은 공식 유형으로 옮기고 나머지는 그대로 둔다`() {
        val legacy =
            listOf(
                "ELEMENTARY_STUDENT",
                "MIDDLE_SCHOOL_STUDENT",
                "HIGH_SCHOOL_STUDENT",
                "SCHOOL_STAFF",
                "PRE_SERVICE_TEACHER",
                "PARENT",
                "GENERAL",
                "TEACHER",
            )
        legacy.forEachIndexed { index, occupation -> insert("0101111000$index", occupation) }
        insert("01022220000", null)

        jdbcTemplate.execute(migrationSql())

        occupations() shouldBe
            listOf(
                "ELEMENTARY_STUDENT",
                "MIDDLE_HIGH_SCHOOL_STUDENT",
                "MIDDLE_HIGH_SCHOOL_STUDENT",
                "GENERAL",
                "PRE_SERVICE_TEACHER",
                "GENERAL",
                "GENERAL",
                "TEACHER",
                null,
            )
    }

    @Test
    fun `여러 번 실행해도 결과가 같다`() {
        insert("01011110000", "SCHOOL_STAFF")

        repeat(2) { jdbcTemplate.execute(migrationSql()) }

        occupations() shouldBe listOf("GENERAL")
    }

    private fun migrationSql(): String =
        ClassPathResource("db/migration/V10__align_occupation_with_official_types.sql")
            .inputStream
            .readAllBytes()
            .toString(StandardCharsets.UTF_8)

    private fun insert(
        phone: String,
        occupation: String?,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant (expo_id, name, phone_number, personal_information_status, application_type, " +
                "application_date, occupation, code) VALUES ('0199aaaa-0000-7000-8000-0000000000a1', '참가자', ?, true, 'PRE', now(), ?, " +
                "left(replace(gen_random_uuid()::text, '-', ''), 22))",
            phone,
            occupation,
        )
    }

    private fun occupations(): List<String?> =
        jdbcTemplate.queryForList("SELECT occupation FROM tb_standard_participant ORDER BY id", String::class.java)
}
