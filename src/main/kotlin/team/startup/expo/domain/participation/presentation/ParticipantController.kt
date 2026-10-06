package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetParticipantResDto
import team.startup.expo.domain.participation.service.GetParticipantsService

@RestController
@RequestMapping("/participant")
class ParticipantController(
    private val getParticipantsService: GetParticipantsService,
) {
    @Operation(summary = "박람회 신청 관람객 조회", description = "date에 입장한 일반 참가자를 페이지 단위로 조회합니다. date를 주지 않으면 오늘입니다.")
    @GetMapping("/{expo_id}")
    fun getParticipants(
        @PathVariable("expo_id") expoId: String,
        @Valid @ModelAttribute reqDto: GetParticipantReqDto,
    ): GetParticipantResDto = getParticipantsService.execute(expoId, reqDto)
}
