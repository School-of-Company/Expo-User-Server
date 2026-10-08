package team.startup.expo.domain.user.presentation

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.user.presentation.dto.response.GetMyInformationResDto
import team.startup.expo.domain.user.presentation.dto.response.GetPendingAdminResDto
import team.startup.expo.domain.user.service.AcceptAdminService
import team.startup.expo.domain.user.service.GetMyInformationService
import team.startup.expo.domain.user.service.GetPendingAdminsService
import team.startup.expo.domain.user.service.RefuseAdminService
import team.startup.expo.domain.user.service.WithdrawalAdminService
import team.startup.expo.global.security.AdminPrincipal

@RestController
@RequestMapping("/admin")
class AdminController(
    private val getMyInformationService: GetMyInformationService,
    private val getPendingAdminsService: GetPendingAdminsService,
    private val acceptAdminService: AcceptAdminService,
    private val refuseAdminService: RefuseAdminService,
    private val withdrawalAdminService: WithdrawalAdminService,
) {
    @Operation(summary = "내 정보 조회")
    @GetMapping("/my")
    fun getMyInformation(
        @AuthenticationPrincipal principal: AdminPrincipal,
    ): GetMyInformationResDto = getMyInformationService.execute(principal.id)

    @Operation(summary = "승인 대기 관리자 목록 조회")
    @GetMapping
    fun getPendingAdmins(): List<GetPendingAdminResDto> = getPendingAdminsService.execute()

    @Operation(summary = "관리자 승인", description = "승인 대기 관리자를 승인합니다. 승인되면 로그인할 수 있습니다.")
    @PatchMapping("/{admin_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptAdmin(
        @PathVariable("admin_id") adminId: Long,
    ) {
        acceptAdminService.execute(adminId)
    }

    @Operation(summary = "관리자 가입 거절", description = "승인 대기 관리자를 삭제합니다. 이미 승인된 관리자는 409입니다.")
    @DeleteMapping("/{admin_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun refuseAdmin(
        @PathVariable("admin_id") adminId: Long,
    ) {
        refuseAdminService.execute(adminId)
    }

    @Operation(summary = "본인 탈퇴", description = "요청한 관리자 본인을 삭제하고 저장된 refresh token을 폐기합니다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdrawal(
        @AuthenticationPrincipal principal: AdminPrincipal,
    ) {
        withdrawalAdminService.execute(principal.id)
    }
}
