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

/**
 * 입장 안내용 작업 조회. `jobId`는 요청한 작업, `currentJobId`는 재출력을 따라간 가장 최신 작업(없으면 `jobId`)이고 `status`는
 * `currentJobId`의 상태다. `station`은 그 작업이 `CLAIMED`·`SUBMITTED`일 때만 점유한 출력대이고, 그 밖의 상태(재시도 대기
 * `WAITING` 포함)는 `null`이다. 지난 점유의 출력대를 현재 배정처럼 보여 주지 않기 위해서다.
 */
data class BadgePrintJobStateResDto(
    val jobId: Long,
    val currentJobId: Long,
    val status: BadgePrintJobStatus,
    val station: BadgePrintJobStationResDto?,
)

data class BadgePrintJobStationResDto(
    val stationId: UUID,
    val label: String,
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
