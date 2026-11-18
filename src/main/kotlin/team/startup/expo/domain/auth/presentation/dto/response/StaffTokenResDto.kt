package team.startup.expo.domain.auth.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

class StaffTokenResDto(
    val accessToken: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val accessTokenExpiresIn: LocalDateTime,
) {
    override fun toString() = "StaffTokenResDto(accessToken=***, accessTokenExpiresIn=$accessTokenExpiresIn)"
}
