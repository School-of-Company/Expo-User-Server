package team.startup.expo.domain.user.presentation

import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.user.presentation.dto.response.GetMyInformationResDto
import team.startup.expo.domain.user.presentation.dto.response.GetPendingAdminResDto
import team.startup.expo.domain.user.service.GetMyInformationService
import team.startup.expo.domain.user.service.GetPendingAdminsService
import team.startup.expo.global.security.AdminPrincipal

@RestController
@RequestMapping("/admin")
class AdminController(
    private val getMyInformationService: GetMyInformationService,
    private val getPendingAdminsService: GetPendingAdminsService,
) {
    @Operation(summary = "내 정보 조회")
    @GetMapping("/my")
    fun getMyInformation(
        @AuthenticationPrincipal principal: AdminPrincipal,
    ): GetMyInformationResDto = getMyInformationService.execute(principal.id)

    @Operation(summary = "승인 대기 관리자 목록 조회")
    @GetMapping
    fun getPendingAdmins(): List<GetPendingAdminResDto> = getPendingAdminsService.execute()
}
