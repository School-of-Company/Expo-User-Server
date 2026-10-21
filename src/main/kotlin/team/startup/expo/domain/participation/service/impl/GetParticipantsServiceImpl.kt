package team.startup.expo.domain.participation.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.request.GetParticipantReqDto
import team.startup.expo.domain.participation.presentation.dto.response.GetParticipantResDto
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantPageInfoResDto
import team.startup.expo.domain.participation.repository.StandardParticipantParticipationRepository
import team.startup.expo.domain.participation.service.GetParticipantsService
import team.startup.expo.global.client.expo.ExpoPeriodReader
import team.startup.expo.global.client.expo.PreregisterSessionReader
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.ZoneId

@Service
class GetParticipantsServiceImpl(
    private val participationRepository: StandardParticipantParticipationRepository,
    private val expoPeriodReader: ExpoPeriodReader,
    private val preregisterSessionReader: PreregisterSessionReader,
) : GetParticipantsService {
    /**
     * v1과 같게 해당 날짜에 입장한 참가자만 보여 준다. 박람회가 없으면 404, 날짜가 박람회 기간 밖이면 400이다.
     * 날짜를 주지 않으면 오늘(한국 시간)이다. 서버 시간대에 따라 자정 전후로 하루가 어긋나지 않게 고정한다.
     *
     * `sessionId`를 주면 날짜 대신 그 사전등록 회차로 입장한 참가자를 보여 주고 `date`는 무시한다. 없는 회차나 다른 박람회의
     * 회차는 404이다. 회차 연결은 V19 이후 입장부터 남으므로 그 전 입장은 회차 목록에 나오지 않는다.
     *
     * `withoutSession=true`면 그 날짜에 회차 없이 입장한 참가자만 보여 준다. `sessionId`와 함께 주면 400이다.
     */
    @Transactional(readOnly = true)
    override fun execute(
        expoId: String,
        reqDto: GetParticipantReqDto,
    ): GetParticipantResDto {
        val period =
            expoPeriodReader.find(expoId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")

        if (reqDto.sessionId != null && reqDto.withoutSession == true) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "sessionId와 withoutSession은 함께 줄 수 없습니다.")
        }
        val pageable = PageRequest.of(reqDto.page ?: 0, reqDto.size ?: GetParticipantReqDto.DEFAULT_SIZE)
        val attendees =
            if (reqDto.sessionId != null) {
                if (!preregisterSessionReader.exists(expoId, reqDto.sessionId)) {
                    throw ExpectedException(HttpStatus.NOT_FOUND, "회차를 찾지 못 했습니다.")
                }
                participationRepository.findSessionAttendees(expoId, reqDto.sessionId, pageable)
            } else {
                val date = reqDto.date ?: LocalDate.now(SEOUL)
                if (date !in period) {
                    throw ExpectedException(HttpStatus.BAD_REQUEST, "요청한 날짜가 박람회 기간 밖에 있습니다.")
                }
                if (reqDto.withoutSession == true) {
                    participationRepository.findSessionlessAttendees(expoId, date, pageable)
                } else {
                    participationRepository.findAttendees(expoId, date, pageable)
                }
            }
        return GetParticipantResDto(
            info = ParticipantPageInfoResDto(totalPage = attendees.totalPages, totalElement = attendees.totalElements.toInt()),
            participants = attendees.content,
        )
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
