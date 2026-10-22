package team.startup.expo.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.startup.expo.domain.participation.entity.StandardParticipant
import team.startup.expo.domain.participation.presentation.dto.request.CreateFieldVisitParticipantsReqDto
import team.startup.expo.domain.participation.presentation.dto.request.FieldVisitStatus
import team.startup.expo.domain.participation.presentation.dto.request.NewFieldVisitParticipant
import team.startup.expo.domain.participation.presentation.dto.request.ProjectFieldVisitReqDto
import team.startup.expo.domain.participation.repository.FieldVisitCreation
import team.startup.expo.domain.participation.repository.FieldVisitProjection
import team.startup.expo.domain.participation.repository.FieldVisitRepository
import team.startup.expo.domain.participation.repository.StandardParticipantRepository
import team.startup.expo.domain.participation.service.ExpoDeletionGuard
import team.startup.expo.domain.participation.service.ParticipantRegistrationLock
import team.startup.expo.domain.participation.service.RegistrationOutboxWriter
import team.startup.expo.domain.participation.service.RepresentativeFinder
import team.startup.expo.domain.participation.service.impl.FieldVisitServiceImpl
import team.startup.expo.domain.training.entity.ApplicationType
import team.startup.expo.global.exception.ExpectedException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class FieldVisitServiceTests :
    BehaviorSpec({
        class Fixture {
            val participants = mockk<StandardParticipantRepository>()
            val visits = mockk<FieldVisitRepository>()
            val outbox = mockk<RegistrationOutboxWriter>(relaxed = true)
            val owners = mockk<RepresentativeFinder>()
            val rows = linkedMapOf<Long, StandardParticipant>()
            val creations = mutableMapOf<Pair<String, String>, FieldVisitCreation>()
            val projections = mutableMapOf<UUID, FieldVisitProjection>()
            val operations = mutableMapOf<UUID, String>()
            val newIds = mutableSetOf<Long>()
            val service =
                FieldVisitServiceImpl(
                    participants,
                    visits,
                    mockk<ParticipantRegistrationLock>(relaxed = true),
                    mockk<ExpoDeletionGuard>(relaxed = true),
                    owners,
                    outbox,
                    JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(),
                )

            init {
                every { owners.find(EXPO, PHONE) } answers { rows.values.firstOrNull { it.representativeId == null } }
                every { participants.findByIdAndExpoId(any(), EXPO) } answers { rows[firstArg()] }
                every { participants.findAllByRepresentativeIdOrderById(any()) } answers
                    { rows.values.filter { it.representativeId == firstArg<Long>() } }
                every { participants.saveAndFlush(any<StandardParticipant>()) } answers {
                    val row = firstArg<StandardParticipant>()
                    val saved =
                        StandardParticipant(
                            id = (rows.keys.maxOrNull() ?: 0) + 1,
                            expoId = row.expoId,
                            name = row.name,
                            phoneNumber = row.phoneNumber,
                            personalInformationStatus = row.personalInformationStatus,
                            applicationType = row.applicationType,
                            applicationDate = row.applicationDate,
                            occupation = row.occupation,
                            school = row.school,
                            region = row.region,
                            representativeId = row.representativeId,
                            code = row.code,
                            informationJson = row.informationJson,
                            informationFormId = row.informationFormId,
                            informationQuestions = row.informationQuestions,
                        )
                    rows[saved.id!!] = saved
                    saved
                }
                every { visits.creation(any(), any()) } answers { creations[firstArg<String>() to secondArg<String>()] }
                every { visits.saveCreation(any(), any(), any(), any()) } answers {
                    creations[firstArg<String>() to secondArg<String>()] = FieldVisitCreation(thirdArg(), arg(3))
                }
                every { visits.markNew(any()) } answers {
                    newIds.add(firstArg())
                    Unit
                }
                every { visits.hasPending(any()) } answers {
                    val owner = firstArg<Long>()
                    rows.values.any {
                        (it.id == owner || it.representativeId == owner) && it.id in newIds &&
                            projections.values.none { visit -> visit.participantId == it.id }
                    }
                }
                every { visits.reservedCount(any()) } answers {
                    val owner = firstArg<Long>()
                    rows.values.count { (it.id == owner || it.representativeId == owner) && reserved(it) }.toLong()
                }
                every { visits.reserved(any()) } answers { reserved(rows.getValue(firstArg())) }
                every { visits.projection(any()) } answers { projections[firstArg()] }
                every { visits.operation(any()) } answers { operations[firstArg()] }
                every { visits.saveOperation(any(), any(), any()) } answers { operations[firstArg()] = thirdArg() }
                every { visits.project(any(), any(), any(), any()) } answers {
                    val body = arg<ProjectFieldVisitReqDto>(3)
                    projections[thirdArg()] = FieldVisitProjection(firstArg(), secondArg(), body.visitDate, body.generation, body.status)
                }
            }

            private fun reserved(person: StandardParticipant): Boolean =
                person.representativeId == null ||
                    projections.values.any { it.participantId == person.id && it.status == FieldVisitStatus.ACTIVE } ||
                    (person.id in newIds && projections.values.none { it.participantId == person.id }) ||
                    (person.id !in newIds && person.applicationType == ApplicationType.PRE && !person.released)

            fun add(
                id: Long,
                owner: Long? = null,
                type: ApplicationType = ApplicationType.PRE,
            ): StandardParticipant =
                StandardParticipant(
                    id = id,
                    expoId = EXPO,
                    name = "기존$id",
                    phoneNumber = if (owner == null) PHONE else null,
                    personalInformationStatus = true,
                    applicationType = type,
                    applicationDate = LocalDateTime.now(),
                    representativeId = owner,
                ).also { rows[id] = it }

            fun project(
                id: Long,
                registrationId: UUID = UUID.randomUUID(),
                generation: Long = 1,
                status: FieldVisitStatus = FieldVisitStatus.ACTIVE,
                date: LocalDate = DATE,
                operationId: UUID = UUID.randomUUID(),
            ): UUID {
                service.project(EXPO, id, registrationId, ProjectFieldVisitReqDto(date, generation, status, operationId))
                return registrationId
            }
        }

        fun request(
            key: String = "new-1",
            names: List<String> = listOf("첫사람", "동행"),
        ) = CreateFieldVisitParticipantsReqDto(
            PHONE,
            key,
            names.mapIndexed {
                i,
                name,
                ->
                NewFieldVisitParticipant("client-$i", name)
            },
            true,
        )

        given("신규 사람을 명시한 생성 요청") {
            `when`("각자의 답변과 제출 당시 form snapshot을 보내면") {
                then("새 사람에게만 저장하고 snapshot과 답변이 달라진 같은 키는 거절한다") {
                    val f = Fixture()
                    val owner = f.add(10)
                    owner.informationJson = "{\"기존\":true}"
                    owner.informationFormId = "old-form"
                    owner.informationQuestions = "[]"
                    val json = JsonMapper.builder().build()
                    val questions = json.readTree("""[{"id":"q1","title":"제출 당시","otherJson":{"extra":true}}]""")
                    val body =
                        request().let {
                            it.copy(
                                formId = "field-form",
                                questions = questions,
                                participants =
                                    it.participants.mapIndexed { i, person ->
                                        person.copy(informationJson = """{"q1":"answer-$i"}""")
                                    },
                            )
                        }
                    val result = f.service.create(EXPO, body)
                    f.service.create(EXPO, body) shouldBe result
                    result.forEachIndexed { i, mapping ->
                        val saved = f.rows.getValue(mapping.participantId)
                        saved.informationJson shouldBe """{"q1":"answer-$i"}"""
                        saved.informationFormId shouldBe "field-form"
                        json.readTree(saved.informationQuestions) shouldBe questions
                    }
                    owner.informationJson shouldBe "{\"기존\":true}"
                    owner.informationFormId shouldBe "old-form"
                    owner.informationQuestions shouldBe "[]"
                    listOf(
                        body.copy(formId = "another-form"),
                        body.copy(questions = json.readTree("[]")),
                        body.copy(participants = body.participants.map { it.copy(informationJson = "{}") }),
                    ).forEach { changed ->
                        shouldThrow<ExpectedException> { f.service.create(EXPO, changed) }.status shouldBe HttpStatus.CONFLICT
                    }
                }
            }
            `when`("잘못된 JSON 답변이나 문항 snapshot을 보내면") {
                then("사람을 일부 생성하기 전에 400으로 거절한다") {
                    val f = Fixture()
                    val json = JsonMapper.builder().build()
                    val invalidAnswer =
                        request().let {
                            it.copy(participants = it.participants.map { person -> person.copy(informationJson = "{") })
                        }
                    listOf(invalidAnswer, request().copy(questions = json.readTree("{}")), request().copy(questions = json.readTree("[1]")))
                        .forEach { invalid ->
                            shouldThrow<ExpectedException> { f.service.create(EXPO, invalid) }.status shouldBe HttpStatus.BAD_REQUEST
                        }
                    verify(exactly = 0) { f.participants.saveAndFlush(any<StandardParticipant>()) }
                    verify(exactly = 0) { f.outbox.registered(any(), any(), any(), any(), any()) }
                }
            }
            `when`("응답이 유실되어 같은 키로 다시 요청하면") {
                then("첫 mapping을 재현하고 한 번만 생성하고 문자 이벤트를 만든다") {
                    val f = Fixture()
                    val first = f.service.create(EXPO, request())
                    f.service.create(EXPO, request()) shouldBe first
                    first.map { it.clientReference } shouldBe listOf("client-0", "client-1")
                    first.map { it.participantId }.distinct().size shouldBe 2
                    f.rows[1]!!.representativeId shouldBe null
                    f.rows[2]!!.representativeId shouldBe 1L
                    verify(exactly = 2) { f.participants.saveAndFlush(any<StandardParticipant>()) }
                    verify(exactly = 1) { f.outbox.registered(any(), any(), any(), any(), any()) }
                }
            }
            `when`("같은 키의 payload가 바뀌거나 pending을 다른 키로 우회하면") {
                then("409이고 추가 저장과 이벤트가 없다") {
                    val f = Fixture()
                    f.service.create(EXPO, request())
                    shouldThrow<ExpectedException> { f.service.create(EXPO, request(names = listOf("다른사람"))) }.status shouldBe
                        HttpStatus.CONFLICT
                    shouldThrow<ExpectedException> { f.service.create(EXPO, request("another")) }.status shouldBe HttpStatus.CONFLICT
                    f.rows.size shouldBe 2
                    verify(exactly = 1) { f.outbox.registered(any(), any(), any(), any(), any()) }
                }
            }
            `when`("중복 clientReference를 보내면") {
                then("부분 생성 없이 400이다") {
                    val f = Fixture()
                    val body = request().let { it.copy(participants = listOf(it.participants.first(), it.participants.first())) }
                    shouldThrow<ExpectedException> { f.service.create(EXPO, body) }.status shouldBe HttpStatus.BAD_REQUEST
                    f.rows.size shouldBe 0
                }
            }
            `when`("기존 사람과 이름이 같아도 신규라고 명시하면") {
                then("기존 ID를 돌려주거나 프로필과 QR을 바꾸지 않는다") {
                    val f = Fixture()
                    val owner = f.add(10)
                    val code = owner.code
                    val response = f.service.create(EXPO, request(names = listOf(owner.name)))
                    response.single().participantId shouldBe 11L
                    f.rows[11]!!.representativeId shouldBe 10L
                    owner.applicationType shouldBe ApplicationType.PRE
                    owner.code shouldBe code
                    owner.name shouldBe "기존10"
                }
            }
        }

        given("대표자를 포함한 5명의 PRE 일행") {
            `when`("모두가 첫날과 둘째날 FIELD 방문하면") {
                then("방문일은 한도에 더하지 않고 여섯번째 신규만 거절한다") {
                    val f = Fixture()
                    f.add(1)
                    (2L..5L).forEach { f.add(it, 1) }
                    val codes = f.rows.mapValues { it.value.code }
                    f.rows.keys.forEach {
                        f.project(it)
                        f.project(it, date = DATE.plusDays(1))
                    }
                    f.projections.size shouldBe 10
                    f.rows.mapValues { it.value.code } shouldBe codes
                    f.rows.values.all { it.applicationType == ApplicationType.PRE } shouldBe true
                    shouldThrow<ExpectedException> { f.service.create(EXPO, request(names = listOf("여섯째"))) }.status shouldBe
                        HttpStatus.CONFLICT
                    verify(exactly = 0) { f.outbox.registered(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("방문 projection fencing") {
            `when`("취소 후 옛 ACTIVE와 CANCELLED가 뒤늦게 도착하면") {
                then("같은 세대 취소는 terminal이고 새 generation은 보존한다") {
                    val f = Fixture()
                    f.add(1)
                    val id = f.project(1)
                    f.project(1, id, status = FieldVisitStatus.CANCELLED)
                    f.project(1, id)
                    f.projections[id]!!.status shouldBe FieldVisitStatus.CANCELLED
                    f.project(1, id, generation = 2)
                    f.project(1, id, status = FieldVisitStatus.CANCELLED)
                    f.project(1, id)
                    f.projections[id]!!.generation shouldBe 2L
                    f.projections[id]!!.status shouldBe FieldVisitStatus.ACTIVE
                }
            }
            `when`("같은 operation을 재전송하거나 다른 payload에 재사용하면") {
                then("동일 요청은 한번만 쓰고 다른 요청은 409다") {
                    val f = Fixture()
                    f.add(1)
                    val op = UUID.randomUUID()
                    val id = f.project(1, operationId = op)
                    f.project(1, id, operationId = op)
                    shouldThrow<ExpectedException> { f.project(1, id, generation = 2, operationId = op) }.status shouldBe
                        HttpStatus.CONFLICT
                    verify(exactly = 1) { f.visits.project(any(), any(), any(), any()) }
                }
            }
            `when`("신규 FIELD 동행자의 마지막 ACTIVE만 취소되면") {
                then("활성 한 자리만 풀리고 PRE와 다른날 ACTIVE는 보존된다") {
                    val f = Fixture()
                    f.add(1)
                    (2L..4L).forEach { f.add(it, 1) }
                    f.add(5, 1, ApplicationType.FIELD)
                    f.newIds.add(5)
                    val first = f.project(5)
                    val second = f.project(5, date = DATE.plusDays(1))
                    f.project(5, first, status = FieldVisitStatus.CANCELLED)
                    shouldThrow<ExpectedException> { f.service.create(EXPO, request(names = listOf("신규"))) }
                    f.project(5, second, date = DATE.plusDays(1), status = FieldVisitStatus.CANCELLED)
                    f.service
                        .create(EXPO, request(names = listOf("신규")))
                        .single()
                        .participantId shouldBe 6L
                    f.rows.size shouldBe 6
                    f.rows[2]!!.applicationType shouldBe ApplicationType.PRE
                }
            }
        }
    }) {
    companion object {
        const val EXPO = "expo-202"
        const val PHONE = "01012345678"
        val DATE: LocalDate = LocalDate.of(2026, 10, 22)
    }
}
