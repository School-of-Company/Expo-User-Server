package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.GetCancellationStatusReqDto
import team.startup.expo.domain.participation.repository.ManagedParticipantView
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.participation.service.impl.GetCancellationStatusServiceImpl
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.global.exception.ExpectedException

class GetCancellationStatusServiceTests :
    BehaviorSpec({
        val representativeFinder = mockk<RepresentativeFinder>()
        val standardParticipantRepository = mockk<StandardParticipantRepository>()
        val participationRepository = mockk<StandardParticipantParticipationRepository>()
        val traineeRepository = mockk<TraineeRepository>()
        val service =
            GetCancellationStatusServiceImpl(
                representativeFinder,
                standardParticipantRepository,
                participationRepository,
                traineeRepository,
            )

        fun view(id: Long): ManagedParticipantView = mockk { every { this@mockk.id } returns id }

        fun representative(id: Long): StandardParticipant = mockk { every { this@mockk.id } returns id }

        fun request(participantId: Long) = GetCancellationStatusReqDto(EXPO, PHONE, participantId)

        fun givenRepresentative(
            representativeId: Long,
            vararg managedIds: Long,
        ) {
            every { representativeFinder.find(EXPO, PHONE) } returns representative(representativeId)
            every { standardParticipantRepository.findAllByIdOrRepresentativeIdOrderById(representativeId, representativeId) } returns
                managedIds.map(::view)
        }

        given("대표자가 관리하는 참가자가 입장하지 않았고 연수자와 이어지지 않았을 때") {
            givenRepresentative(1L, 1L, 2L)
            every { participationRepository.existsByStandardParticipantId(2L) } returns false
            every { traineeRepository.findByStandardParticipantId(2L) } returns null

            `when`("동행자의 취소 상태를 조회하면") {
                val result = service.execute(request(2L))

                then("대표자가 아니고 미입장이며 명시적 미연결이다") {
                    result.participantId shouldBe 2L
                    result.representative shouldBe false
                    result.entered shouldBe false
                    result.traineeLinked shouldBe false
                    result.linkedTraineeId shouldBe null
                }
            }
        }

        given("대표자 본인이 입장했고 연수자와 이어졌을 때") {
            givenRepresentative(1L, 1L)
            every { participationRepository.existsByStandardParticipantId(1L) } returns true
            every { traineeRepository.findByStandardParticipantId(1L) } returns mockk<Trainee> { every { id } returns 9L }

            `when`("대표자 본인의 취소 상태를 조회하면") {
                val result = service.execute(request(1L))

                then("대표자이고 입장했으며 연결된 연수자 id를 돌려준다") {
                    result.representative shouldBe true
                    result.entered shouldBe true
                    result.traineeLinked shouldBe true
                    result.linkedTraineeId shouldBe 9L
                }
            }
        }

        given("요청한 참가자가 이 대표자의 관리 대상이 아닐 때") {
            givenRepresentative(1L, 1L)

            `when`("다른 사람의 참가자를 조회하면") {
                then("404이고 입장·연결 조회는 하지 않는다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request(99L)) }

                    error.status shouldBe HttpStatus.NOT_FOUND
                    verify(exactly = 0) { participationRepository.existsByStandardParticipantId(99L) }
                    verify(exactly = 0) { traineeRepository.findByStandardParticipantId(99L) }
                }
            }
        }

        given("등록되지 않은 번호일 때") {
            every { representativeFinder.find(EXPO, PHONE) } returns null

            `when`("조회하면") {
                then("관리 대상이 아닐 때와 같은 404이다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request(1L)) }

                    error.status shouldBe HttpStatus.NOT_FOUND
                    error.message shouldBe "관리하는 참가자를 찾을 수 없습니다."
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000e01"
        const val PHONE = "01012345678"
    }
}
