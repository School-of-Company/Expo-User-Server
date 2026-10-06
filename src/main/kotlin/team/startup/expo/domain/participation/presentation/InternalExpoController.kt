package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.service.PurgeExpoDataService

/** 서비스 간 호출 전용이다. `X-Internal-Token`이 필요하며 gateway에 라우팅하지 않는다. */
@RestController
@RequestMapping("/internal/expos")
class InternalExpoController(
    private val purgeExpoDataService: PurgeExpoDataService,
) {
    @Operation(
        summary = "박람회 참가자 데이터 정리",
        description = "박람회의 연수자, 일반 참가자, 입장 기록, 설문 답변을 지우고 삭제 기록을 남겨 이후 재삽입을 막습니다. 이미 삭제됐거나 데이터가 없어도 204입니다.",
    )
    @DeleteMapping("/{expoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun purge(
        @PathVariable("expoId") @Size(min = 1, max = 36) expoId: String,
    ) {
        purgeExpoDataService.execute(expoId)
    }
}
