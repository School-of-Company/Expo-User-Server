package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantBriefsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantBriefResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantDetailResDto
import team.startup.expo.domain.participation.service.GetStandardParticipantDetailsService
import team.startup.expo.global.dto.DetailPageReqDto
import team.startup.expo.global.dto.DetailPageResDto

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 전화번호는 응답 본문으로만 주고 URL이나 로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/internal")
class InternalParticipantDetailController(
    private val getStandardParticipantDetailsService: GetStandardParticipantDetailsService,
) {
    @Operation(
        summary = "박람회 일반 참가자 상세 조회",
        description = "박람회의 일반 참가자를 id 오름차순으로 cursor 뒤부터 size개 돌려줍니다. 마지막 페이지면 nextCursor가 null이고, 참가자가 없으면 빈 목록입니다.",
    )
    @GetMapping("/expos/{expoId}/standard-participants/details")
    fun getStandardParticipantDetails(
        @PathVariable("expoId") @Size(min = 1, max = 36) expoId: String,
        @Valid @ModelAttribute reqDto: DetailPageReqDto,
    ): DetailPageResDto<StandardParticipantDetailResDto> = getStandardParticipantDetailsService.page(expoId, reqDto)

    @Operation(
        summary = "일반 참가자 일괄 상세 조회",
        description = "요청한 id를 요청 순서대로 모두 돌려주거나, 없는 id나 다른 박람회의 참가자가 있으면 404입니다.",
    )
    @PostMapping("/standard-participants/details")
    fun getStandardParticipantBriefs(
        @Valid @RequestBody reqDto: GetStandardParticipantBriefsReqDto,
    ): List<StandardParticipantBriefResDto> = getStandardParticipantDetailsService.briefs(reqDto)
}
