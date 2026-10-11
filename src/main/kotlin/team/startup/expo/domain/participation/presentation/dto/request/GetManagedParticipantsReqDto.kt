package team.startup.expo.domain.participation.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 전화번호는 `URL`에 남지 않도록 본문으로만 받는다. 번호는 신청 서비스가 세션에서 얻은 검증된 값이다. */
data class GetManagedParticipantsReqDto(
    @field:NotBlank
    @field:Size(max = 36)
    val expoId: String,
    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,
) {
    override fun toString() = "GetManagedParticipantsReqDto(expoId=$expoId, phoneNumber=***)"
}
