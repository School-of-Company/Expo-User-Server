package team.startup.expo.domain.participation.service

import team.startup.expo.domain.participation.presentation.dto.request.QrSmsSentEvent

interface RecordQrSmsSentService {
    fun execute(event: QrSmsSentEvent)
}
