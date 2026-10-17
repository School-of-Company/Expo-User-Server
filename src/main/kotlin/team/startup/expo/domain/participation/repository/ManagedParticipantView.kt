package team.startup.expo.domain.participation.repository

import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.training.entity.ApplicationType

/** 대표자가 관리하는 참가자 목록에 필요한 값만 읽는다. 전화번호, 개인 `code`, `information_json`은 읽지 않는다. */
interface ManagedParticipantView {
    val id: Long
    val name: String
    val occupation: Occupation?
    val applicationType: ApplicationType
    val representativeId: Long?
}
