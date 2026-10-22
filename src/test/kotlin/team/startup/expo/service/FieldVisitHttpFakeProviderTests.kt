package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import team.startup.expo.domain.participation.presentation.InternalFieldVisitController
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryStatusResDto
import team.startup.expo.domain.participation.presentation.dto.response.FieldVisitParticipantResDto
import team.startup.expo.domain.participation.service.FieldVisitService
import team.startup.expo.domain.participation.service.GetEntryStatusService
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate
import java.util.UUID

class FieldVisitHttpFakeProviderTests :
    BehaviorSpec({
        val visits = mockk<FieldVisitService>()
        val entries = mockk<GetEntryStatusService>()
        val json = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        val mvc =
            MockMvcBuilders
                .standaloneSetup(InternalFieldVisitController(visits, entries))
                .setMessageConverters(JacksonJsonHttpMessageConverter(json))
                .build()
        val date = LocalDate.of(2026, 10, 22)

        given("DB와 실제 SMS 없이 제공하는 User HTTP 계약") {
            `when`("신규 참가자 생성 응답을 받으면") {
                then("200 배열에 clientReference와 participantId만 정확히 대응한다") {
                    every { visits.create("expo", any()) } returns
                        listOf(
                            FieldVisitParticipantResDto(10, "client-b"),
                            FieldVisitParticipantResDto(12, "client-a"),
                        )
                    mvc
                        .perform(
                            post("/internal/expos/expo/field-visit-participants")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"phoneNumber":"01012345678","requestId":"key","personalInformationStatus":true,
                                     "formId":"field-form","questions":[{"id":"q1","title":"제출 당시"}],
                                     "participants":[{"clientReference":"client-b","name":"신규","informationJson":"{\"q1\":\"개별 답변\"}"},
                                                     {"clientReference":"client-a","name":"동행"}]}
                                    """.trimIndent(),
                                ),
                        ).andExpect(status().isOk)
                        .andExpect(
                            content().json(
                                """
                                [{"participantId":10,"clientReference":"client-b"},
                                 {"participantId":12,"clientReference":"client-a"}]
                                """.trimIndent(),
                            ),
                        )
                    verify {
                        visits.create(
                            "expo",
                            match {
                                it.participants.map { person -> person.clientReference } == listOf("client-b", "client-a") &&
                                    it.formId == "field-form" && it.questions
                                        ?.get(0)
                                        ?.path("title")
                                        ?.asString() == "제출 당시" &&
                                    it.participants[0].informationJson == """{"q1":"개별 답변"}""" &&
                                    it.participants[1].informationJson == "{}"
                            },
                        )
                    }
                }
            }
            `when`("projection을 전송하면") {
                then("UUID 날짜 세대 상태를 바인딩하고 204 빈 본문을 반환한다") {
                    val registration = UUID.randomUUID()
                    val operation = UUID.randomUUID()
                    val body = ProjectFieldVisitReqDto(date, 5, FieldVisitStatus.CANCELLED, operation)
                    every { visits.project("expo", 10, registration, body) } returns Unit
                    mvc
                        .perform(
                            put("/internal/expos/expo/participants/10/field-visits/$registration")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)),
                        ).andExpect(status().isNoContent)
                        .andExpect(content().string(""))
                    verify(exactly = 1) { visits.project("expo", 10, registration, body) }
                }
            }
            `when`("날짜별 entry-status를 조회하면") {
                then("같은 날짜를 echo하는 응답을 반환한다") {
                    val body = GetEntryStatusReqDto("expo", 10, date)
                    every { entries.execute(body) } returns EntryStatusResDto(10, date, true)
                    mvc
                        .perform(
                            post("/internal/standard-participants/entry-status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)),
                        ).andExpect(status().isOk)
                        .andExpect(jsonPath("$.attendanceDate").value("2026-10-22"))
                        .andExpect(jsonPath("$.entered").value(true))
                        .andExpect(jsonPath("$.participantId").value(10))
                }
            }
            `when`("0 generation을 보내면") {
                then("서비스 실행 전에 400으로 거절한다") {
                    val id = UUID.randomUUID()
                    val body = ProjectFieldVisitReqDto(date, 0, FieldVisitStatus.ACTIVE, UUID.randomUUID())
                    mvc
                        .perform(
                            put("/internal/expos/expo/participants/10/field-visits/$id")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)),
                        ).andExpect(status().isBadRequest)
                    verify(exactly = 0) { visits.project("expo", 10, id, any()) }
                }
            }
        }
    })
