package team.startup.expo.domain.training.repository

/** id와 이름만 필요할 때 엔티티 전체(`information_json` 포함)를 읽지 않도록 하는 투영. */
interface TraineeNameView {
    val id: Long
    val name: String
}
