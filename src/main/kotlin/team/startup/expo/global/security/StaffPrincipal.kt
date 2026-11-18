package team.startup.expo.global.security

data class StaffPrincipal(
    val inviteId: Long,
    val expoId: String,
) {
    companion object {
        const val AUTHORITY = "ROLE_STAFF"
    }
}
