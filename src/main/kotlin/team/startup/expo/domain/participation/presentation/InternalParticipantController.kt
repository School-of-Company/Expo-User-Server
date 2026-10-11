package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.CreateStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantNamesReqDto
import team.startup.expo.domain.participation.presentation.dto.request.IncreaseSmsTryTimeReqDto
import team.startup.expo.domain.participation.presentation.dto.request.RecordEntryReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ResolveTraineeByParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.request.SelectManagedParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.VerifyStandardParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.CreateStandardParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.GetManagedParticipantsResDto
import team.startup.expo.domain.participation.presentation.dto.response.GetSurveyAnswerEventResDto
import team.startup.expo.domain.participation.presentation.dto.response.ManagedParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.RecordEntryResDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveStandardParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.ResolveTraineeByParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantNameResDto
import team.startup.expo.domain.participation.service.GetManagedParticipantsService
import team.startup.expo.domain.participation.service.GetStandardParticipantNamesService
import team.startup.expo.domain.participation.service.GetSurveyAnswerEventService
import team.startup.expo.domain.participation.service.IncreaseSmsTryTimeService
import team.startup.expo.domain.participation.service.RecordEntryService
import team.startup.expo.domain.participation.service.RegisterStandardParticipantService
import team.startup.expo.domain.participation.service.ResolveParticipantService
import team.startup.expo.domain.participation.service.ResolveStandardParticipantService
import team.startup.expo.domain.participation.service.ResolveTraineeByParticipantService
import team.startup.expo.domain.participation.service.SelectManagedParticipantsService
import team.startup.expo.domain.participation.service.VerifyStandardParticipantService

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
    private val getSurveyAnswerEventService: GetSurveyAnswerEventService,
    private val registerStandardParticipantService: RegisterStandardParticipantService,
    private val increaseSmsTryTimeService: IncreaseSmsTryTimeService,
    private val recordEntryService: RecordEntryService,
    private val verifyStandardParticipantService: VerifyStandardParticipantService,
    private val resolveTraineeByParticipantService: ResolveTraineeByParticipantService,
    private val getManagedParticipantsService: GetManagedParticipantsService,
    private val selectManagedParticipantsService: SelectManagedParticipantsService,
) {
    @Operation(summary = "일반 참가자 id 조회", description = "박람회 id와 전화번호로 일반 참가자 id를 찾습니다. 없으면 404입니다.")
    @PostMapping("/standard-participants/resolve")
    fun resolveStandardParticipant(
        @Valid @RequestBody reqDto: ResolveStandardParticipantReqDto,
    ): ResolveStandardParticipantResDto = resolveStandardParticipantService.execute(reqDto)

    @Operation(
        summary = "일반 참가자 QR 확인",
        description =
            "박람회 id, 참가자 id, code가 맞는지만 확인합니다. 맞으면 204이고 입장·출석은 기록하지 않습니다. " +
                "참가자가 없거나 다른 박람회의 참가자이거나 code가 다르면 입장 기록과 같은 404입니다.",
    )
    @PostMapping("/standard-participants/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verifyStandardParticipant(
        @Valid @RequestBody reqDto: VerifyStandardParticipantReqDto,
    ) = verifyStandardParticipantService.execute(reqDto)

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

    @Operation(summary = "설문 답변 이벤트 처리 결과 조회", description = "eventId로 처리를 마친 이벤트의 결과(STORED, REJECTED와 사유)를 돌려줍니다. 처리한 적 없으면 404입니다.")
    @GetMapping("/survey-answer-events/{eventId}")
    fun getSurveyAnswerEvent(
        @PathVariable eventId: String,
    ): GetSurveyAnswerEventResDto = getSurveyAnswerEventService.execute(eventId)

    @Operation(
        summary = "일반 참가자 등록",
        description =
            "새로 만들면 201, 같은 번호가 이미 있고 QR 문자를 두 번 미만 보냈으면 기존 참가자로 200, 두 번 이상 보냈으면 409입니다. " +
                "같은 requestId의 재시도는 처음의 결과를 그대로 돌려주고, 다른 내용에 쓰면 409입니다.",
    )
    @PostMapping("/standard-participants")
    fun createStandardParticipant(
        @Valid @RequestBody reqDto: CreateStandardParticipantReqDto,
    ): ResponseEntity<CreateStandardParticipantResDto> {
        val result = registerStandardParticipantService.execute(reqDto)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result)
    }

    @Operation(
        summary = "QR 문자 발송 횟수 증가",
        description =
            "QR 문자를 보낸 뒤 일반 참가자의 발송 횟수를 1 올립니다. eventId가 있으면 같은 값은 한 번만 올리며 다시 불러도 204입니다. " +
                "참가자가 없으면 404, 연수자는 400, eventId를 다른 참가자에 썼으면 409입니다.",
    )
    @PostMapping("/participants/sms-try")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun increaseSmsTryTime(
        @Valid @RequestBody reqDto: IncreaseSmsTryTimeReqDto,
    ) = increaseSmsTryTimeService.execute(reqDto)

    @Operation(
        summary = "박람회 입장 기록",
        description = "박람회 id, 전화번호, 응답자 구분으로 참가자를 찾아 오늘의 입장을 기록하고 참가자 정보를 돌려줍니다. 참가자가 없으면 404, 오늘 이미 입장했으면 409입니다.",
    )
    @PostMapping("/entries")
    fun recordEntry(
        @Valid @RequestBody reqDto: RecordEntryReqDto,
    ): RecordEntryResDto = recordEntryService.execute(reqDto)

    @Operation(
        summary = "대표자가 관리하는 참가자 목록",
        description =
            "행사와 검증된 전화번호의 대표자 본인과 동행자를 돌려줍니다. 이 번호로 등록된 대표자가 없으면 registered=false입니다. " +
                "전화번호와 QR code는 싣지 않습니다.",
    )
    @PostMapping("/standard-participants/managed")
    fun getManagedParticipants(
        @Valid @RequestBody reqDto: GetManagedParticipantsReqDto,
    ): GetManagedParticipantsResDto = getManagedParticipantsService.execute(reqDto)

    @Operation(
        summary = "선택한 참가자의 관리 권한 확인",
        description =
            "요청한 참가자가 모두 그 번호의 대표자 본인이나 동행자이면 저장된 값을 요청 순서대로 돌려줍니다. " +
                "하나라도 아니면(다른 대표자, 다른 행사, 없는 id, 등록 안 된 번호) 구분하지 않고 404입니다.",
    )
    @PostMapping("/standard-participants/managed/select")
    fun selectManagedParticipants(
        @Valid @RequestBody reqDto: SelectManagedParticipantsReqDto,
    ): List<ManagedParticipantResDto> = selectManagedParticipantsService.execute(reqDto)

    @Operation(
        summary = "참가자에 이어진 연수자 조회",
        description =
            "QR의 participantId와 code가 맞고 그 참가자에 이어진 연수자가 있으면 traineeId를 돌려줍니다. 입장이나 출석을 기록하지 않고 " +
                "상태를 바꾸지 않으며 연결이 없어도 새로 만들지 않습니다. 참가자가 없거나, 다른 박람회이거나, code가 다르거나, " +
                "연결이 없으면 구분하지 않고 404입니다.",
    )
    @PostMapping("/standard-participants/trainee")
    fun resolveTraineeByParticipant(
        @Valid @RequestBody reqDto: ResolveTraineeByParticipantReqDto,
    ): ResolveTraineeByParticipantResDto = resolveTraineeByParticipantService.execute(reqDto)
}
