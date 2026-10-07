package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.participation.entity.Occupation

class OccupationTests {
    @Test
    fun `직업은 사전등록 공식 6개 유형이고 Form의 Occupation과 같은 이름이다`() {
        // Form 서비스의 Occupation과 이름이 하나라도 다르면 신청 요청이 400이 된다. 계약 이름을 일부러 고정해 둔다
        Occupation.entries.map { it.name } shouldBe
            listOf(
                "KINDERGARTEN_STUDENT",
                "ELEMENTARY_STUDENT",
                "MIDDLE_HIGH_SCHOOL_STUDENT",
                "GENERAL",
                "TEACHER",
                "PRE_SERVICE_TEACHER",
            )
    }

    @Test
    fun `컬럼 길이 안에 들어간다`() {
        // occupation 컬럼은 VARCHAR(30)이다
        Occupation.entries.all { it.name.length <= 30 } shouldBe true
    }
}
