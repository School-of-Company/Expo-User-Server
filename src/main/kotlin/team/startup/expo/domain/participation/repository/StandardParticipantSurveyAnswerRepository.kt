package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantSurveyAnswer

interface StandardParticipantSurveyAnswerRepository : JpaRepository<StandardParticipantSurveyAnswer, Long> {
    fun existsBySurveyIdAndStandardParticipant(
        surveyId: String,
        standardParticipant: StandardParticipant,
    ): Boolean
}
