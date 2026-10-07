package team.startup.expo.domain.participation.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.ParticipationType
import team.startup.expo.domain.participation.entity.StandardParticipantSurveyAnswer
import team.startup.expo.domain.participation.entity.SurveyAnswerEvent
import team.startup.expo.domain.participation.entity.SurveyAnswerStatus
import team.startup.expo.domain.participation.presentation.dto.request.SurveyAnswerSubmittedEvent
import team.startup.expo.domain.participation.presentation.dto.response.SurveyAnswerResultEvent
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.repository.StandardParticipantSurveyAnswerRepository
import team.startup.expo.domain.participation.repository.SurveyAnswerEventRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.SaveSurveyAnswerService
import team.startup.expo.domain.training.entity.TraineeSurveyAnswer
import team.startup.expo.domain.training.repository.TraineeRepository
import team.startup.expo.domain.training.repository.TraineeSurveyAnswerRepository
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.util.PhoneNumbers
import team.startup.expo.global.util.QuestionSnapshot

/**
 * 같은 `eventId`는 한 번만 저장하고, 다시 오면 처음의 결과를 그대로 돌려준다. 결과 이벤트가 유실됐을 때
 * Form이 재발행한 이벤트에 같은 결과로 답하는 것이 Form이 접수를 종결하는 유일한 경로다.
 *
 * `(survey_id, 응답자)`에 이미 답변이 있으면 다른 `eventId`여도 정상 중복으로 보고 `STORED`로 답한다.
 * 응답자를 찾지 못하거나 번호를 하나로 특정할 수 없는 것은 다시 해도 같으므로 `REJECTED`이고,
 * 그 밖의 예외는 일시적 장애로 보고 전파해 컨슈머가 재시도하게 한다.
 *
 * 삭제 중이거나 삭제된 박람회의 이벤트는 이벤트 기록과 답변을 남기지 않고 건너뛴다(`null`). 폼 서비스도 그 박람회의 접수 기록을
 * 지웠으므로 결과를 받을 곳이 없다. 같은 박람회의 삭제와는 [ExpoDeletionGuard]의 lock으로 직렬화한다.
 *
 * 조회 서비스를 호출하지 않고 저장소를 직접 쓴다. `@Transactional` 서비스에서 예외가 나가면 바깥
 * 트랜잭션이 rollback-only가 되어 `REJECTED` 기록까지 롤백되기 때문이다.
 */
@Service
class SaveSurveyAnswerServiceImpl(
    private val surveyAnswerEventRepository: SurveyAnswerEventRepository,
    private val traineeRepository: TraineeRepository,
    private val standardParticipantRepository: StandardParticipantRepository,
    private val traineeSurveyAnswerRepository: TraineeSurveyAnswerRepository,
    private val standardParticipantSurveyAnswerRepository: StandardParticipantSurveyAnswerRepository,
    private val expoDeletionGuard: ExpoDeletionGuard,
) : SaveSurveyAnswerService {
    @Transactional
    override fun execute(event: SurveyAnswerSubmittedEvent): SurveyAnswerResultEvent? {
        // 박람회 삭제와 같은 박람회의 소비를 한 줄로 세운다. 삭제가 끝난 뒤(또는 진행 중인 삭제가 끝난 뒤)에 늦게 도착한 이벤트가
        // 삭제된 박람회의 이벤트 기록이나 답변을 새로 남기지 않도록 아무것도 저장하지 않고 건너뛴다
        if (expoDeletionGuard.isDeleted(event.expoId)) return null

        surveyAnswerEventRepository.findById(event.eventId).orElse(null)?.let {
            return SurveyAnswerResultEvent(it.eventId, it.status, it.reason)
        }

        val rejection =
            try {
                store(event)
                null
            } catch (e: ExpectedException) {
                e.message
            }

        val status = if (rejection == null) SurveyAnswerStatus.STORED else SurveyAnswerStatus.REJECTED
        surveyAnswerEventRepository.save(
            SurveyAnswerEvent(
                eventId = event.eventId,
                surveyId = event.surveyId,
                expoId = event.expoId,
                status = status,
                reason = rejection?.take(REASON_MAX_LENGTH),
            ),
        )
        return SurveyAnswerResultEvent(event.eventId, status, rejection)
    }

    private fun store(event: SurveyAnswerSubmittedEvent) {
        val digits = PhoneNumbers.digitsOnly(event.phoneNumber)
        when (event.participationType) {
            ParticipationType.TRAINEE -> {
                val trainee =
                    traineeRepository.findByExpoIdAndPhoneNumber(event.expoId, event.phoneNumber)
                        ?: PhoneNumbers.select(traineeRepository.findAllByExpoIdAndDigits(event.expoId, digits), event.phoneNumber) {
                            it.phoneNumber
                        }
                        ?: throw ExpectedException(HttpStatus.NOT_FOUND, RESPONDENT_NOT_FOUND)
                if (!traineeSurveyAnswerRepository.existsBySurveyIdAndTrainee(event.surveyId, trainee)) {
                    traineeSurveyAnswerRepository.save(
                        TraineeSurveyAnswer(
                            surveyId = event.surveyId,
                            trainee = trainee,
                            answerJson = event.answerJson,
                            personalInformationStatus = event.personalInformationStatus,
                            answerQuestions = QuestionSnapshot.serialize(event.questions),
                        ),
                    )
                }
            }

            ParticipationType.STANDARD -> {
                val participant =
                    standardParticipantRepository.findByExpoIdAndPhoneNumber(event.expoId, event.phoneNumber)
                        ?: PhoneNumbers.select(
                            standardParticipantRepository.findAllByExpoIdAndDigits(event.expoId, digits),
                            event.phoneNumber,
                        ) { it.phoneNumber }
                        ?: throw ExpectedException(HttpStatus.NOT_FOUND, RESPONDENT_NOT_FOUND)
                if (!standardParticipantSurveyAnswerRepository.existsBySurveyIdAndStandardParticipant(event.surveyId, participant)) {
                    standardParticipantSurveyAnswerRepository.save(
                        StandardParticipantSurveyAnswer(
                            surveyId = event.surveyId,
                            standardParticipant = participant,
                            answerJson = event.answerJson,
                            personalInformationStatus = event.personalInformationStatus,
                            answerQuestions = QuestionSnapshot.serialize(event.questions),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val RESPONDENT_NOT_FOUND = "행사 참가자를 찾지 못 했습니다."
        const val REASON_MAX_LENGTH = 255
    }
}
