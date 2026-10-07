package team.startup.expo.domain.participation.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantBriefsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantBriefResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantDetailResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.repository.StandardParticipantSurveyAnswerRepository
import team.startup.expo.domain.participation.service.GetStandardParticipantDetailsService
import team.startup.expo.global.dto.DetailPageReqDto
import team.startup.expo.global.dto.DetailPageResDto
import team.startup.expo.global.dto.InformationResDto
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.InformationJson

/**
 * Report 서비스의 엑셀 내보내기용 조회다. v1 `findByExpo`처럼 `id` 오름차순이고, 행사에 참가자가 없으면 빈 목록이다.
 * 행사 존재 여부는 호출자가 Expo 서비스에 따로 확인한다.
 *
 * 설문 답변은 페이지의 참가자에 대해서만 한 번에 읽는다. 설문이 여럿이면 v1 엑셀처럼 가장 최근 답변을 쓴다.
 */
@Service
class GetStandardParticipantDetailsServiceImpl(
    private val standardParticipantRepository: StandardParticipantRepository,
    private val surveyAnswerRepository: StandardParticipantSurveyAnswerRepository,
) : GetStandardParticipantDetailsService {
    @Transactional(readOnly = true)
    override fun page(
        expoId: String,
        reqDto: DetailPageReqDto,
    ): DetailPageResDto<StandardParticipantDetailResDto> {
        val size = reqDto.sizeOrDefault
        // 다음 페이지가 있는지 알려고 한 행 더 읽는다
        val rows =
            standardParticipantRepository.findByExpoIdAndIdGreaterThanOrderById(expoId, reqDto.cursorOrZero, PageRequest.ofSize(size + 1))
        val hasNext = rows.size > size
        val participants = rows.take(size)

        val surveyAnswers =
            surveyAnswerRepository
                .findAnswersByParticipantIds(participants.map { it.id!! })
                .associateBy { it.participantId }

        val items =
            participants.map {
                StandardParticipantDetailResDto(
                    participantId = it.id!!,
                    name = it.name,
                    phoneNumber = it.phoneNumber,
                    personalInformationStatus = it.personalInformationStatus,
                    applicationType = it.applicationType,
                    information =
                        InformationResDto(
                            answers = InformationJson.toNode(it.informationJson),
                            questions = InformationJson.toQuestionsNode(it.informationQuestions),
                        ),
                    surveyAnswer =
                        surveyAnswers[it.id]?.let { answer ->
                            InformationResDto(
                                answers = InformationJson.toNode(answer.answerJson),
                                questions = InformationJson.toQuestionsNode(answer.answerQuestions),
                            )
                        },
                )
            }
        return DetailPageResDto(items = items, nextCursor = if (hasNext) items.last().participantId else null)
    }

    /**
     * 요청한 id를 요청 순서대로 모두 돌려주거나 아예 실패한다. 없는 id와 다른 박람회의 참가자는 구분하지 않고
     * 같은 404로 처리해(`/names`와 같다) 다른 박람회의 참가자 존재 여부가 드러나지 않게 한다. 중복 id는 한 번만 돌려준다.
     */
    @Transactional(readOnly = true)
    override fun briefs(reqDto: GetStandardParticipantBriefsReqDto): List<StandardParticipantBriefResDto> {
        val ids = reqDto.participantIds.distinct()
        val found =
            ids
                .chunked(QUERY_CHUNK_SIZE)
                .flatMap { standardParticipantRepository.findBriefsByExpoIdAndIdIn(reqDto.expoId, it) }
                .associateBy { it.id }
        if (found.size != ids.size) {
            val missing = ids.filterNot(found::containsKey)
            val shown = missing.take(MAX_REPORTED_IDS).joinToString(", ")
            val rest = if (missing.size > MAX_REPORTED_IDS) " 외 ${missing.size - MAX_REPORTED_IDS}개" else ""
            throw ExpectedException(HttpStatus.NOT_FOUND, "요청한 참가자 중 찾을 수 없는 참가자가 있습니다. (id: $shown$rest)")
        }
        return ids.map {
            val view = found.getValue(it)
            StandardParticipantBriefResDto(
                participantId = view.id,
                name = view.name,
                phoneNumber = view.phoneNumber,
                personalInformationStatus = view.personalInformationStatus,
            )
        }
    }

    private companion object {
        const val QUERY_CHUNK_SIZE = 1_000
        const val MAX_REPORTED_IDS = 20
    }
}
