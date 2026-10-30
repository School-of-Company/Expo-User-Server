package team.startup.expo.domain.participation.service.impl

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.entity.BadgePrintAttemptOutcome
import team.startup.expo.domain.participation.entity.BadgePrintJobStatus
import team.startup.expo.domain.participation.entity.BadgePrintOutcome
import team.startup.expo.domain.participation.entity.BadgePrintResolveAction
import team.startup.expo.domain.participation.presentation.dto.request.ClaimBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.CreateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetBadgePrintJobsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ReportBadgePrintResultReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.UpdateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintAttemptResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintAttemptsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintClaimResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobStateResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobStationResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintSessionResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationStateResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationsResDto
import team.startup.expo.domain.participation.repository.BadgePrintJob
import team.startup.expo.domain.participation.repository.BadgePrintRepository
import team.startup.expo.domain.participation.service.BadgePrintService
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 출력대는 오늘(한국 시간) 대기 작업을 오래된 순서로 하나씩 점유(claim)하고 결과를 보고한다. 출력대마다 마지막으로 연 세션만
 * claim·결과를 보낼 수 있고, 점유마다 새 `claimToken`을 준다. 출력대 행을 먼저 잠가 같은 출력대의 요청을 줄 세우고, 작업은
 * `SKIP LOCKED`로 골라 여러 출력대가 같은 작업을 가져가지 않는다.
 *
 * 종이가 나왔는지 알 수 없는 점유는 자동으로 다시 출력하지 않고 `NEEDS_CHECK`로 둔다. 점유 후 [CLAIM_TIMEOUT_SECONDS]초
 * 안에 결과가 없을 때, 새 세션이 출력대를 열었을 때(새로고침·다른 노트북), 같은 세션이 결과 없이 다른 claim을 보냈을 때다.
 * 인쇄 요청 전 실패(`FAILED_BEFORE_PRINT`)만 [RETRY_DELAYS_SECONDS] 뒤에 다시 대기하고, 재시도를 다 쓰면 `FAILED`이다.
 */
