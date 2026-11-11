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
import team.startup.expo.domain.participation.repository.BadgePrintStation
import team.startup.expo.domain.participation.service.BadgePrintExpiryGrace
import team.startup.expo.domain.participation.service.BadgePrintService
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDateTime
import java.util.UUID

/**
 * 출력대는 오늘(한국 시간) 대기 작업을 오래된 순서로 하나씩 점유(claim)하고 결과를 보고한다. 출력대마다 마지막으로 연 세션만
 * claim·결과를 보낼 수 있고, 점유마다 새 `claimToken`을 준다. 출력대 행을 먼저 잠가 같은 출력대의 요청을 줄 세우고, 작업은
 * `SKIP LOCKED`로 골라 여러 출력대가 같은 작업을 가져가지 않는다.
 *
 * 종이가 나왔는지 알 수 없는 점유는 자동으로 다시 출력하지 않고 `NEEDS_CHECK`로 둔다. 점유 후 [CLAIM_TIMEOUT_SECONDS]초
 * 안에 결과가 없을 때, 새 세션이 출력대를 열었을 때(새로고침·다른 노트북), 같은 세션이 결과 없이 다른 claim을 보냈을 때다.
 * 인쇄 요청 전 실패(`FAILED_BEFORE_PRINT`)만 [RETRY_DELAYS_SECONDS] 뒤에 다시 대기하고, 재시도를 다 쓰면 `FAILED`이다.
 * 결과 모름으로 닫힌 점유라도 직원이 처리하거나 다시 점유하기 전에 결과가 오면 그 결과를 반영한다(#245, [reportResult]).
 *
 * 시각은 모두 DB 시계의 한국 시간이다([BadgePrintRepository.dbNow], #246). 잠금을 잡은 뒤에 읽어 잠금 순서와 시각 순서를 맞춘다.
 * 기동 직후 [BadgePrintExpiryGrace] 동안은 점유 만료를 미룬다.
 */
