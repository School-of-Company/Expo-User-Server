package team.startup.expo.global.client.expo

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "expo-expo-server",
    url = "\${clients.expo.url:}",
    configuration = [ExpoClientConfiguration::class],
)
interface ExpoClient {
    @GetMapping("/internal/expo/{expoId}/admin-access")
    fun requireAdminAccess(
        @PathVariable("expoId") expoId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestParam("permission", required = false) permission: String?,
    )

    @PostMapping("/internal/staff-invites/exchange")
    fun exchangeStaffInvite(
        @RequestBody request: StaffInviteExchangeReqDto,
    ): StaffInviteExchangeResDto

    @GetMapping("/internal/expo/{expoId}")
    fun getPeriod(
        @PathVariable("expoId") expoId: String,
    ): ExpoPeriodResDto

    @GetMapping("/internal/expo/{expoId}/preregister-sessions/{sessionId}")
    fun getPreregisterSession(
        @PathVariable("expoId") expoId: String,
        @PathVariable("sessionId") sessionId: Long,
    ): PreregisterSessionResDto
}
