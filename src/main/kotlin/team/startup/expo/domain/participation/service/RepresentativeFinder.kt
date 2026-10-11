package team.startup.expo.domain.participation.service

import org.springframework.stereotype.Component
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.global.util.PhoneNumbers

/**
 * 행사와 검증된 전화번호로 대표자를 찾는다. 번호를 가진 사람은 대표자뿐이고(동행자는 번호가 없다), 저장된 표기와 정확히
 * 같은 번호를 먼저 찾고 없을 때만 숫자만 남겨 비교한다. 표기만 다른 후보가 여럿이면 하나로 특정할 수 없어 `409`로 실패한다.
 */
@Component
class RepresentativeFinder(
    private val standardParticipantRepository: StandardParticipantRepository,
) {
    fun find(
        expoId: String,
        phoneNumber: String,
    ): StandardParticipant? {
        val digits = PhoneNumbers.digitsOnly(phoneNumber)
        val found =
            standardParticipantRepository.findByExpoIdAndPhoneNumber(expoId, phoneNumber)
                ?: PhoneNumbers.select(standardParticipantRepository.findAllByExpoIdAndDigits(expoId, digits), phoneNumber) {
                    it.phoneNumber
                }
        return found?.takeIf { it.representativeId == null }
    }
}
