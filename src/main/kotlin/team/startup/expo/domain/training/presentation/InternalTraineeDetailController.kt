package team.startup.expo.domain.training.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailItemResDto
import team.startup.expo.domain.training.presentation.dto.response.TraineeDetailResDto
import team.startup.expo.domain.training.service.GetTraineeDetailsService
import team.startup.expo.global.dto.DetailPageReqDto
import team.startup.expo.global.dto.DetailPageResDto

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 전화번호는 응답 본문으로만 주고 URL이나 로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/internal")
class InternalTraineeDetailController(
    private val getTraineeDetailsService: GetTraineeDetailsService,
) {
    @Operation(
        summary = "박람회 연수자 상세 조회",
        description = "박람회의 연수자를 id 오름차순으로 cursor 뒤부터 size개 돌려줍니다. 마지막 페이지면 nextCursor가 null이고, 연수자가 없으면 빈 목록입니다.",
    )
    @GetMapping("/expos/{expoId}/trainees/details")
    fun getTraineeDetails(
        @PathVariable("expoId") @Size(min = 1, max = 36) expoId: String,
        @Valid @ModelAttribute reqDto: DetailPageReqDto,
    ): DetailPageResDto<TraineeDetailItemResDto> = getTraineeDetailsService.page(expoId, reqDto)

    @Operation(summary = "연수자 단건 상세 조회", description = "연수자 한 명의 소속 박람회 id, 이름, 연수 번호, 신청 답변을 돌려줍니다. 없으면 404입니다.")
    @GetMapping("/trainees/{traineeId}/details")
    fun getTraineeDetail(
        @PathVariable("traineeId") traineeId: Long,
    ): TraineeDetailResDto = getTraineeDetailsService.one(traineeId)
}
