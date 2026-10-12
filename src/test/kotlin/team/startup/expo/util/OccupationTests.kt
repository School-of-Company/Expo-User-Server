package team.startup.expo.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.startup.expo.domain.participation.entity.Occupation

class OccupationTests :
    BehaviorSpec({
        given("Form 서비스와 맞춘 직업 값") {
            `when`("직업 이름을 모두 모으면") {
                then("9개이고 Form의 Occupation과 같은 이름이다") {
                    // Form 서비스의 Occupation과 이름이 하나라도 다르면 신청 요청이 400이 된다. 계약 이름을 일부러 고정해 둔다
                    Occupation.entries.map { it.name } shouldBe
                        listOf(
                            "KINDERGARTEN_STUDENT",
                            "ELEMENTARY_STUDENT",
                            "MIDDLE_SCHOOL_STUDENT",
                            "HIGH_SCHOOL_STUDENT",
                            "SCHOOL_STAFF",
                            "PARENT",
                            "GENERAL",
                            "TEACHER",
                            "PRE_SERVICE_TEACHER",
                        )
                }
            }
            `when`("컬럼 길이와 비교하면") {
                then("occupation 컬럼(VARCHAR(30)) 안에 들어간다") {
                    Occupation.entries.all { it.name.length <= 30 } shouldBe true
                }
            }
        }
    })
