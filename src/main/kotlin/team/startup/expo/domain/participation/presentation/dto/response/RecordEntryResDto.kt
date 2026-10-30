package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.BadgePrintResult
import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.ParticipationType

/**
 * 연수자는 `occupation`이 없고(연수자는 모두 교사) `school`만 있다. 동행자는 `phoneNumber`가 없고, 입장 문자는
 * `notificationPhoneNumber`(본인 번호, 없으면 대표자 번호)로 보낸다.
 *
 * `badgePrint`는 명찰 자동 출력 작업 저장 결과다. `QUEUED`는 이번 입장으로 저장했고, `DUPLICATE`는 같은 사람의 오늘 작업이 이미
 * 있다. 대상(연수자, 교사·예비교사)이 아니면 `null`이다. 이 필드가 없는 이전 서버의 응답도 `null`로 읽으면 된다.
 *
 * `badgePrintJobId`는 그 작업의 id다. `QUEUED`면 이번에 만든 작업, `DUPLICATE`면 같은 사람의 그날 원본 자동 작업이다(재출력
 * 작업이 아니다). 관리자 `GET /admin/badge-prints/{expoId}/jobs/{jobId}`로 상태와 출력대를 조회한다. 대상이 아니면 `null`이다.
 */
data class RecordEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String?,
    val notificationPhoneNumber: String?,
    val personalInformationStatus: Boolean,
    val participationType: ParticipationType,
    val occupation: Occupation?,
    val school: String?,
    val badgePrint: BadgePrintResult? = null,
    val badgePrintJobId: Long? = null,
)
