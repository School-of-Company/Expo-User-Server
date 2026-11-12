package team.startup.expo.domain.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank

class StaffAuthReqDto(
    @field:NotBlank val secret: String,
) {
    override fun toString() = "StaffAuthReqDto(secret=***)"
}
