package team.startup.expo.domain.auth.service

import team.startup.expo.domain.auth.presentation.dto.request.SignUpReqDto

interface SignUpService {
    fun execute(reqDto: SignUpReqDto)
}
