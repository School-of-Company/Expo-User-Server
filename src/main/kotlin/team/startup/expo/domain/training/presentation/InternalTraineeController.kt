package team.startup.expo.domain.training.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.training.presentation.dto.request.CreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.request.GetTraineeNamesReqDto
import team.startup.expo.domain.training.presentation.dto.request.ResolveOrCreateTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.request.ResolveTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.CreateTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveOrCreateTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.ResolveTraineeResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeNameResDto
import team.startup.expo.domain.training.service.GetTraineeNamesService
import team.startup.expo.domain.training.service.RegisterTraineeService
import team.startup.expo.domain.training.service.ResolveOrCreateTraineeService
import team.startup.expo.domain.training.service.ResolveTraineeService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 연수 번호(`trainingId`)는 URL이나 쿼리에 남지 않도록 요청 본문으로만 받는다.
 */
@RestController
@RequestMapping("/internal/trainees")
class InternalTraineeController(
    private val resolveTraineeService: ResolveTraineeService,
    private val getTraineeNamesService: GetTraineeNamesService,
    private val registerTraineeService: RegisterTraineeService,
    private val resolveOrCreateTraineeService: ResolveOrCreateTraineeService,
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
}
