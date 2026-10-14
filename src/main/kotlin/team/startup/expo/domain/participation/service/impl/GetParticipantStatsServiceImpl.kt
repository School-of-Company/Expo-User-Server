package team.startup.expo.domain.participation.service.impl

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.startup.expo.domain.participation.presentation.dto.response.ParticipantStatsResDto
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.GetParticipantStatsService

/**
 * 관리자 화면의 박람회별 참가자 집계다. 박람회 존재 여부와 관리자 권한은 호출하는 신청 서비스가 확인하므로, 없는 박람회는
 * 오류가 아니라 0건이다. 아무것도 만들거나 기록하지 않는다.
 */
@Service
class GetParticipantStatsServiceImpl(
    private val jdbcTemplate: JdbcTemplate,
    private val standardParticipantRepository: StandardParticipantRepository,
) : GetParticipantStatsService {
    @Transactional(readOnly = true)
    override fun execute(expoId: String): ParticipantStatsResDto =
        ParticipantStatsResDto(
            expoId = expoId,
            verifiedPhoneCount =
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM tb_participant_phone_verification WHERE expo_id = ?",
                    Long::class.java,
                    expoId,
                ) ?: 0L,
            registeredParticipantCount = standardParticipantRepository.countByExpoId(expoId),
        )
}
