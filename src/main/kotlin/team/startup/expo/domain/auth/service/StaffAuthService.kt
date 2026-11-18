package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.presentation.dto.request.StaffAuthReqDto
import team.startup.expo.domain.auth.presentation.dto.response.StaffTokenResDto

interface StaffAuthService {
    fun execute(request: StaffAuthReqDto): StaffTokenResDto
}
