package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.Size
import team.startup.expo.domain.participation.entity.BadgePrintJobStatus
import team.startup.expo.domain.participation.entity.BadgePrintOutcome
import team.startup.expo.domain.participation.entity.BadgePrintResolveAction
import java.util.UUID

/** `label`이 없으면 `출력대 N`(등록 순서)이다. */
data class CreateBadgePrintStationReqDto(
    @field:Size(max = 50)
    val label: String? = null,
)

data class UpdateBadgePrintStationReqDto(
    val paused: Boolean,
)

/**
 * `requestId`는 claim 요청마다 새로 만드는 값이다. 응답을 못 받아 같은 요청을 다시 보낼 때만 같은 값을 보낸다. 그러면 이미
 * 점유한 작업을 같은 `claimToken`으로 다시 준다. 값이 없거나 다르면 결과를 보고하지 않은 점유를 `NEEDS_CHECK`로 닫는다.
 */
data class ClaimBadgePrintJobReqDto(
    val sessionId: UUID,
    val requestId: UUID? = null,
)

/** `reason`은 실패 사유로 시도 이력에 남는다. 이름·전화번호를 넣지 않는다. */
data class ReportBadgePrintResultReqDto(
    val sessionId: UUID,
    val claimToken: UUID,
    val outcome: BadgePrintOutcome,
    @field:Size(max = 200)
    val reason: String? = null,
)

/** `status`가 없으면 `NEEDS_CHECK`, `FAILED`이다. */
data class GetBadgePrintJobsReqDto(
    val status: List<BadgePrintJobStatus>? = null,
)

/** `requestKey`는 처리 요청의 멱등키다. 같은 키로 다시 보내면 같은 결과(204)다. */
data class ResolveBadgePrintJobReqDto(
    val action: BadgePrintResolveAction,
    val requestKey: UUID,
)
