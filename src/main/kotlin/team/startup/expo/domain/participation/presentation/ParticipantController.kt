package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetParticipantResDto
import team.startup.expo.domain.participation.service.GetParticipantsService

@Tag(name = "참가자 목록(관리자)", description = "박람회별 일반 참가자 조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/participant")
class ParticipantController(
    private val getParticipantsService: GetParticipantsService,
) {
    @Operation(
        summary = "박람회 신청 관람객 조회",
        description =
            "date에 입장한 일반 참가자를 페이지 단위로 조회합니다. date를 주지 않으면 오늘이고, 박람회 기간 밖이면 400입니다. " +
                "sessionId(사전등록 회차 ID)를 주면 date 대신 그 회차로 입장한 참가자를 조회하며 date는 무시합니다. " +
                "회차가 없거나 다른 박람회의 회차이면 404입니다. 회차 연결은 이 기능 배포 이후의 입장부터 남습니다. " +
                "withoutSession=true를 주면 date에 회차 없이 입장한 참가자(현장등록 입장 등)만 조회하며, sessionId와 함께 주면 400입니다.",
    )
    @GetMapping("/{expo_id}")
    fun getParticipants(
        @PathVariable("expo_id") expoId: String,
        @ParameterObject @Valid @ModelAttribute reqDto: GetParticipantReqDto,
    ): GetParticipantResDto = getParticipantsService.execute(expoId, reqDto)
}
