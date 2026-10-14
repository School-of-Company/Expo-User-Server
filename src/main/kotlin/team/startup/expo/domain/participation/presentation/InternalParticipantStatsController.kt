package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantStatsResDto
import team.startup.expo.domain.participation.service.GetParticipantStatsService

@Tag(name = "내부: 참가자 집계", description = "신청 서비스가 관리자 화면에 쓰는 박람회별 참가자 집계")
@SecurityRequirement(name = "internalToken")
@Validated
@RestController
@RequestMapping("/internal")
class InternalParticipantStatsController(
    private val getParticipantStatsService: GetParticipantStatsService,
) {
    @Operation(
        summary = "박람회별 참가자 집계",
        description =
            "전화 인증을 마친 고유 번호 수(verifiedPhoneCount)와 등록한 고유 참가자 수(registeredParticipantCount)를 돌려줍니다. " +
                "같은 번호의 재인증은 한 번만 세고, 전화 인증 기록을 시작한 이후의 인증만 셉니다. 번호 원문은 저장하지 않고 응답에도 " +
                "싣지 않습니다. 박람회 존재와 관리자 권한은 호출하는 쪽이 확인하며, 없는 박람회는 0건입니다.",
    )
    @GetMapping("/expos/{expoId}/participant-stats")
    fun getParticipantStats(
        @PathVariable("expoId") @Size(min = 1, max = 36) expoId: String,
    ): ParticipantStatsResDto = getParticipantStatsService.execute(expoId)
}