@Service
class BadgePrintServiceImpl(
    private val badgePrintRepository: BadgePrintRepository,
) : BadgePrintService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createStation(
        expoId: String,
        reqDto: CreateBadgePrintStationReqDto,
    ): BadgePrintStationResDto {
        val label = reqDto.label?.trim()?.takeIf { it.isNotEmpty() } ?: "출력대 ${badgePrintRepository.countStations(expoId) + 1}"
        val stationId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        badgePrintRepository.insertStation(stationId, expoId, label, sessionId, now())
        return BadgePrintStationResDto(stationId, label, sessionId)
    }

    @Transactional(readOnly = true)
    override fun getStations(expoId: String) =
        BadgePrintStationsResDto(
            badgePrintRepository.stations(expoId).map { BadgePrintStationStateResDto(it.id, it.label, it.paused, it.lastSeenAt) },
        )

    @Transactional
    override fun openSession(
        expoId: String,
        stationId: UUID,
    ): BadgePrintSessionResDto {
        val station = badgePrintRepository.lockStation(expoId, stationId) ?: throw stationNotFound()
        val now = now()
        badgePrintRepository.activeJob(stationId)?.let { closeUnknown(it, BadgePrintAttemptOutcome.TAKEN_OVER, now) }
        val sessionId = UUID.randomUUID()
        badgePrintRepository.updateStation(stationId, sessionId, station.paused, now)
        return BadgePrintSessionResDto(sessionId)
    }

    @Transactional
    override fun updateStation(
        expoId: String,
        stationId: UUID,
        reqDto: UpdateBadgePrintStationReqDto,
    ) {
        val station = badgePrintRepository.lockStation(expoId, stationId) ?: throw stationNotFound()
        badgePrintRepository.updateStation(stationId, station.sessionId, reqDto.paused, station.lastSeenAt)
    }

    @Transactional
    override fun claim(
        expoId: String,
        stationId: UUID,
        reqDto: ClaimBadgePrintJobReqDto,
    ): BadgePrintClaimResDto? {
        val station = badgePrintRepository.lockStation(expoId, stationId) ?: throw stationNotFound()
        // 잠근 뒤의 시각이라야 같은 출력대의 점유 시각이 잠금 순서와 같다
        val now = now()
        if (station.sessionId != reqDto.sessionId) throw staleSession()
        badgePrintRepository.updateStation(stationId, station.sessionId, station.paused, now)
        badgePrintRepository.expireClaims(now.minusSeconds(CLAIM_TIMEOUT_SECONDS), now)
        val active = badgePrintRepository.activeJob(stationId)
        if (active != null) {
            // 응답을 못 받은 같은 요청의 재시도만 같은 점유를 다시 준다. 그 밖에는 이미 인쇄했을 수 있어 확인 필요로 넘긴다
            if (reqDto.requestId != null && active.claimRequestId == reqDto.requestId) return active.toClaim()
            closeUnknown(active, BadgePrintAttemptOutcome.ABANDONED, now)
        }
        if (station.paused) return null
        val job = badgePrintRepository.pickWaiting(expoId, now.toLocalDate(), now) ?: return null
        val claimToken = UUID.randomUUID()
        badgePrintRepository.claim(job.id, stationId, claimToken, reqDto.requestId, now)
        return BadgePrintClaimResDto(job.id, claimToken, job.name, job.school)
    }

    @Transactional
    override fun reportResult(
        expoId: String,
        jobId: Long,
        reqDto: ReportBadgePrintResultReqDto,
    ) {
        val claimed = badgePrintRepository.attempt(reqDto.claimToken)?.takeIf { it.jobId == jobId } ?: throw staleClaim()
        val station = badgePrintRepository.lockStation(expoId, claimed.stationId) ?: throw jobNotFound()
        if (station.sessionId != reqDto.sessionId) throw staleSession()
        val job = badgePrintRepository.lockJob(expoId, jobId) ?: throw jobNotFound()
        val now = now()
        badgePrintRepository.updateStation(station.id, station.sessionId, station.paused, now)
        // 잠근 뒤 다시 읽는다. 그사이 만료·인수로 닫혔을 수 있다
        val outcome = requireNotNull(badgePrintRepository.attempt(reqDto.claimToken)).outcome
        if (outcome != null) {
            // 결과 응답을 못 받은 출력대의 재전송은 같은 결과로 성공한다
            if (outcome.name == reqDto.outcome.name) return
            throw ExpectedException(HttpStatus.CONFLICT, "이미 닫힌 점유입니다.")
        }
        when (reqDto.outcome) {
            BadgePrintOutcome.SUBMITTED -> {
                badgePrintRepository.updateJob(jobId, BadgePrintJobStatus.SUBMITTED, job.failures, null, now)
            }

            BadgePrintOutcome.FAILED_BEFORE_PRINT -> {
                val failures = job.failures + 1
                val delay = RETRY_DELAYS_SECONDS.getOrNull(failures - 1)
                if (delay == null) {
                    badgePrintRepository.updateJob(jobId, BadgePrintJobStatus.FAILED, failures, null, now)
                } else {
                    badgePrintRepository.updateJob(jobId, BadgePrintJobStatus.WAITING, failures, now.plusSeconds(delay), now)
                }
            }
        }
        badgePrintRepository.finishAttempt(reqDto.claimToken, BadgePrintAttemptOutcome.valueOf(reqDto.outcome.name), reqDto.reason, now)
    }

    @Transactional(readOnly = true)
    override fun getJobs(
        expoId: String,
        reqDto: GetBadgePrintJobsReqDto,
    ): BadgePrintJobsResDto {
        val statuses = reqDto.status?.takeIf { it.isNotEmpty() } ?: listOf(BadgePrintJobStatus.NEEDS_CHECK, BadgePrintJobStatus.FAILED)
        return BadgePrintJobsResDto(
            badgePrintRepository.jobs(expoId, now().toLocalDate(), statuses).map {
                BadgePrintJobResDto(it.id, it.name, it.school, it.status, it.updatedAt)
            },
        )
    }

    @Transactional(readOnly = true)
    override fun getJob(
        expoId: String,
        jobId: Long,
    ): BadgePrintJobStateResDto {
        val current = badgePrintRepository.currentJob(expoId, jobId) ?: throw jobNotFound()
        // station_id는 마지막 점유 값이 상태와 상관없이 남으므로(재시도 대기 포함) 상태로 판단한다
        val station =
            if (current.status in STATION_STATUSES && current.stationId != null && current.stationLabel != null) {
                BadgePrintJobStationResDto(current.stationId, current.stationLabel)
            } else {
                null
            }
        return BadgePrintJobStateResDto(jobId, current.id, current.status, station)
    }

    @Transactional(readOnly = true)
    override fun getAttempts(
        expoId: String,
        jobId: Long,
    ): BadgePrintAttemptsResDto {
        if (!badgePrintRepository.jobExists(expoId, jobId)) throw jobNotFound()
        return BadgePrintAttemptsResDto(
            badgePrintRepository.attempts(jobId).map {
                BadgePrintAttemptResDto(it.stationId, it.claimedAt, it.outcome, it.reason, it.finishedAt)
            },
        )
    }

    @Transactional
    override fun resolve(
        expoId: String,
        jobId: Long,
        adminId: Long,
        reqDto: ResolveBadgePrintJobReqDto,
    ) {
        val now = now()
        val job = badgePrintRepository.lockJob(expoId, jobId) ?: throw jobNotFound()
        when (reqDto.action) {
            BadgePrintResolveAction.REQUEUE -> {
                val reprint = badgePrintRepository.reprintOf(jobId)
                if (reprint != null) {
                    if (reprint.requestKey == reqDto.requestKey) return
                    throw ExpectedException(HttpStatus.CONFLICT, "이미 다시 대기열에 넣은 작업입니다.")
                }
                requireResolvable(job)
                badgePrintRepository.insertReprint(jobId, reqDto.requestKey, now.toLocalDate(), now)
                badgePrintRepository.resolve(jobId, BadgePrintJobStatus.REQUEUED, adminId, now)
            }

            BadgePrintResolveAction.DONE -> {
                if (job.status == BadgePrintJobStatus.DONE) return
                requireResolvable(job)
                badgePrintRepository.resolve(jobId, BadgePrintJobStatus.DONE, adminId, now)
            }
        }
    }

    @Transactional
    override fun expireClaims(): Int {
        val now = now()
        return badgePrintRepository.expireClaims(now.minusSeconds(CLAIM_TIMEOUT_SECONDS), now)
    }

    private fun closeUnknown(
        job: BadgePrintJob,
        outcome: BadgePrintAttemptOutcome,
        now: LocalDateTime,
    ) {
        badgePrintRepository.closeUnknown(job, outcome, now)
        log.warn("명찰 출력 점유를 확인 필요로 닫음: jobId={}, outcome={}", job.id, outcome)
    }

    private fun requireResolvable(job: BadgePrintJob) {
        if (job.status != BadgePrintJobStatus.NEEDS_CHECK && job.status != BadgePrintJobStatus.FAILED) {
            throw ExpectedException(HttpStatus.CONFLICT, "확인 필요 또는 실패 작업만 처리할 수 있습니다.")
        }
    }

    private fun BadgePrintJob.toClaim() = BadgePrintClaimResDto(id, requireNotNull(claimToken), name, school)

    private fun now() = LocalDateTime.now(SEOUL).truncatedTo(ChronoUnit.MICROS)

    private fun stationNotFound() = ExpectedException(HttpStatus.NOT_FOUND, "출력대를 찾지 못 했습니다.")

    private fun jobNotFound() = ExpectedException(HttpStatus.NOT_FOUND, "출력 작업을 찾지 못 했습니다.")

    private fun staleSession() = ExpectedException(HttpStatus.CONFLICT, "다른 세션이 연 출력대입니다. 출력 화면을 다시 여세요.")

    private fun staleClaim() = ExpectedException(HttpStatus.CONFLICT, "점유 토큰이 맞지 않습니다.")

    companion object {
        const val CLAIM_TIMEOUT_SECONDS = 30L

        /** n번째 인쇄 전 실패 뒤 다시 대기하기까지의 초. 실패가 이보다 많으면(4번째) `FAILED`이다. */
        val RETRY_DELAYS_SECONDS = listOf(2L, 5L, 15L)

        /** 출력대를 안내하는 상태. 지금 점유 중이거나 그 출력대가 인쇄 요청을 넘긴 작업이다. */
        private val STATION_STATUSES = setOf(BadgePrintJobStatus.CLAIMED, BadgePrintJobStatus.SUBMITTED)

        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
