package team.startup.expo.domain.participation.service

/** 박람회 삭제와 참가자 쓰기를 직렬화하는 advisory lock의 키 네임스페이스. `V4` 마이그레이션의 트리거와 같은 값이다. */
object ExpoDeletionLock {
    const val NAMESPACE = 26
}
