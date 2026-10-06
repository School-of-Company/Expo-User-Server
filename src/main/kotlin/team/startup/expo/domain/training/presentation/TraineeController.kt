package team.startup.expo.domain.training.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.training.presentation.dto.request.GetTraineeReqDto
import team.startup.expo.domain.training.presentation.dto.response.GetTraineeResDto
import team.startup.expo.domain.training.service.GetTraineesService

@RestController
@RequestMapping("/trainee")
class TraineeController(
    private val getTraineesService: GetTraineesService,
) {
    @Operation(summary = "연수자 목록 조회", description = "박람회에 신청한 연수자 전체를 조회합니다. name을 주면 이름에 포함된 연수자만 조회합니다.")
    @GetMapping("/{expo_id}")
    fun getTrainees(
        @PathVariable("expo_id") expoId: String,
        @Valid @ModelAttribute reqDto: GetTraineeReqDto,
    ): List<GetTraineeResDto> = getTraineesService.execute(expoId, reqDto)
}
