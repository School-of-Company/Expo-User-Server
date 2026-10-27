package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryCardsReqDto
import team.startup.expo.domain.participation.presentation.dto.response.EntryCardsResDto
import team.startup.expo.domain.participation.service.GetEntryCardsService

/**
 * 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다.
 * 전화번호는 응답 본문으로만 주고 URL이나 로그에 남기지 않는다.
 */
@Tag(name = "내부: 입장 목록(Attendance)", description = "입구 화면의 입장 목록용 참가자·연수자 카드 조회")
@SecurityRequirement(name = "internalToken")
@RestController
@RequestMapping("/internal/participants")
class InternalEntryCardController(
    private val getEntryCardsService: GetEntryCardsService,
) {
    @Operation(
        summary = "입장 목록용 참가자·연수자 카드 일괄 조회",
        description =
            "일반 참가자와 연수자의 이름, 전화번호, 개인정보 동의 여부, 직업(일반 참가자만), 학교를 요청 순서대로 돌려줍니다. 목록마다 최대 1,000개입니다. " +
                "없는 id와 다른 박람회의 id는 404 없이 결과에서 빠지고, 중복 id는 한 번만 돌려줍니다. 아무것도 기록하지 않습니다.",
    )
    @PostMapping("/entry-cards")
    fun getEntryCards(
        @Valid @RequestBody reqDto: GetEntryCardsReqDto,
    ): EntryCardsResDto = getEntryCardsService.execute(reqDto)
}
