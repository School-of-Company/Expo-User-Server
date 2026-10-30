package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.ClaimBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.CreateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetBadgePrintJobsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ReportBadgePrintResultReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.UpdateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintAttemptsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintClaimResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintSessionResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationsResDto
import java.util.UUID

/** 명찰 자동 출력 큐의 출력대·작업 관리. 작업은 입장 기록(`RecordEntryServiceImpl`)이 저장한다. */
interface BadgePrintService {
    fun createStation(
        expoId: String,
        reqDto: CreateBadgePrintStationReqDto,
    ): BadgePrintStationResDto

    fun getStations(expoId: String): BadgePrintStationsResDto

    fun openSession(
        expoId: String,
        stationId: UUID,
    ): BadgePrintSessionResDto

    fun updateStation(
        expoId: String,
        stationId: UUID,
        reqDto: UpdateBadgePrintStationReqDto,
    )

    /** 줄 작업이 없으면 `null`이다. */
    fun claim(
        expoId: String,
        stationId: UUID,
        reqDto: ClaimBadgePrintJobReqDto,
    ): BadgePrintClaimResDto?

    fun reportResult(
        expoId: String,
        jobId: Long,
        reqDto: ReportBadgePrintResultReqDto,
    )

    fun getJobs(
        expoId: String,
        reqDto: GetBadgePrintJobsReqDto,
    ): BadgePrintJobsResDto

    fun getAttempts(
        expoId: String,
        jobId: Long,
    ): BadgePrintAttemptsResDto

    fun resolve(
        expoId: String,
        jobId: Long,
        adminId: Long,
        reqDto: ResolveBadgePrintJobReqDto,
    )

    /** 점유 후 30초 안에 결과가 없는 작업을 모든 박람회에서 `NEEDS_CHECK`로 닫는다. 닫은 수를 돌려준다. */
    fun expireClaims(): Int
}
