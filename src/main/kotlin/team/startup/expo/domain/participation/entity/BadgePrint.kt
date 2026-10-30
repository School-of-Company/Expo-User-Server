package team.startup.expo.domain.participation.entity

/** 입장 응답의 `badgePrint`. 대상이 아니면 `null`이다. */
enum class BadgePrintResult {
    /** 이번 입장으로 출력 작업을 저장했다. */
    QUEUED,

    /** 같은 사람의 오늘(방문일) 자동 출력 작업이 이미 있다. */
    DUPLICATE,
}

/**
 * `WAITING → CLAIMED → SUBMITTED`가 정상 흐름이다. `SUBMITTED`는 출력대가 인쇄 요청을 브라우저에 넘겼다는 뜻이지 종이가
 * 나왔다는 뜻은 아니다. 인쇄 전 확정 실패는 다시 `WAITING`(재시도 대기)이 되고, 재시도를 다 쓰면 `FAILED`가 된다.
 * 결과를 알 수 없는 점유는 `NEEDS_CHECK`이며 자동으로 다시 출력하지 않는다. `DONE`·`REQUEUED`는 관리자가 처리한 상태다.
 */
enum class BadgePrintJobStatus {
    WAITING,
    CLAIMED,
    SUBMITTED,
    FAILED,
    NEEDS_CHECK,

    /** 관리자가 처리 완료로 닫았다. */
    DONE,

    /** 관리자가 다시 대기열에 넣어 원본을 가리키는 새 작업이 생겼다. */
    REQUEUED,
}

/** 출력대가 보고하는 결과. */
enum class BadgePrintOutcome {
    /** 인쇄 요청을 넘겼다. */
    SUBMITTED,

    /** 인쇄 요청을 넘기기 전에 실패했다. 재시도해도 같은 명찰이 두 장 나오지 않는다. */
    FAILED_BEFORE_PRINT,
}

/** 시도 이력의 결과. 출력대가 보고한 결과와 서버가 결과 없이 닫은 사유다. */
enum class BadgePrintAttemptOutcome {
    SUBMITTED,
    FAILED_BEFORE_PRINT,

    /** 점유 후 30초 안에 결과가 오지 않았다. */
    EXPIRED,

    /** 출력대에 새 세션이 열렸다(새로고침·다른 노트북). */
    TAKEN_OVER,

    /** 같은 세션이 결과를 보고하지 않고 새 작업을 요청했다. */
    ABANDONED,
}

/** 관리자의 `NEEDS_CHECK`·`FAILED` 작업 처리. */
enum class BadgePrintResolveAction {
    REQUEUE,
    DONE,
}
