package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import team.startup.expo.domain.auth.model.IssuedParticipantSession
import team.startup.expo.domain.auth.presentation.dto.request.VerifyParticipantCodeReqDto
import team.startup.expo.domain.auth.service.ParticipantSessionService
import team.startup.expo.domain.auth.service.PhoneVerificationRecorder
import team.startup.expo.domain.auth.service.impl.VerifyParticipantCodeServiceImpl
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.impl.GetParticipantStatsServiceImpl
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.notification.NotificationAuthSender
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.InternalProperties
import team.startup.expo.global.security.ParticipantSessionProperties

class ParticipantStatsAndVerificationTests :
    BehaviorSpec({
        given("박람회별 참가자 집계를 조회할 때") {
            val jdbcTemplate = mockk<JdbcTemplate>()
            val standardParticipantRepository = mockk<StandardParticipantRepository>()
            val service = GetParticipantStatsServiceImpl(jdbcTemplate, standardParticipantRepository)

            `when`("인증한 번호와 등록한 참가자가 있으면") {
                every {
                    jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tb_participant_phone_verification WHERE expo_id = ?",
                        Long::class.java,
                        EXPO,
                    )
                } returns 19L
                every { standardParticipantRepository.countByExpoId(EXPO) } returns 34L

                val result = service.execute(EXPO)

                then("두 값을 각각 돌려준다") {
                    result.expoId shouldBe EXPO
                    result.verifiedPhoneCount shouldBe 19L
                    result.registeredParticipantCount shouldBe 34L
                }
            }
            `when`("인증 기록이 비어 있어 건수가 null로 오면") {
                every {
                    jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tb_participant_phone_verification WHERE expo_id = ?",
                        Long::class.java,
                        EXPO,
                    )
                } returns null
                every { standardParticipantRepository.countByExpoId(EXPO) } returns 0L

                then("0건이다") {
                    service.execute(EXPO).verifiedPhoneCount shouldBe 0L
                }
            }
        }

        given("전화 인증 기록을 저장할 때") {
            val jdbcTemplate = mockk<JdbcTemplate>()
            val recorder =
                PhoneVerificationRecorder(
                    jdbcTemplate,
                    ParticipantSessionProperties(),
                    InternalProperties(token = "test-internal-token-0123456789abcdef"),
                )

            `when`("저장에 성공하면") {
                clearMocks(jdbcTemplate)
                every { jdbcTemplate.update(any<String>(), any<String>(), any<String>()) } returns 1

                recorder.record(EXPO, "010-1234-5678")

                then("전화번호 원문이 아니라 64자 해시를 넘긴다") {
                    verify(exactly = 1) {
                        jdbcTemplate.update(
                            match<String> { it.contains("ON CONFLICT (expo_id, phone_hash) DO NOTHING") },
                            EXPO,
                            match<String> { it.length == 64 && !it.contains("01012345678") },
                        )
                    }
                }
            }
            `when`("저장에 실패하면") {
                clearMocks(jdbcTemplate)
                every { jdbcTemplate.update(any<String>(), any<String>(), any<String>()) } throws IllegalStateException("db down")

                then("예외를 밖으로 던지지 않아 인증을 막지 않는다") {
                    recorder.record(EXPO, "01012345678")
                }
            }
        }

        given("참가자 인증번호를 검증할 때") {
            val expoPeriodReader = mockk<ExpoPeriodReader>()
            val notificationAuthSender = mockk<NotificationAuthSender>()
            val participantSessionService = mockk<ParticipantSessionService>()
            val recorder = mockk<PhoneVerificationRecorder>()
            val service = VerifyParticipantCodeServiceImpl(expoPeriodReader, notificationAuthSender, participantSessionService, recorder)
            val request = VerifyParticipantCodeReqDto(EXPO, "01012345678", "1234")
            val session = mockk<IssuedParticipantSession>()

            fun reset() {
                clearMocks(expoPeriodReader, notificationAuthSender, participantSessionService, recorder)
                every { expoPeriodReader.find(EXPO) } returns mockk()
                every { notificationAuthSender.verifyCode(any(), any()) } just runs
                every { recorder.record(any(), any()) } just runs
            }

            `when`("인증이 성공해 세션이 만들어지면") {
                reset()
                every { participantSessionService.issueIfCodeUnused(EXPO, "01012345678", "1234") } returns session

                val result = service.execute(request)

                then("세션을 돌려주고 세션을 만든 뒤에 번호를 기록한다") {
                    result shouldBe session
                    verifyOrder {
                        participantSessionService.issueIfCodeUnused(EXPO, "01012345678", "1234")
                        recorder.record(EXPO, "01012345678")
                    }
                }
            }
            `when`("이미 쓴 인증번호라 세션이 만들어지지 않으면") {
                reset()
                every { participantSessionService.issueIfCodeUnused(EXPO, "01012345678", "1234") } returns null

                then("409이고 번호를 기록하지 않는다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.status shouldBe HttpStatus.CONFLICT
                    verify(exactly = 0) { recorder.record(any(), any()) }
                }
            }
            `when`("Notification 인증이 실패하면") {
                reset()
                every { notificationAuthSender.verifyCode(any(), any()) } throws
                    ExpectedException(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다.")

                then("세션도 기록도 만들지 않는다") {
                    shouldThrow<ExpectedException> { service.execute(request) }.status shouldBe HttpStatus.BAD_REQUEST
                    verify(exactly = 0) { participantSessionService.issueIfCodeUnused(any(), any(), any()) }
                    verify(exactly = 0) { recorder.record(any(), any()) }
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000f01"
    }
}
