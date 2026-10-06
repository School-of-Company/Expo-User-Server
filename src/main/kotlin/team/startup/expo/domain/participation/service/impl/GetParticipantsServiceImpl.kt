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
import team.startup.expo.global.exception.ExpectedException
import java.time.LocalDate
import java.time.ZoneId

@Service
class GetParticipantsServiceImpl(
    private val participationRepository: StandardParticipantParticipationRepository,
    private val expoPeriodReader: ExpoPeriodReader,
) : GetParticipantsService {
    /**
     * v1과 같게 해당 날짜에 입장한 참가자만 보여 준다. 박람회가 없으면 404, 날짜가 박람회 기간 밖이면 400이다.
     * 날짜를 주지 않으면 오늘(한국 시간)이다. 서버 시간대에 따라 자정 전후로 하루가 어긋나지 않게 고정한다.
     */
    @Transactional(readOnly = true)
    override fun execute(
        expoId: String,
        reqDto: GetParticipantReqDto,
    ): GetParticipantResDto {
        val period =
            expoPeriodReader.find(expoId)
                ?: throw ExpectedException(HttpStatus.NOT_FOUND, "박람회를 찾지 못 했습니다.")

        val date = reqDto.date ?: LocalDate.now(SEOUL)
        if (date !in period) {
            throw ExpectedException(HttpStatus.BAD_REQUEST, "요청한 날짜가 박람회 기간 밖에 있습니다.")
        }

        val pageable = PageRequest.of(reqDto.page ?: 0, reqDto.size ?: GetParticipantReqDto.DEFAULT_SIZE)
        val attendees = participationRepository.findAttendees(expoId, date, pageable)
        return GetParticipantResDto(
            info = ParticipantPageInfoResDto(totalPage = attendees.totalPages, totalElement = attendees.totalElements.toInt()),
            participants = attendees.content,
        )
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
