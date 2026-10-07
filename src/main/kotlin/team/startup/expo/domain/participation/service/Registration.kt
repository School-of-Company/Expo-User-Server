package team.startup.expo.domain.participation.service

/** 등록 한 건의 결과. [created]는 새로 만들었는지(201)와 기존 참가자를 돌려줬는지(200)를 가르고, [participantIds]는 이번 신청의 문자에 담은 참가자 ID다. */
data class Registration(
    val id: Long,
    val phoneNumber: String,
    val created: Boolean,
    val participantIds: List<Long> = emptyList(),
)