@Service
class BadgePrintServiceImpl(
    private val badgePrintRepository: BadgePrintRepository,
    private val expiryGrace: BadgePrintExpiryGrace,
) : BadgePrintService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createStation(
        expoId: String,
        reqDto: CreateBadgePrintStationReqDto,
    ): BadgePrintStationResDto {
        val requestKey = reqDto.requestKey
        if (requestKey != null) badgePrintRepository.stationByRequestKey(expoId, requestKey)?.let { return it.toStationRes() }
        val label = reqDto.label?.trim()?.takeIf { it.isNotEmpty() } ?: "출력대 ${badgePrintRepository.countStations(expoId) + 1}"
        val stationId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        if (badgePrintRepository.insertStation(stationId, expoId, label, sessionId, requestKey, dbNow())) {
            return BadgePrintStationResDto(stationId, label, sessionId)
        }
        // 같은 키의 동시 등록이 먼저 커밋했다. 저장하지 않은 경우는 키가 있을 때뿐이다
        val existing = badgePrintRepository.stationByRequestKey(expoId, requireNotNull(requestKey)) ?: throw stationNotFound()
        return existing.toStationRes()
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
        val now = dbNow()
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
        val now = dbNow()
        if (station.sessionId != reqDto.sessionId) throw staleSession()
        touch(station, now)
        if (!expiryGrace.active()) badgePrintRepository.expireClaims(now.minusSeconds(CLAIM_TIMEOUT_SECONDS), now)
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
        val now = dbNow()
        touch(station, now)
        // 잠근 뒤 다시 읽는다. 그사이 만료·인수로 닫혔을 수 있다
        val outcome = requireNotNull(badgePrintRepository.attempt(reqDto.claimToken)).outcome
        if (outcome != null) {
            // 결과 응답을 못 받은 출력대의 재전송은 같은 결과로 성공한다. 늦은 보고를 반영한 뒤의 재전송도 같다
            if (outcome.name == reqDto.outcome.name) return
            // 결과 모름으로 닫힌 뒤 직원이 처리하지도(DONE·REQUEUED) 다시 점유되지도(claim_token 교체) 않았을 때만 늦은 보고를
            // 받는다. 작업 상태를 바꾸는 곳(claim·만료·인수·처리)은 모두 작업 행을 잠그므로 잠근 뒤 읽은 값으로 판단하면 된다
            val acceptsLate =
                outcome in UNKNOWN_OUTCOMES && job.status == BadgePrintJobStatus.NEEDS_CHECK && job.claimToken == reqDto.claimToken
            if (!acceptsLate) throw ExpectedException(HttpStatus.CONFLICT, "이미 닫힌 점유입니다.")
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
        val attemptOutcome = BadgePrintAttemptOutcome.valueOf(reqDto.outcome.name)
        if (outcome == null) {
            badgePrintRepository.finishAttempt(reqDto.claimToken, attemptOutcome, reqDto.reason, now)
        } else {
            badgePrintRepository.finishLateAttempt(reqDto.claimToken, attemptOutcome, reqDto.reason, now)
            log.info("결과 모름으로 닫힌 명찰 출력 점유에 늦은 결과를 반영함: jobId={}, previous={}, outcome={}", jobId, outcome, attemptOutcome)
        }
    }

    @Transactional(readOnly = true)
    override fun getJobs(
        expoId: String,
        reqDto: GetBadgePrintJobsReqDto,
    ): BadgePrintJobsResDto {
        val statuses = reqDto.status?.takeIf { it.isNotEmpty() } ?: listOf(BadgePrintJobStatus.NEEDS_CHECK, BadgePrintJobStatus.FAILED)
        return BadgePrintJobsResDto(
            badgePrintRepository.jobs(expoId, dbNow().toLocalDate(), statuses).map {
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
                BadgePrintAttemptResDto(
                    it.stationId,
                    it.claimedAt,
                    it.outcome,
                    it.reason,
                    it.finishedAt,
                    it.previousOutcome,
                    it.previousFinishedAt,
                )
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
        // 작업을 잠근 뒤 판단한다. 늦은 결과 보고·claim·만료도 같은 행을 잠그므로 한쪽만 반영된다
        val job = badgePrintRepository.lockJob(expoId, jobId) ?: throw jobNotFound()
        val now = dbNow()
        when (reqDto.action) {
            BadgePrintResolveAction.REQUEUE -> {
                val reprint = badgePrintRepository.reprintOf(jobId)
                if (reprint != null) {
                    if (reprint.requestKey == reqDto.requestKey) return
                    throw ExpectedException(HttpStatus.CONFLICT, "이미 다시 대기열에 넣은 작업입니다.")
                }
                // 인쇄 요청을 넘긴 작업의 재출력은 직원이 SUBMITTED를 보고 누른 요청만 받는다. 생략한 구 요청이 늦은 보고 뒤에 와도 다시 찍지 않는다
                if (job.status == BadgePrintJobStatus.SUBMITTED && reqDto.expectedStatus == null) {
                    throw ExpectedException(HttpStatus.CONFLICT, "작업 상태가 바뀌었습니다. 목록을 새로 보세요.")
                }
                requireResolvable(job, reqDto.expectedStatus, REQUEUE_STATUSES)
                badgePrintRepository.insertReprint(jobId, reqDto.requestKey, now.toLocalDate(), now)
                badgePrintRepository.resolve(jobId, BadgePrintJobStatus.REQUEUED, adminId, now)
            }

            BadgePrintResolveAction.DONE -> {
                if (job.status == BadgePrintJobStatus.DONE) return
                requireResolvable(job, reqDto.expectedStatus, DONE_STATUSES)
                badgePrintRepository.resolve(jobId, BadgePrintJobStatus.DONE, adminId, now)
            }
        }
    }

    @Transactional
    override fun expireClaims(): Int {
        if (expiryGrace.active()) return 0
        val now = dbNow()
        return badgePrintRepository.expireClaims(now.minusSeconds(CLAIM_TIMEOUT_SECONDS), now)
    }

    /**
     * 출력대의 마지막 연결 시각을 [LAST_SEEN_INTERVAL_SECONDS]초에 한 번만 쓴다(#246). claim이 2초마다 와도 같은 행을 매번 다시
     * 쓰지 않는다. 연결 판정은 화면에서 20초 기준이라 5초 지연은 문제가 없다. 출력대 잠금 안에서 부른다.
     */
    private fun touch(
        station: BadgePrintStation,
        now: LocalDateTime,
    ) {
        val lastSeenAt = station.lastSeenAt
        if (lastSeenAt != null && lastSeenAt.isAfter(now.minusSeconds(LAST_SEEN_INTERVAL_SECONDS))) return
        badgePrintRepository.updateStation(station.id, station.sessionId, station.paused, now)
    }

    private fun closeUnknown(
        job: BadgePrintJob,
        outcome: BadgePrintAttemptOutcome,
        now: LocalDateTime,
    ) {
        badgePrintRepository.closeUnknown(job, outcome, now)
        log.warn("명찰 출력 점유를 확인 필요로 닫음: jobId={}, outcome={}", job.id, outcome)
    }

    /**
     * 직원이 본 상태([expectedStatus])가 있으면 지금 상태가 그것과 같아야 한다. 확인 필요 작업에 다시 출력을 누른 사이 늦은
     * `SUBMITTED` 보고가 먼저 반영됐으면, 직원이 보지 못한 상태로 재출력하지 않고 409로 다시 보게 한다.
     */
    private fun requireResolvable(
        job: BadgePrintJob,
        expectedStatus: BadgePrintJobStatus?,
        allowed: Set<BadgePrintJobStatus>,
    ) {
        if (expectedStatus != null && job.status != expectedStatus) {
            throw ExpectedException(HttpStatus.CONFLICT, "작업 상태가 바뀌었습니다. 목록을 새로 보세요.")
        }
        if (job.status !in allowed) {
            throw ExpectedException(HttpStatus.CONFLICT, "처리할 수 없는 상태의 작업입니다.")
        }
    }

    private fun BadgePrintStation.toStationRes() = BadgePrintStationResDto(id, label, sessionId)

    private fun BadgePrintJob.toClaim() = BadgePrintClaimResDto(id, requireNotNull(claimToken), name, school)

    private fun dbNow() = badgePrintRepository.dbNow()

    private fun stationNotFound() = ExpectedException(HttpStatus.NOT_FOUND, "출력대를 찾지 못 했습니다.")

    private fun jobNotFound() = ExpectedException(HttpStatus.NOT_FOUND, "출력 작업을 찾지 못 했습니다.")

    private fun staleSession() = ExpectedException(HttpStatus.CONFLICT, "다른 세션이 연 출력대입니다. 출력 화면을 다시 여세요.")

    private fun staleClaim() = ExpectedException(HttpStatus.CONFLICT, "점유 토큰이 맞지 않습니다.")

    companion object {
        const val CLAIM_TIMEOUT_SECONDS = 30L

        /** claim·결과 보고 때 출력대 `last_seen_at`을 다시 쓰는 최소 간격(초). */
        const val LAST_SEEN_INTERVAL_SECONDS = 5L

        /** n번째 인쇄 전 실패 뒤 다시 대기하기까지의 초. 실패가 이보다 많으면(4번째) `FAILED`이다. */
        val RETRY_DELAYS_SECONDS = listOf(2L, 5L, 15L)

        /** 출력대를 안내하는 상태. 지금 점유 중이거나 그 출력대가 인쇄 요청을 넘긴 작업이다. */
        private val STATION_STATUSES = setOf(BadgePrintJobStatus.CLAIMED, BadgePrintJobStatus.SUBMITTED)

        /** 서버가 결과를 모른 채 닫은 시도. 이 시도에는 늦게 온 결과를 반영할 수 있다. */
        private val UNKNOWN_OUTCOMES =
            setOf(BadgePrintAttemptOutcome.EXPIRED, BadgePrintAttemptOutcome.TAKEN_OVER, BadgePrintAttemptOutcome.ABANDONED)

        /** 다시 출력할 수 있는 상태. `SUBMITTED`는 인쇄 요청 뒤 종이가 안 나온 경우다(직원의 "안 나옴"). */
        private val REQUEUE_STATUSES = setOf(BadgePrintJobStatus.NEEDS_CHECK, BadgePrintJobStatus.FAILED, BadgePrintJobStatus.SUBMITTED)

        /** 처리 완료로 닫을 수 있는 상태. `SUBMITTED`는 이미 끝난 작업으로 본다. */
        private val DONE_STATUSES = setOf(BadgePrintJobStatus.NEEDS_CHECK, BadgePrintJobStatus.FAILED)
    }
}
