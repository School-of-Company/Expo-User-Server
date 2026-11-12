package team.startup.expo.domain.auth.service.impl

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import team.startup.expo.domain.auth.presentation.dto.request.StaffAuthReqDto
import team.startup.expo.domain.auth.presentation.dto.response.StaffTokenResDto
import team.startup.expo.domain.auth.service.StaffAuthService
import team.startup.expo.global.client.expo.ExpoAuthorization
import team.startup.expo.global.client.expo.StaffInviteExchangeReqDto
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.global.security.StaffPrincipal
import team.startup.expo.global.security.jwt.JwtProvider
import java.time.Instant

@Service
class StaffAuthServiceImpl(
    private val authorization: ExpoAuthorization,
    private val jwtProvider: JwtProvider,
) : StaffAuthService {
    override fun execute(request: StaffAuthReqDto): StaffTokenResDto {
        val invite = authorization.exchange(StaffInviteExchangeReqDto(request.secret))
        if (invite.inviteId <= 0 || invite.expoId.isBlank() || !invite.expiresAt.isAfter(Instant.now())) {
            throw ExpectedException(HttpStatus.UNAUTHORIZED, "유효하지 않은 스태프 링크입니다.")
        }
        val token = jwtProvider.generateStaffToken(StaffPrincipal(invite.inviteId, invite.expoId))
        return StaffTokenResDto(token.value, token.expiresAt)
    }
}
