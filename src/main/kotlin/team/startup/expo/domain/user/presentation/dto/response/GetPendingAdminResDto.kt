package team.startup.expo.domain.user.presentation.dto.response

data class GetPendingAdminResDto(
    val id: Long,
    val name: String,
    val nickname: String,
    val email: String,
    val phoneNumber: String,
)
