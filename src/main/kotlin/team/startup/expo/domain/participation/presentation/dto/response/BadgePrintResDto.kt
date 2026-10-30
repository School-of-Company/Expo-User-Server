package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.BadgePrintAttemptOutcome
import team.startup.expo.domain.participation.entity.BadgePrintJobStatus
import java.time.LocalDateTime
import java.util.UUID

data class BadgePrintStationResDto(
    val stationId: UUID,
    val label: String,
    val sessionId: UUID,
)

data class BadgePrintSessionResDto(
    val sessionId: UUID,
)

data class BadgePrintStationsResDto(
    val stations: List<BadgePrintStationStateResDto>,
)

/** `lastSeenAt`은 출력대가 마지막으로 claim·결과를 보낸 시각(한국 시간)이다. 프린터가 정상이라는 뜻은 아니다. */
data class BadgePrintStationStateResDto(
    val stationId: UUID,
    val label: String,
    val paused: Boolean,
    val lastSeenAt: LocalDateTime?,
)

data class BadgePrintClaimResDto(
    val jobId: Long,
    val claimToken: UUID,
    val name: String,
    val school: String?,
)

data class BadgePrintJobsResDto(
    val jobs: List<BadgePrintJobResDto>,
)

data class BadgePrintJobResDto(
    val jobId: Long,
    val name: String,
    val school: String?,
    val status: BadgePrintJobStatus,
    val updatedAt: LocalDateTime,
)

data class BadgePrintAttemptsResDto(
    val attempts: List<BadgePrintAttemptResDto>,
)

/** `outcome`이 없으면 아직 결과가 없는 점유다. */
data class BadgePrintAttemptResDto(
    val stationId: UUID,
    val claimedAt: LocalDateTime,
    val outcome: BadgePrintAttemptOutcome?,
    val reason: String?,
    val finishedAt: LocalDateTime?,
)
