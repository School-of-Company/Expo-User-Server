package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.participation.entity.Region
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.training.entity.ApplicationType
import tools.jackson.databind.json.JsonMapper

/** 멱등 fingerprint는 요청 본문의 직렬화로 만든다. `region`이 생기기 전과 같은 요청이 같은 직렬화를 내는지 확인한다. */
class CreateStandardParticipantReqDtoSerializationTests {
    private val mapper = JsonMapper.builder().build()

    private fun dto(region: Region?) =
        CreateStandardParticipantReqDto(
            expoId = "e",
            name = "홍길동",
            phoneNumber = "01012345678",
            informationJson = "{}",
            personalInformationStatus = true,
            applicationType = ApplicationType.PRE,
            region = region,
        )

    @Test
    fun `region이 없으면 직렬화에 싣지 않아 기존 요청과 같다`() {
        mapper.writeValueAsString(dto(null)) shouldBe
            """{"expoId":"e","name":"홍길동","phoneNumber":"01012345678","informationJson":"{}","personalInformationStatus":true,""" +
            """"applicationType":"PRE","occupation":null,"school":null,"formId":null,"questions":null,"requestId":null}"""
    }

    @Test
    fun `region이 있으면 직렬화에 싣는다`() {
        mapper.writeValueAsString(dto(Region.GWANGJU)).contains(""""region":"GWANGJU"""") shouldBe true
    }
}
