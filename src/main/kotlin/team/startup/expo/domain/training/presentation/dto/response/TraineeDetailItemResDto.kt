package team.startup.expo.domain.training.presentation.dto.response

import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.dto.InformationResDto

data class TraineeDetailItemResDto(
    val traineeId: Long,
    val name: String,
    val trainingId: String?,
    val phoneNumber: String?,
    // 문자를 받을 번호. 이어 만든 연수자는 본인 번호가 없어 대표자나 참가자의 번호이고, 없으면 phoneNumber다
    val notificationPhoneNumber: String?,
    val personalInformationStatus: Boolean,
    val applicationType: ApplicationType,
    val information: InformationResDto,
    // 사전등록 참가자에 이어진 연수자만 값이 있다. 기존 연수자는 모두 null이고 위 information이 그대로다
    val participantId: Long?,
    val school: String?,
    // 이어진 참가자의 등록 당시 답변 원천이다. 신규 연수 신청 답변(Application 소유)과 섞지 않는다. 스냅샷 없이 저장된
    // 참가자(동행자 등)나 다른 박람회의 참가자면 null이다
    val registration: InformationResDto?,
)
