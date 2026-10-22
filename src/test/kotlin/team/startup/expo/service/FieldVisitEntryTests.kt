package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantParticipation
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.request.RecordEntryReqDto
import team.startup.expo.domain.participation.repository.FieldVisitProjection
import team.startup.expo.domain.participation.repository.FieldVisitRepository
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.impl.GetEntryStatusServiceImpl
import team.startup.expo.domain.participation.service.impl.RecordEntryServiceImpl
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class FieldVisitEntryTests :
    BehaviorSpec({
        class Fixture {
            val participants = mockk<StandardParticipantRepository>()
            val entries = mockk<StandardParticipantParticipationRepository>()
            val visits = mockk<FieldVisitRepository>()
            val participant =
                StandardParticipant(
                    id = 1,
                    expoId = EXPO,
                    name = "사전등록자",
                    phoneNumber = "01012345678",
                    personalInformationStatus = true,
                    applicationType = ApplicationType.PRE,
                    applicationDate = LocalDateTime.now(),
                )
            val service = RecordEntryServiceImpl(participants, entries, mockk(), mockk(), visits)
            val status = GetEntryStatusServiceImpl(participants, entries)
            val saved = slot<StandardParticipantParticipation>()

            init {
                every { participants.findByIdAndExpoId(1, EXPO) } returns participant
                every { participants.existsByIdAndExpoId(1, EXPO) } returns true
                every { entries.saveAndFlush(capture(saved)) } answers { firstArg() }
                every { visits.requiresProjection(1) } returns false
            }

            fun request(
                date: LocalDate = DATE,
                registration: UUID? = null,
                generation: Long? = null,
            ) = RecordEntryReqDto(
                EXPO,
                ParticipationType.STANDARD,
                participantId = 1,
                code = participant.code,
                attendanceDate = date,
                fieldVisitRegistrationId = registration,
                fieldVisitGeneration = generation,
            )
        }

        given("Attendance가 날짜별 권위를 검증한 FIELD 입장") {
            `when`("PRE 참가자가 서로 다른 날 방문하면") {
                then("지정 날짜로 각각 저장하고 프로필과 QR을 유지한다") {
                    val f = Fixture()
                    val code = f.participant.code
                    val id = UUID.randomUUID()
                    every { f.visits.projection(id) } returns FieldVisitProjection(EXPO, 1, DATE, 2, FieldVisitStatus.ACTIVE)
                    f.service.execute(f.request(registration = id, generation = 2))
                    f.saved.captured.attendanceDate shouldBe DATE
                    every { f.visits.projection(id) } returns FieldVisitProjection(EXPO, 1, DATE.plusDays(1), 3, FieldVisitStatus.ACTIVE)
                    f.service.execute(f.request(DATE.plusDays(1), id, 3))
                    f.saved.captured.attendanceDate shouldBe DATE.plusDays(1)
                    f.participant.applicationType shouldBe ApplicationType.PRE
                    f.participant.code shouldBe code
                    verify(exactly = 0) { f.entries.existsByStandardParticipantId(any()) }
                }
            }
            `when`("projection이 없거나 취소되거나 날짜 또는 세대가 다르면") {
                then("과거 FIELD와 생성일을 추정하지 않고 입장 저장을 거절한다") {
                    val f = Fixture()
                    val id = UUID.randomUUID()
                    listOf(
                        null,
                        FieldVisitProjection(EXPO, 1, DATE, 2, FieldVisitStatus.CANCELLED),
                        FieldVisitProjection(EXPO, 1, DATE.plusDays(1), 2, FieldVisitStatus.ACTIVE),
                        FieldVisitProjection(EXPO, 1, DATE, 3, FieldVisitStatus.ACTIVE),
                    ).forEach { projection ->
                        every { f.visits.projection(id) } returns projection
                        shouldThrow<ExpectedException> { f.service.execute(f.request(registration = id, generation = 2)) }.status shouldBe
                            HttpStatus.CONFLICT
                    }
                    verify(exactly = 0) { f.entries.saveAndFlush(any<StandardParticipantParticipation>()) }
                }
            }
            `when`("FIELD 필드가 일부만 오면") {
                then("400으로 거절한다") {
                    val f = Fixture()
                    shouldThrow<ExpectedException> { f.service.execute(f.request(registration = UUID.randomUUID())) }.status shouldBe
                        HttpStatus.BAD_REQUEST
                }
            }
        }

        given("기존 PRE 회차 입장 계약") {
            `when`("신규 pending 또는 방문일 미상 FIELD가 권위 필드를 생략하면") {
                then("ownership이나 생성일을 입장권한으로 추정하지 않는다") {
                    val pending = Fixture()
                    every { pending.visits.requiresProjection(1) } returns true
                    shouldThrow<ExpectedException> { pending.service.execute(pending.request()) }.status shouldBe HttpStatus.CONFLICT
                    shouldThrow<ExpectedException> {
                        pending.service.execute(pending.request().copy(attendanceDate = null))
                    }.status shouldBe HttpStatus.CONFLICT
                    val legacy = Fixture()
                    legacy.participant.applicationType = ApplicationType.FIELD
                    shouldThrow<ExpectedException> { legacy.service.execute(legacy.request()) }.status shouldBe HttpStatus.CONFLICT
                    verify(exactly = 0) { pending.entries.saveAndFlush(any<StandardParticipantParticipation>()) }
                    verify(exactly = 0) { legacy.entries.saveAndFlush(any<StandardParticipantParticipation>()) }
                }
            }
            `when`("FIELD 필드 없이 호출하면") {
                then("기존 회차와 지정 날짜를 보존하고 projection을 조회하지 않는다") {
                    val f = Fixture()
                    f.service.execute(f.request().copy(preregisterSessionId = 25))
                    f.saved.captured.preregisterSessionId shouldBe 25L
                    f.saved.captured.attendanceDate shouldBe DATE
                    verify(exactly = 0) { f.visits.projection(any()) }
                    f.participant.applicationType = ApplicationType.FIELD
                    every { f.visits.requiresProjection(1) } returns true
                    f.service.execute(f.request().copy(preregisterSessionId = 26))
                    f.saved.captured.preregisterSessionId shouldBe 26L
                    f.participant.applicationType shouldBe ApplicationType.FIELD
                }
            }
        }

        given("첫입장 저장 응답이 유실된 뒤 날짜별 상태 조회") {
            `when`("다른 날의 입장 기록만 있으면") {
                then("정확한 날짜를 echo하며 해당 날짜의 저장 여부만 답한다") {
                    val f = Fixture()
                    every { f.entries.existsByStandardParticipantIdAndAttendanceDate(1, DATE) } returns false
                    every { f.entries.existsByStandardParticipantIdAndAttendanceDate(1, DATE.plusDays(1)) } returns true
                    f.status.execute(GetEntryStatusReqDto(EXPO, 1, DATE)).let {
                        it.attendanceDate shouldBe DATE
                        it.entered shouldBe false
                    }
                    f.status.execute(GetEntryStatusReqDto(EXPO, 1, DATE.plusDays(1))).entered shouldBe true
                    verify(exactly = 0) { f.entries.existsByStandardParticipantId(any()) }
                }
            }
            `when`("다른 행사 또는 없는 참가자를 조회하면") {
                then("404로 닫고 날짜별 조회도 하지 않는다") {
                    val f = Fixture()
                    every { f.participants.existsByIdAndExpoId(99, EXPO) } returns false
                    shouldThrow<ExpectedException> { f.status.execute(GetEntryStatusReqDto(EXPO, 99, DATE)) }.status shouldBe
                        HttpStatus.NOT_FOUND
                    verify(exactly = 0) { f.entries.existsByStandardParticipantIdAndAttendanceDate(any(), any()) }
                }
            }
        }
    }) {
    companion object {
        const val EXPO = "expo-202"
        val DATE: LocalDate = LocalDate.of(2026, 10, 20)
    }
}
