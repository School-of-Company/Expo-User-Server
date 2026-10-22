package team.startup.expo.domain.participation.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import java.time.LocalDate
import java.util.UUID

data class FieldVisitProjection(
    val expoId: String,
    val participantId: Long,
    val visitDate: LocalDate,
    val generation: Long,
    val status: FieldVisitStatus,
)

data class FieldVisitCreation(
    val fingerprint: String,
    val responseJson: String,
)

@Repository
class FieldVisitRepository(
    private val jdbc: JdbcTemplate,
) {
    fun creation(
        expoId: String,
        requestId: String,
    ): FieldVisitCreation? =
        jdbc
            .query(
                "SELECT fingerprint, response_json FROM tb_field_visit_creation WHERE expo_id = ? AND request_id = ?",
                { rs, _ -> FieldVisitCreation(rs.getString(1), rs.getString(2)) },
                expoId,
                requestId,
            ).singleOrNull()

    fun saveCreation(
        expoId: String,
        requestId: String,
        fingerprint: String,
        responseJson: String,
    ) {
        jdbc.update("INSERT INTO tb_field_visit_creation VALUES (?, ?, ?, ?)", expoId, requestId, fingerprint, responseJson)
    }

    fun markNew(participantId: Long) {
        jdbc.update("INSERT INTO tb_field_visit_member VALUES (?)", participantId)
    }

    fun requiresProjection(participantId: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM tb_field_visit_member WHERE participant_id = ?)",
            Boolean::class.java,
            participantId,
        ) == true

    fun hasPending(representativeId: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM tb_standard_participant p JOIN tb_field_visit_member m ON m.participant_id = p.id " +
                "WHERE (p.id = ? OR p.representative_id = ?) AND NOT EXISTS(SELECT 1 FROM tb_field_visit v WHERE v.participant_id = p.id))",
            Boolean::class.java,
            representativeId,
            representativeId,
        ) == true

    fun reservedCount(representativeId: Long): Long =
        jdbc.queryForObject(
            "SELECT count(*) FROM tb_standard_participant p WHERE (p.id = ? OR p.representative_id = ?) AND ($RESERVED)",
            Long::class.java,
            representativeId,
            representativeId,
        )!!

    fun reserved(participantId: Long): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM tb_standard_participant p WHERE p.id = ? AND ($RESERVED))",
            Boolean::class.java,
            participantId,
        ) == true

    fun projection(registrationId: UUID): FieldVisitProjection? =
        jdbc
            .query(
                "SELECT expo_id, participant_id, visit_date, generation, status FROM tb_field_visit WHERE registration_id = ?",
                { rs, _ ->
                    FieldVisitProjection(
                        rs.getString(1),
                        rs.getLong(2),
                        rs.getObject(3, LocalDate::class.java),
                        rs.getLong(4),
                        FieldVisitStatus.valueOf(rs.getString(5)),
                    )
                },
                registrationId,
            ).singleOrNull()

    fun operation(operationId: UUID): String? =
        jdbc
            .query(
                "SELECT fingerprint FROM tb_field_visit_operation WHERE operation_id = ?",
                { rs, _ -> rs.getString(1) },
                operationId,
            ).singleOrNull()

    fun saveOperation(
        operationId: UUID,
        expoId: String,
        fingerprint: String,
    ) {
        jdbc.update("INSERT INTO tb_field_visit_operation VALUES (?, ?, ?)", operationId, expoId, fingerprint)
    }

    fun project(
        expoId: String,
        participantId: Long,
        registrationId: UUID,
        body: ProjectFieldVisitReqDto,
    ) {
        jdbc.update(
            "INSERT INTO tb_field_visit VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (registration_id) DO UPDATE " +
                "SET generation = EXCLUDED.generation, status = EXCLUDED.status, operation_id = EXCLUDED.operation_id",
            registrationId,
            expoId,
            participantId,
            body.visitDate,
            body.generation,
            body.status.name,
            body.operationId,
        )
    }

    private companion object {
        const val RESERVED =
            "p.representative_id IS NULL " +
                "OR EXISTS(SELECT 1 FROM tb_field_visit v WHERE v.participant_id = p.id AND v.status = 'ACTIVE') " +
                "OR (EXISTS(SELECT 1 FROM tb_field_visit_member m WHERE m.participant_id = p.id) " +
                "AND NOT EXISTS(SELECT 1 FROM tb_field_visit v WHERE v.participant_id = p.id)) " +
                "OR (p.application_type = 'PRE' AND p.released = false " +
                "AND NOT EXISTS(SELECT 1 FROM tb_field_visit_member m WHERE m.participant_id = p.id))"
    }
}
