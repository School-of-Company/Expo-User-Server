package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.GetStandardParticipantBriefsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.StandardParticipantDetailPageReqDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantBriefResDto
import team.startup.expo.domain.participation.presentation.dto.response.StandardParticipantDetailResDto
import team.startup.expo.domain.participation.service.GetStandardParticipantDetailsService
import team.startup.expo.global.dto.DetailPageResDto

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 전화번호는 응답 본문으로만 주고 URL이나 로그에 남기지 않는다.
 */
@Tag(name = "내부: 참가자 상세(Report)", description = "리포트용 참가자 상세 조회")
@SecurityRequirement(name = "internalToken")
@RestController
@RequestMapping("/internal")
class InternalParticipantDetailController(
    private val getStandardParticipantDetailsService: GetStandardParticipantDetailsService,
) {
    @Operation(
        summary = "박람회 일반 참가자 상세 조회",
        description =
            "박람회의 일반 참가자를 id 오름차순으로 cursor 뒤부터 size개 돌려줍니다. 마지막 페이지면 nextCursor가 null이고, 참가자가 없으면 빈 목록입니다. " +
                "q는 이름의 대소문자 무시 부분 일치이고 숫자와 하이픈뿐이면 전화번호(동행자는 대표 수신 번호)도 하이픈을 뺀 부분 일치로 찾습니다. " +
                "occupation은 저장된 직업과 정확히 같은 참가자만 남깁니다. 필터를 써도 id 오름차순 커서와 최대 500건 규칙은 같습니다.",
    )
    @GetMapping("/expos/{expoId}/standard-participants/details")
    fun getStandardParticipantDetails(
        @PathVariable("expoId") @Size(min = 1, max = 36) expoId: String,
        @ParameterObject @Valid @ModelAttribute reqDto: StandardParticipantDetailPageReqDto,
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
