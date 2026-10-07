package team.startup.expo.domain.participation.entity

/**
 * 일반 참가자의 직업(유형). 사전등록 공식 요구사항의 참여자 유형(유, 초, 중고, 일반, 교사, 예비교사)이며 Form 서비스의
 * `Occupation`과 같은 값을 쓴다. 이름이 다르면 신청 요청이 `400`이 되므로 Form과 함께 바꾼다.
 *
 * 컬럼은 `VARCHAR`로 이름을 그대로 저장한다. 값을 바꾸면 기존 행도 함께 옮겨야 한다(`V10` 참고).
 */
enum class Occupation {
    /** 유 — 유치원생 */
    KINDERGARTEN_STUDENT,

    /** 초 — 초등학생 */
    ELEMENTARY_STUDENT,

    /** 중고 — 중학생과 고등학생 */
    MIDDLE_HIGH_SCHOOL_STUDENT,

    /** 일반 — 학부모, 교직원 등 그 밖의 참가자 */
    GENERAL,

    /** 교사 — 소속을 입력한다. */
    TEACHER,

    /** 예비교사 — 소속을 입력한다. */
    PRE_SERVICE_TEACHER,
}
