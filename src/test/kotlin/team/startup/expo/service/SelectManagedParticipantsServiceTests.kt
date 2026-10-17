package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.SelectManagedParticipantsReqDto
import team.startup.expo.domain.participation.repository.ManagedParticipantView
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.participation.service.impl.SelectManagedParticipantsServiceImpl
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.exception.ExpectedException

class SelectManagedParticipantsServiceTests :
    BehaviorSpec({
        val representativeFinder = mockk<RepresentativeFinder>()
        val standardParticipantRepository = mockk<StandardParticipantRepository>()
        val service = SelectManagedParticipantsServiceImpl(representativeFinder, standardParticipantRepository)

        fun view(
            id: Long,
            name: String,
            occupation: Occupation?,
            applicationType: ApplicationType = ApplicationType.PRE,
        ): ManagedParticipantView =
            mockk {
                every { this@mockk.id } returns id
                every { this@mockk.name } returns name
                every { this@mockk.occupation } returns occupation
                every { this@mockk.applicationType } returns applicationType
            }

        fun request(vararg ids: Long) = SelectManagedParticipantsReqDto(EXPO, PHONE, ids.toList())

        given("대표자 1번과 동행자 2번, 3번이 있을 때") {
            every { representativeFinder.find(EXPO, PHONE) } returns mockk<StandardParticipant> { every { id } returns 1L }
            every { standardParticipantRepository.findAllByIdOrRepresentativeIdOrderById(1L, 1L) } returns
                listOf(
                    view(1L, "대표", Occupation.TEACHER),
                    view(2L, "동행", null),
                    view(3L, "동행2", Occupation.PARENT, ApplicationType.FIELD),
                )

            `when`("3번, 1번, 3번 순서로 선택하면") {
                val result = service.execute(request(3L, 1L, 3L))

                then("중복은 한 번만 요청 순서대로 돌려주고 대표자 여부를 표시한다") {
                    result.map { it.participantId } shouldBe listOf(3L, 1L)
                    result.map { it.representative } shouldBe listOf(false, true)
                    result[0].occupation shouldBe Occupation.PARENT
                    result.map { it.applicationType } shouldBe listOf(ApplicationType.FIELD, ApplicationType.PRE)
                }
            }

            `when`("관리 대상이 아닌 id가 하나라도 섞여 있으면") {
                then("일부만 돌려주지 않고 404로 실패한다") {
                    val error = shouldThrow<ExpectedException> { service.execute(request(1L, 99L)) }

                    error.status shouldBe HttpStatus.NOT_FOUND
                    error.message shouldBe "관리하는 참가자를 찾을 수 없습니다."
                }
            }
        }

        given("등록되지 않은 번호일 때") {
            every { representativeFinder.find(EXPO, PHONE) } returns null

            `when`("선택하면") {
                then("남의 참가자 id를 고를 때와 같은 404이다") {
                    shouldThrow<ExpectedException> { service.execute(request(1L)) }.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }) {
    private companion object {
        const val EXPO = "0199aaaa-0000-7000-8000-000000000e02"
        const val PHONE = "01012345678"
    }
}
