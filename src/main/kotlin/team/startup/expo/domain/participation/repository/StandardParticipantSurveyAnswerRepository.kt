package team.startup.expo.domain.participation.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.entity.StandardParticipantSurveyAnswer

interface StandardParticipantSurveyAnswerRepository : JpaRepository<StandardParticipantSurveyAnswer, Long> {
    fun existsBySurveyIdAndStandardParticipant(
        surveyId: String,
        standardParticipant: StandardParticipant,
    ): Boolean

    /** 참가자별 답변을 `id` 오름차순으로 읽는다. 호출자는 같은 참가자의 마지막(가장 최근) 답변을 쓴다. */
    @Query(
        "select a.standardParticipant.id as participantId, a.answerJson as answerJson " +
            "from StandardParticipantSurveyAnswer a where a.standardParticipant.id in :participantIds order by a.id",
    )
    fun findAnswersByParticipantIds(
        @Param("participantIds") participantIds: Collection<Long>,
    ): List<SurveyAnswerView>
}
