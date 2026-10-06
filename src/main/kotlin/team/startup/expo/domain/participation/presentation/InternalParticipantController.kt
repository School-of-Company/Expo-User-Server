package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantNamesReqDto
import team.startup.expo.domain.participation.presentation.dto.request.IncreaseSmsTryTimeReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveStandardParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantNameResDto
import team.startup.expo.domain.participation.service.CreateStandardParticipantService
import team.startup.expo.domain.participation.service.GetStandardParticipantNamesService
import team.startup.expo.domain.participation.service.IncreaseSmsTryTimeService
import team.startup.expo.domain.participation.service.ResolveParticipantService
import team.startup.expo.domain.participation.service.ResolveStandardParticipantService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 전화번호는 URL이나 쿼리에 남지 않도록 요청 본문으로만 받는다.
 */
@RestController
@RequestMapping("/internal")
class InternalParticipantController(
    private val resolveStandardParticipantService: ResolveStandardParticipantService,
    private val resolveParticipantService: ResolveParticipantService,
    private val getStandardParticipantNamesService: GetStandardParticipantNamesService,
    private val createStandardParticipantService: CreateStandardParticipantService,
    private val increaseSmsTryTimeService: IncreaseSmsTryTimeService,
) {
    @Operation(summary = "일반 참가자 id 조회", description = "박람회 id와 전화번호로 일반 참가자 id를 찾습니다. 없으면 404입니다.")
    @PostMapping("/standard-participants/resolve")
    fun resolveStandardParticipant(
        @Valid @RequestBody reqDto: ResolveStandardParticipantReqDto,
    ): ResolveStandardParticipantResDto = resolveStandardParticipantService.execute(reqDto)

    @Operation(summary = "일반 참가자 이름 일괄 조회", description = "요청한 id를 모두 돌려주거나, 없는 id나 다른 박람회의 참가자가 있으면 404입니다.")
    @PostMapping("/standard-participants/names")
    fun getStandardParticipantNames(
        @Valid @RequestBody reqDto: GetStandardParticipantNamesReqDto,
    ): List<StandardParticipantNameResDto> = getStandardParticipantNamesService.execute(reqDto)

    @Operation(summary = "응답자 id 조회", description = "박람회 id, 전화번호, 응답자 구분(TRAINEE, STANDARD)으로 해당 구분의 참가자 id를 찾습니다. 없으면 404입니다.")
    @PostMapping("/participants/resolve")
    fun resolveParticipant(
        @Valid @RequestBody reqDto: ResolveParticipantReqDto,
    ): ResolveParticipantResDto = resolveParticipantService.execute(reqDto)

    @Operation(
        summary = "일반 참가자 등록",
        description = "새로 만들면 201, 같은 번호가 이미 있고 QR 문자를 두 번 미만 보냈으면 기존 참가자로 200, 두 번 이상 보냈으면 409입니다.",
    )
    @PostMapping("/standard-participants")
    fun createStandardParticipant(
        @Valid @RequestBody reqDto: CreateStandardParticipantReqDto,
    ): ResponseEntity<CreateStandardParticipantResDto> {
        val result = createStandardParticipantService.execute(reqDto)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result)
    }

    @Operation(summary = "QR 문자 발송 횟수 증가", description = "QR 문자를 보낸 뒤 일반 참가자의 발송 횟수를 1 올립니다. 참가자가 없으면 404, 연수자는 400입니다.")
    @PostMapping("/participants/sms-try")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun increaseSmsTryTime(
        @Valid @RequestBody reqDto: IncreaseSmsTryTimeReqDto,
    ) = increaseSmsTryTimeService.execute(reqDto)
}
