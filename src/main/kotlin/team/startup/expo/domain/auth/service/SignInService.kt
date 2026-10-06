package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.presentation.dto.request.SignInReqDto
import team.startup.expo.domain.auth.presentation.dto.response.TokenResDto

interface SignInService {
    fun execute(reqDto: SignInReqDto): TokenResDto
}
