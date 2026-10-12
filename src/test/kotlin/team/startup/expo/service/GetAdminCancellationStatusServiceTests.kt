package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.presentation.dto.request.GetAdminCancellationStatusReqDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.impl.GetAdminCancellationStatusServiceImpl
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException

class GetAdminCancellationStatusServiceTests :
    BehaviorSpec({
        val standardParticipantRepository = mockk<StandardParticipantRepository>()
        val participationRepository = mockk<StandardParticipantParticipationRepository>()
        val traineeRepository = mockk<TraineeRepository>()
        val service = GetAdminCancellationStatusServiceImpl(standardParticipantRepository, participationRepository, traineeRepository)

        fun request(participantId: Long) = GetAdminCancellationStatusReqDto(EXPO, participantId)

        given("이 박람회의 입장하지 않았고 연수자와 이어지지 않은 참가자일 때") {
            every { standardParticipantRepository.existsByIdAndExpoId(1L, EXPO) } returns true
            every { participationRepository.existsByStandardParticipantId(1L) } returns false
            every { traineeRepository.findByStandardParticipantId(1L) } returns null

            `when`("조회하면") {
                val result = service.execute(request(1L))

                then("미입장이고 명시적 미연결이다") {
                    result.participantId shouldBe 1L
                    result.entered shouldBe false
                    result.traineeLinked shouldBe false
                    result.linkedTraineeId shouldBe null
                }
            }
        }

        given("입장했고 연수자와 이어진 참가자일 때") {
            every { standardParticipantRepository.existsByIdAndExpoId(2L, EXPO) } returns true
            every { participationRepository.existsByStandardParticipantId(2L) } returns true
            every { traineeRepository.findByStandardParticipantId(2L) } returns mockk<Trainee> { every { id } returns 9L }

            `when`("조회하면") {
                val result = service.execute(request(2L))

                then("입장했고 연결된 연수자 id를 돌려준다") {
                    result.entered shouldBe true
                    result.traineeLinked shouldBe true
                    result.linkedTraineeId shouldBe 9L
                }
            }
        }

        given("없는 참가자이거나 다른 박람회의 참가자일 때") {
            every { standardParticipantRepository.existsByIdAndExpoId(99L, EXPO) } returns false

            `when`("조회하면") {
                then("구분 없이 404이고 입장·연결 조회는 하지 않는다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request(99L)) }

                    error.status shouldBe HttpStatus.NOT_FOUND
                    verify(exactly = 0) { participationRepository.existsByStandardParticipantId(99L) }
                    verify(exactly = 0) { traineeRepository.findByStandardParticipantId(99L) }
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000e03"
    }
}
