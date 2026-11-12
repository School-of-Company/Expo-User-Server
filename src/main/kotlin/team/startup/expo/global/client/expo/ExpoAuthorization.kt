package team.startup.expo.global.client.expo

import feign.FeignException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import team.startup.expo.global.exception.ExpectedException

@Component
class ExpoAuthorization(
    private val client: ExpoClient,
) {
    fun requireAccess(
        expoId: String,
        authorization: String,
        permission: String?,
    ) = call { client.requireAdminAccess(expoId, authorization, permission) }

    fun exchange(request: StaffInviteExchangeReqDto): StaffInviteExchangeResDto = call { client.exchangeStaffInvite(request) }

    private fun <T> call(action: () -> T): T =
        try {
            action()
        } catch (e: Exception) {
            val status = (e as? FeignException)?.status()?.let(HttpStatus::resolve)
            if (status in listOf(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
                throw ExpectedException(requireNotNull(status), "박람회 접근 또는 스태프 링크가 유효하지 않습니다.")
            }
            throw ExpectedException(HttpStatus.SERVICE_UNAVAILABLE, "박람회 권한을 확인할 수 없습니다.")
        }
}
