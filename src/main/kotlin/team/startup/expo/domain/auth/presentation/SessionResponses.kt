package team.startup.expo.domain.auth.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import team.startup.expo.domain.auth.model.ParticipantSessionLookup
import team.startup.expo.domain.auth.presentation.dto.response.SessionErrorResDto

/** 세션이 없거나 만료된 조회를 `code`가 있는 401로 바꾼다. 클라이언트가 인증 화면과 만료 안내를 가르는 데 쓴다. */
internal object SessionResponses {
    fun error(lookup: ParticipantSessionLookup): ResponseEntity<SessionErrorResDto> {
        val expired = lookup == ParticipantSessionLookup.Expired
        val code = if (expired) SessionErrorResDto.SESSION_EXPIRED else SessionErrorResDto.SESSION_REQUIRED
        val message = if (expired) "인증 세션이 만료되었습니다." else "전화 인증이 필요합니다."
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SessionErrorResDto(HttpStatus.UNAUTHORIZED.value(), code, message))
    }
}
