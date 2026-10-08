package team.startup.expo.domain.participation.service

interface PurgeExpoDataService {
    /** 박람회의 연수자와 일반 참가자, 그에 딸린 입장 기록과 설문 답변을 모두 지운다. 여러 번 불러도 같은 결과다. */
    fun execute(expoId: String)
}
