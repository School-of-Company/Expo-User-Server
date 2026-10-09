package team.startup.expo.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import team.startup.expo.domain.participation.service.RegisteredParticipant
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonMapperBuilder

/** 아웃박스에는 `participants_json`이 문자열로 저장되어 있다가 발행할 때 읽힌다. `name`이 생기기 전에 저장된 행도 읽혀야 한다. */
class RegisteredParticipantSerializationTests {
    private val mapper = jacksonMapperBuilder().build()
    private val type = object : TypeReference<List<RegisteredParticipant>>() {}

    @Test
    fun `name이 생기기 전에 저장된 참가자 목록도 읽히고 이름은 null이다`() {
        val stored = """[{"id":1,"code":"abc"},{"id":2,"code":"def"}]"""

        mapper.readValue(stored, type) shouldBe listOf(RegisteredParticipant(1, "abc"), RegisteredParticipant(2, "def"))
        mapper.readValue(stored, type).all { it.name == null } shouldBe true
    }

    @Test
    fun `이름이 있으면 저장하고 다시 읽어도 그대로이다`() {
        val participants = listOf(RegisteredParticipant(1, "abc", "홍길동"), RegisteredParticipant(2, "def", "김철수"))

        mapper.readValue(mapper.writeValueAsString(participants), type) shouldBe participants
    }
}
