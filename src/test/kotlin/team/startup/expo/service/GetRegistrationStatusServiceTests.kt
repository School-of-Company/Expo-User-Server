package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.RegistrationCancellation
import team.startup.expo.domain.participation.entity.RegistrationRequest
import team.startup.expo.domain.participation.presentation.dto.request.GetRegistrationStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.response.RegistrationStatus
import team.startup.expo.domain.participation.repository.RegistrationCancellationRepository
import team.startup.expo.domain.participation.repository.RegistrationRequestRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.participation.service.RegistrationIdempotency
import team.startup.expo.domain.participation.service.impl.GetRegistrationStatusServiceImpl
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.json.JsonMapper
import java.util.Optional

class GetRegistrationStatusServiceTests :
    BehaviorSpec({
        class Fixture {
            val requests = mockk<RegistrationRequestRepository>()
            val cancellations =
                mockk<RegistrationCancellationRepository> {
                    every { save(any()) } answers { firstArg() }
                }
            val guard = mockk<ExpoDeletionGuard>(relaxed = true)
            val lock = mockk<ParticipantRegistrationLock>(relaxed = true)
            val service = GetRegistrationStatusServiceImpl(requests, cancellations, guard, lock)
        }

        val request = GetRegistrationStatusReqDto(EXPO, REQUEST)

        fun saved(expoId: String = EXPO) = RegistrationRequest(REQUEST, ParticipationType.STANDARD, expoId, "fp", 7L, "01000000000", true)

        given("저장된 등록 기록이 있는 requestId") {
            val f = Fixture()
            every { f.requests.findById(REQUEST) } returns Optional.of(saved())
            `when`("조회하면") {
                val result = f.service.execute(request)
                then("SAVED와 대표자 ID만 답하고 취소 기록을 남기지 않으며 삭제 guard, 요청 키 lock 순서로 잡는다") {
                    result.status shouldBe RegistrationStatus.SAVED
                    result.participantId shouldBe 7L
                    verifyOrder {
                        f.guard.requireNotDeleted(EXPO)
                        f.lock.lockRequest(REQUEST)
                    }
                    verify(exactly = 0) { f.cancellations.save(any()) }
                }
            }
        }

        given("기록도 취소 기록도 없는 requestId") {
            val f = Fixture()
            every { f.requests.findById(REQUEST) } returns Optional.empty()
            every { f.cancellations.findById(REQUEST) } returns Optional.empty()
            `when`("조회하면") {
                val result = f.service.execute(request)
                then("NOT_SAVED를 답하고 취소 기록을 남긴다") {
                    result.status shouldBe RegistrationStatus.NOT_SAVED
                    result.participantId shouldBe null
                    verify(exactly = 1) { f.cancellations.save(match { it.requestId == REQUEST && it.expoId == EXPO }) }
                }
            }
        }

        given("이미 취소 기록이 있는 requestId") {
            val f = Fixture()
            every { f.requests.findById(REQUEST) } returns Optional.empty()
            every { f.cancellations.findById(REQUEST) } returns Optional.of(RegistrationCancellation(REQUEST, EXPO))
            `when`("다시 조회하면") {
                val result = f.service.execute(request)
                then("NOT_SAVED를 답하고 기록을 또 남기지 않는다") {
                    result.status shouldBe RegistrationStatus.NOT_SAVED
                    verify(exactly = 0) { f.cancellations.save(any()) }
                }
            }
        }

        given("저장 기록이 다른 박람회의 것인 requestId") {
            val f = Fixture()
            every { f.requests.findById(REQUEST) } returns Optional.of(saved("other"))
            `when`("조회하면") {
                then("409이다") {
                    shouldThrow<ExpectedException> { f.service.execute(request) }.status shouldBe HttpStatus.CONFLICT
                }
            }
        }

        given("취소 기록이 다른 박람회의 것인 requestId") {
            val f = Fixture()
            every { f.requests.findById(REQUEST) } returns Optional.empty()
            every { f.cancellations.findById(REQUEST) } returns Optional.of(RegistrationCancellation(REQUEST, "other"))
            `when`("조회하면") {
                then("409이고 새로 남기지 않는다") {
                    shouldThrow<ExpectedException> { f.service.execute(request) }.status shouldBe HttpStatus.CONFLICT
                    verify(exactly = 0) { f.cancellations.save(any()) }
                }
            }
        }

        given("삭제 중인 박람회") {
            val f = Fixture()
            every { f.guard.requireNotDeleted(EXPO) } throws ExpectedException(HttpStatus.CONFLICT, "삭제")
            `when`("조회하면") {
                then("409이고 lock도 잡지 않는다") {
                    shouldThrow<ExpectedException> { f.service.execute(request) }.status shouldBe HttpStatus.CONFLICT
                    verify(exactly = 0) { f.lock.lockRequest(any()) }
                }
            }
        }

        given("취소 기록이 있는 requestId로 등록 요청이 오면") {
            val f = Fixture()
            val idempotency = RegistrationIdempotency(f.requests, f.cancellations, f.guard, f.lock, JsonMapper.builder().build())
            every { f.requests.findById(REQUEST) } returns Optional.empty()
            every { f.cancellations.existsById(REQUEST) } returns true
            var ran = false
            `when`("등록을 실행하면") {
                then("409이고 저장 동작을 실행하지 않는다") {
                    shouldThrow<ExpectedException> {
                        idempotency.execute(REQUEST, ParticipationType.STANDARD, EXPO, mapOf("a" to 1)) {
                            ran = true
                            error("실행되면 안 된다")
                        }
                    }.status shouldBe HttpStatus.CONFLICT
                    ran shouldBe false
                    verify(exactly = 0) { f.requests.save(any()) }
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-0000000000b1"
        const val REQUEST = "request-a"
    }
}
