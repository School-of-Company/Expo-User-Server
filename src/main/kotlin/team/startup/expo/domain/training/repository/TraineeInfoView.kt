package team.startup.expo.domain.training.repository

import team.startup.expo.domain.training.entity.ApplicationType

/** 목록에 필요한 컬럼만 읽는 투영. `information_json` 같은 큰 컬럼을 읽지 않는다. */
interface TraineeInfoView {
    val id: Long
    val name: String
    val trainingId: String
    val phoneNumber: String
    val applicationType: ApplicationType
}
