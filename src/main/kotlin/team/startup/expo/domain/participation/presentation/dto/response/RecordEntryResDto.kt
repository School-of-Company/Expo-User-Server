package team.startup.expo.domain.participation.presentation.dto.response

import team.startup.expo.domain.participation.entity.Occupation
import team.startup.expo.domain.participation.entity.ParticipationType

/** 연수자는 `occupation`이 없고(연수자는 모두 교사) `school`만 있다. */
data class RecordEntryResDto(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val personalInformationStatus: Boolean,
    val participationType: ParticipationType,
    val occupation: Occupation?,
    val school: String?,
)
