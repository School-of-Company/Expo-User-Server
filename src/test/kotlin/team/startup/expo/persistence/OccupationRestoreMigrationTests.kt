package team.startup.expo.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import team.startup.expo.support.IntegrationTestSupport
import java.nio.charset.StandardCharsets

/** `V13`은 `V10`이 합친 직업 값을, 신청 답변에 원래 키가 남아 있으면 복원하고 아니면 유효한 값으로 옮긴다. */
class OccupationRestoreMigrationTests : IntegrationTestSupport() {
    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tb_standard_participant RESTART IDENTITY CASCADE")
    }

    @Test
    fun `답변에 원래 키가 남아 있으면 복원한다`() {
        insert("01011110001", "MIDDLE_HIGH_SCHOOL_STUDENT", """{"직업":"MIDDLE_SCHOOL_STUDENT"}""")
        insert("01011110002", "MIDDLE_HIGH_SCHOOL_STUDENT", """{"직업":"HIGH_SCHOOL_STUDENT"}""")
        insert("01011110003", "GENERAL", """{"직업":"SCHOOL_STAFF"}""")
        insert("01011110004", "GENERAL", """{"직업":"PARENT"}""")

        jdbcTemplate.execute(migrationSql())

        occupations() shouldBe listOf("MIDDLE_SCHOOL_STUDENT", "HIGH_SCHOOL_STUDENT", "SCHOOL_STAFF", "PARENT")
    }

    @Test
    fun `근거가 없으면 중고는 중학생으로 옮기고 일반은 그대로 둔다`() {
        insert("01011110001", "MIDDLE_HIGH_SCHOOL_STUDENT", """{"직업":"중고등학생"}""")
        insert("01011110002", "MIDDLE_HIGH_SCHOOL_STUDENT", null)
        insert("01011110003", "GENERAL", """{"직업":"일반"}""")
        insert("01011110004", "GENERAL", null)

        jdbcTemplate.execute(migrationSql())

        occupations() shouldBe listOf("MIDDLE_SCHOOL_STUDENT", "MIDDLE_SCHOOL_STUDENT", "GENERAL", "GENERAL")
    }

    @Test
    fun `이미 유효한 값과 비어 있는 값은 건드리지 않는다`() {
        val untouched = listOf("KINDERGARTEN_STUDENT", "ELEMENTARY_STUDENT", "TEACHER", "PRE_SERVICE_TEACHER")
        untouched.forEachIndexed { index, occupation -> insert("0101111000$index", occupation, """{"직업":"PARENT"}""") }
        insert("01022220000", null, """{"직업":"SCHOOL_STAFF"}""")

        jdbcTemplate.execute(migrationSql())

        occupations() shouldBe untouched + listOf(null)
    }

    @Test
    fun `여러 번 실행해도 결과가 같다`() {
        insert("01011110001", "MIDDLE_HIGH_SCHOOL_STUDENT", """{"직업":"HIGH_SCHOOL_STUDENT"}""")
        insert("01011110002", "GENERAL", """{"직업":"PARENT"}""")

        repeat(2) { jdbcTemplate.execute(migrationSql()) }

        occupations() shouldBe listOf("HIGH_SCHOOL_STUDENT", "PARENT")
    }

    private fun migrationSql(): String =
        ClassPathResource("db/migration/V13__restore_occupation_types.sql")
            .inputStream
            .readAllBytes()
            .toString(StandardCharsets.UTF_8)

    private fun insert(
        phone: String,
        occupation: String?,
        informationJson: String?,
    ) {
        jdbcTemplate.update(
            "INSERT INTO tb_standard_participant (expo_id, name, phone_number, personal_information_status, application_type, " +
                "application_date, occupation, information_json, code) VALUES ('0199aaaa-0000-7000-8000-0000000000a1', '참가자', ?, true, " +
                "'PRE', now(), ?, ?::jsonb, left(replace(gen_random_uuid()::text, '-', ''), 22))",
            phone,
            occupation,
            informationJson,
        )
    }

    private fun occupations(): List<String?> =
        jdbcTemplate.queryForList("SELECT occupation FROM tb_standard_participant ORDER BY id", String::class.java)
}
