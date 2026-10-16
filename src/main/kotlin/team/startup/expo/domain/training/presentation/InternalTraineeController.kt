package team.startup.expo.domain.training.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.request.GetTraineeNamesReqDto
import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeByParticipantReqDto
import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.request.ResolveTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.request.TraineeParticipantLinkReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeByParticipantResDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeNameResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeParticipantLinkResDto
import team.startup.expo.domain.training.service.GetTraineeNamesService
import team.startup.expo.domain.training.service.LinkTraineeParticipantService
import team.startup.expo.domain.training.service.RegisterTraineeService
import team.startup.expo.domain.training.service.ResolveOrCreateTraineeByParticipantService
import team.startup.expo.domain.training.service.ResolveOrCreateTraineeService
import team.startup.expo.domain.training.service.ResolveTraineeService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 연수 번호(`trainingId`)는 URL이나 쿼리에 남지 않도록 요청 본문으로만 받는다.
 */
@Tag(name = "내부: 연수자", description = "연수자 조회, 등록, 참가자와의 연결")
@SecurityRequirement(name = "internalToken")
@RestController
@RequestMapping("/internal/trainees")
class InternalTraineeController(
    private val resolveTraineeService: ResolveTraineeService,
    private val getTraineeNamesService: GetTraineeNamesService,
    private val registerTraineeService: RegisterTraineeService,
    private val resolveOrCreateTraineeService: ResolveOrCreateTraineeService,
    private val resolveOrCreateTraineeByParticipantService: ResolveOrCreateTraineeByParticipantService,
    private val linkTraineeParticipantService: LinkTraineeParticipantService,
) {
    @Operation(summary = "연수자 id 조회", description = "박람회 id와 연수 번호로 연수자 id를 찾습니다. 없으면 404, 같은 번호의 연수자가 여럿이면 409입니다.")
    @PostMapping("/resolve")
    fun resolveTrainee(
        @Valid @RequestBody reqDto: ResolveTraineeReqDto,
    ): ResolveTraineeResDto = resolveTraineeService.execute(reqDto)

    @Operation(summary = "연수자 이름 일괄 조회", description = "요청한 id를 모두 돌려주거나, 없는 id나 다른 박람회의 연수자가 있으면 404입니다.")
    @PostMapping("/names")
    fun getTraineeNames(
        @Valid @RequestBody reqDto: GetTraineeNamesReqDto,
    ): List<TraineeNameResDto> = getTraineeNamesService.execute(reqDto)

    @Operation(
        summary = "연수자 등록",
        description = "사전 등록은 연수 번호나 전화번호가 같은 연수자가, 현장 등록은 전화번호가 같은 연수자나 일반 참가자가 이미 있으면 409입니다.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTrainee(
        @Valid @RequestBody reqDto: CreateTraineeReqDto,
    ): CreateTraineeResDto = registerTraineeService.execute(reqDto)

    @Operation(
        summary = "연수자 조회 또는 생성",
        description = "박람회 id와 전화번호로 연수자를 찾아 있으면 그대로 돌려주고(created=false, 요청 값으로 덮어쓰지 않음) 없으면 사전 등록으로 만듭니다(created=true).",
    )
    @PostMapping("/resolve-or-create")
    fun resolveOrCreateTrainee(
        @Valid @RequestBody reqDto: ResolveOrCreateTraineeReqDto,
    ): ResolveOrCreateTraineeResDto = resolveOrCreateTraineeService.execute(reqDto)

    @Operation(
        summary = "사전등록 참가자에 이어진 연수자 조회 또는 생성",
        description =
            "참가자에 이어진 연수자가 있으면 그대로 돌려주고(created=false, 요청 값으로 덮어쓰지 않음) 없으면 한 번 만들어 잇습니다(created=true). " +
                "참가자가 없거나 다른 박람회의 참가자면 404, 삭제 중인 박람회면 409입니다.",
    )
    @PostMapping("/resolve-or-create-by-participant")
    fun resolveOrCreateTraineeByParticipant(
        @Valid @RequestBody reqDto: ResolveOrCreateTraineeByParticipantReqDto,
    ): ResolveOrCreateTraineeByParticipantResDto = resolveOrCreateTraineeByParticipantService.execute(reqDto)

    @Operation(
        summary = "레거시 연수자를 확인된 참가자에 연결",
        description =
            "운영자가 같은 사람으로 확인한 같은 박람회의 연수자와 참가자를 잇고 승인자와 근거를 감사 기록에 남깁니다. 이름이나 번호로 찾지 않고 " +
                "연수자·참가자를 만들거나 ID를 바꾸지 않으며 기존 신청·출석은 그대로입니다. 이미 그 연결이면 바꾸지 않고 changed=false입니다. " +
                "연수자가 다른 참가자에, 또는 참가자가 다른 연수자에 이미 이어져 있으면 409, actorAdminId가 관리자가 아니면 403, " +
                "없거나 다른 박람회의 연수자·참가자는 구분 없이 404, 삭제 중인 박람회면 409입니다.",
    )
    @PostMapping("/participant-link")
    fun linkTraineeParticipant(
        @Valid @RequestBody reqDto: TraineeParticipantLinkReqDto,
    ): TraineeParticipantLinkResDto = linkTraineeParticipantService.link(reqDto)

    @Operation(
        summary = "잘못 확인한 연수자·참가자 연결 해제",
        description =
            "이 연결 API로 이은 연결만 풀고 승인자와 근거를 감사 기록에 남깁니다. 기존 신청·출석은 옮기거나 지우지 않습니다. 이미 연결이 없으면 " +
                "changed=false입니다. 연수자가 다른 참가자에 이어져 있거나 참가자에서 만든 연수자의 연결이면 409, actorAdminId가 관리자가 아니면 403, " +
                "없거나 다른 박람회의 연수자는 404입니다. 다른 참가자로 바로잡을 때는 해제한 뒤 다시 연결합니다.",
    )
    @PostMapping("/participant-link/revoke")
    fun revokeTraineeParticipant(
        @Valid @RequestBody reqDto: TraineeParticipantLinkReqDto,
    ): TraineeParticipantLinkResDto = linkTraineeParticipantService.revoke(reqDto)
}
