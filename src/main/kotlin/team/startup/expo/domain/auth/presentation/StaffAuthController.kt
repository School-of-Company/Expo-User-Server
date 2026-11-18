package team.startup.expo.domain.auth.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.auth.presentation.dto.request.StaffAuthReqDto
import team.startup.expo.domain.auth.presentation.dto.response.StaffTokenResDto
import team.startup.expo.domain.auth.service.StaffAuthService

@RestController
class StaffAuthController(
    private val service: StaffAuthService,
) {
    @Operation(summary = "스태프 토큰 발급", description = "스태프 링크 비밀값으로 15분 access token을 발급합니다. 새로고침 토큰은 없습니다.")
    @PostMapping("/staff-auth")
    fun exchange(
        @Valid @RequestBody request: StaffAuthReqDto,
    ): StaffTokenResDto = service.execute(request)
}
