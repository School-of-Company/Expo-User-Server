package team.startup.expo.domain.participation.service

/** 등록 한 건의 결과. [created]는 새로 만들었는지(201)와 기존 참가자를 돌려줬는지(200)를 가른다. */
data class Registration(
    val id: Long,
    val phoneNumber: String,
    val created: Boolean,
)
