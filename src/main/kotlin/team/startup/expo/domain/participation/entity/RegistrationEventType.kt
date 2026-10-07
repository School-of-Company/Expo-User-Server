package team.startup.expo.domain.participation.entity

/** 아웃박스 이벤트 종류. 종류마다 발행하는 토픽이 다르다. */
enum class RegistrationEventType {
    /** 등록 완료. QR 문자를 보낼 대상이 생겼다는 신호이며 Notification이 소비한다. */
    REGISTERED,

    /** 일반 참가자 신규 저장. Expo의 신청 인원 집계 누락을 재처리하는 장애 복구용이다. */
    STANDARD_CREATED,
}
