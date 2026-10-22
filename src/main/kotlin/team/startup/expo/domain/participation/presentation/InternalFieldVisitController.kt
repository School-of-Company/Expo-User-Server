package team.startup.expo.domain.participation.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.startup.expo.domain.participation.presentation.dto.request.CreateFieldVisitParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.GetEntryStatusReqDto
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.service.FieldVisitService
import team.startup.expo.domain.participation.service.GetEntryStatusService
import java.util.UUID

@RestController
@RequestMapping("/internal")
@SecurityRequirement(name = "internalToken")
class InternalFieldVisitController(
    private val service: FieldVisitService,
    private val entryStatus: GetEntryStatusService,
) {
    @Operation(
        summary = "방문 신청의 신규 참가자만 생성",
        description = "같은 expo와 requestId는 최초 clientReference와 participantId 매핑을 재현하며 내용이 다르면 409입니다.",
    )
    @PostMapping("/expos/{expoId}/field-visit-participants")
    fun create(
        @PathVariable expoId: String,
        @Valid @RequestBody body: CreateFieldVisitParticipantsReqDto,
    ) = service.create(expoId, body)

    @Operation(summary = "방문일 등록 상태 projection", description = "generation fencing을 적용하며 같은 세대 CANCELLED는 terminal입니다. 프로필과 QR은 변경하지 않습니다.")
    @PutMapping("/expos/{expoId}/participants/{participantId}/field-visits/{registrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun project(
        @PathVariable expoId: String,
        @PathVariable participantId: Long,
        @PathVariable registrationId: UUID,
        @Valid @RequestBody body: ProjectFieldVisitReqDto,
    ) = service.project(expoId, participantId, registrationId, body)

    @Operation(summary = "지정 날짜의 입장 기록 조회", description = "attendanceDate를 그대로 돌려줍니다. false는 진행 중인 저장의 취소나 미저장을 확정하지 않습니다.")
    @PostMapping("/standard-participants/entry-status")
    fun entryStatus(
        @Valid @RequestBody body: GetEntryStatusReqDto,
    ) = entryStatus.execute(body)
}
