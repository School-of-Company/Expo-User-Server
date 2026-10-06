package team.startup.expo.domain.training.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.startup.expo.domain.training.entity.Trainee
import team.startup.expo.domain.training.entity.TraineeSurveyAnswer

interface TraineeSurveyAnswerRepository : JpaRepository<TraineeSurveyAnswer, Long> {
    fun existsBySurveyIdAndTrainee(
        surveyId: String,
        trainee: Trainee,
    ): Boolean
}
