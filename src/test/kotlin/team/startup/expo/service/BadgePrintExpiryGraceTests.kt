package team.startup.expo.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.startup.expo.domain.participation.presentation.dto.request.ClaimBadgePrintJobReqDto
import team.startup.expo.domain.participation.repository.BadgePrintRepository
import team.startup.expo.domain.participation.repository.BadgePrintStation
import team.startup.expo.domain.participation.service.BadgePrintExpiryGrace
import team.startup.expo.domain.participation.service.impl.BadgePrintServiceImpl
import java.time.LocalDateTime
import java.util.UUID

class BadgePrintExpiryGraceTests :
    BehaviorSpec({
        given("기동 직후 만료 유예") {
            var clock = 0L
            val grace = BadgePrintExpiryGrace(30).apply { nanoTime = { clock } }

            `when`("요청을 받기 전이면") {
                then("만료를 미룬다") {
                    grace.active() shouldBe true
                }
            }
            `when`("요청을 받기 시작한 뒤 30초가 지나기 전이면") {
                then("미루고, 30초가 지나면 미루지 않는다") {
                    clock = 1_000L
                    grace.onReady()
                    clock += 29_999_999_999L
                    grace.active() shouldBe true
                    clock += 1L
                    grace.active() shouldBe false
                }
            }
            `when`("유예를 0초로 두면") {
                then("기동 중에도 미루지 않는다") {
                    BadgePrintExpiryGrace(0).active() shouldBe false
                }
            }
        }

        given("유예 중인 서비스") {
            val repository = mockk<BadgePrintRepository>(relaxed = true)
            // 요청을 받기 전(onReady 전)이라 유예 중이다
            val service = BadgePrintServiceImpl(repository, BadgePrintExpiryGrace(30))
            val now = LocalDateTime.of(2026, 11, 1, 10, 0)
            val stationId = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            every { repository.dbNow() } returns now
            every { repository.lockStation("expo", stationId) } returns BadgePrintStation(stationId, "출력대 1", sessionId, false, now)
            every { repository.activeJob(stationId) } returns null
            every { repository.pickWaiting(any(), any(), any()) } returns null

            `when`("주기 만료가 돌면") {
                then("점유를 닫지 않는다") {
                    service.expireClaims() shouldBe 0
                    verify(exactly = 0) { repository.expireClaims(any(), any()) }
                }
            }
            `when`("claim이 오면") {
                then("만료 없이 대기 작업만 찾는다") {
                    service.claim("expo", stationId, ClaimBadgePrintJobReqDto(sessionId, UUID.randomUUID())) shouldBe null
                    verify(exactly = 0) { repository.expireClaims(any(), any()) }
                    verify { repository.pickWaiting("expo", now.toLocalDate(), now) }
                }
            }
        }
    })
