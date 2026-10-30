package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.ClaimBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.CreateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetBadgePrintJobsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ReportBadgePrintResultReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveBadgePrintJobReqDto
import team.startup.expo.domain.participation.presentation.dto.request.UpdateBadgePrintStationReqDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintAttemptsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintClaimResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobStateResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintJobsResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintSessionResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationResDto
import team.startup.expo.domain.participation.presentation.dto.response.BadgePrintStationsResDto
import team.startup.expo.domain.participation.service.BadgePrintService
import team.startup.expo.global.security.AdminPrincipal
import java.util.UUID

@Tag(name = "명찰 자동 출력(관리자)", description = "출력대 등록·세션, 작업 claim·결과 보고, 확인 필요 작업 처리")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/badge-prints/{expo_id}")
class BadgePrintController(
    private val badgePrintService: BadgePrintService,
) {
    @Operation(summary = "출력대 등록", description = "새 출력대와 첫 세션을 만듭니다. label이 없으면 '출력대 N'입니다.")
    @PostMapping("/stations")
    fun createStation(
        @PathVariable("expo_id") expoId: String,
        @Valid @RequestBody(required = false) reqDto: CreateBadgePrintStationReqDto?,
    ): BadgePrintStationResDto = badgePrintService.createStation(expoId, reqDto ?: CreateBadgePrintStationReqDto())

    @Operation(summary = "출력대 목록", description = "등록 순서대로 일시정지 여부와 마지막 요청 시각(한국 시간)을 줍니다.")
    @GetMapping("/stations")
    fun getStations(
        @PathVariable("expo_id") expoId: String,
    ): BadgePrintStationsResDto = badgePrintService.getStations(expoId)

    @Operation(
        summary = "출력대 세션 열기",
        description =
            "출력 화면을 열 때마다 부릅니다. 이전 세션의 claim·결과 보고는 이후 409입니다. 이전 세션이 점유 중이던 작업은 " +
                "인쇄 여부를 알 수 없으므로 NEEDS_CHECK가 됩니다.",
    )
    @PostMapping("/stations/{station_id}/sessions")
    fun openSession(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("station_id") stationId: UUID,
    ): BadgePrintSessionResDto = badgePrintService.openSession(expoId, stationId)

    @Operation(summary = "출력대 일시정지/재개", description = "paused=true면 claim이 새 작업을 주지 않습니다(204). 점유 중인 작업의 결과 보고는 받습니다.")
    @PatchMapping("/stations/{station_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateStation(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("station_id") stationId: UUID,
        @Valid @RequestBody reqDto: UpdateBadgePrintStationReqDto,
    ) = badgePrintService.updateStation(expoId, stationId, reqDto)

    @Operation(
        summary = "다음 출력 작업 점유",
        description =
            "오늘(한국 시간) 대기 작업을 오래된 순서로 하나 점유합니다. 없거나 일시정지면 204, 세션이 바뀌었으면 409입니다. " +
                "같은 requestId로 다시 보내면 이미 점유한 작업을 같은 claimToken으로 다시 줍니다(응답 유실 재시도). " +
                "결과를 보고하지 않은 점유가 있는데 다른 requestId로 보내면 그 작업은 NEEDS_CHECK가 되고 다음 작업을 줍니다. " +
                "점유 후 30초 안에 결과가 없으면 NEEDS_CHECK입니다.",
    )
    @PostMapping("/stations/{station_id}/claim")
    fun claim(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("station_id") stationId: UUID,
        @Valid @RequestBody reqDto: ClaimBadgePrintJobReqDto,
    ): ResponseEntity<BadgePrintClaimResDto> =
        badgePrintService.claim(expoId, stationId, reqDto)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @Operation(
        summary = "출력 결과 보고",
        description =
            "SUBMITTED는 인쇄 요청을 넘겼다는 뜻입니다. FAILED_BEFORE_PRINT는 2/5/15초 뒤 다시 대기하고 네 번째 실패에서 FAILED입니다. " +
                "같은 결과를 다시 보내면 204, 세션·토큰이 맞지 않거나 이미 다른 결과(만료 등)로 닫혔으면 409입니다.",
    )
    @PostMapping("/jobs/{job_id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reportResult(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("job_id") jobId: Long,
        @Valid @RequestBody reqDto: ReportBadgePrintResultReqDto,
    ) = badgePrintService.reportResult(expoId, jobId, reqDto)

    @Operation(summary = "오늘 작업 목록", description = "status를 주지 않으면 NEEDS_CHECK, FAILED입니다. 예: status=NEEDS_CHECK,FAILED")
    @GetMapping("/jobs")
    fun getJobs(
        @PathVariable("expo_id") expoId: String,
        @ParameterObject @Valid @ModelAttribute reqDto: GetBadgePrintJobsReqDto,
    ): BadgePrintJobsResDto = badgePrintService.getJobs(expoId, reqDto)

    @Operation(
        summary = "작업 상태와 출력대 조회",
        description =
            "입장 응답의 badgePrintJobId로 조회합니다. 재출력됐으면 가장 최신 작업(currentJobId)의 상태를 줍니다. station은 " +
                "CLAIMED·SUBMITTED일 때만 그 작업을 점유한 출력대이고, 아직 점유되지 않았거나 재시도 대기·확인 필요·실패·완료면 null입니다. " +
                "SUBMITTED는 인쇄 요청 전달이지 종이 출력 완료가 아닙니다. 다른 박람회의 작업은 404입니다.",
    )
    @GetMapping("/jobs/{job_id}")
    fun getJob(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("job_id") jobId: Long,
    ): BadgePrintJobStateResDto = badgePrintService.getJob(expoId, jobId)

    @Operation(summary = "작업 시도 이력", description = "점유마다 한 줄입니다. outcome이 없으면 아직 결과가 없는 점유입니다.")
    @GetMapping("/jobs/{job_id}/attempts")
    fun getAttempts(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("job_id") jobId: Long,
    ): BadgePrintAttemptsResDto = badgePrintService.getAttempts(expoId, jobId)

    @Operation(
        summary = "확인 필요·실패 작업 처리",
        description =
            "REQUEUE는 원본을 가리키는 새 대기 작업을 만들고, DONE은 처리 완료로 닫습니다. 같은 requestKey로 다시 보내면 204이고, " +
                "이미 다른 요청으로 처리됐으면 409입니다.",
    )
    @PostMapping("/jobs/{job_id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resolve(
        @PathVariable("expo_id") expoId: String,
        @PathVariable("job_id") jobId: Long,
        @AuthenticationPrincipal principal: AdminPrincipal,
        @Valid @RequestBody reqDto: ResolveBadgePrintJobReqDto,
    ) = badgePrintService.resolve(expoId, jobId, principal.id, reqDto)
}
