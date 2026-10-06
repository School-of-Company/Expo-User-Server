package team.startup.expo.user

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.domain.user.entity.Status
import team.startup.expo.domain.user.repository.AdminRepository
import team.startup.expo.domain.user.service.AcceptAdminService
import team.startup.expo.domain.user.service.RefuseAdminService
import team.startup.expo.global.exception.ExpectedException
import team.startup.expo.support.IntegrationTestSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 같은 대기 계정에 승인과 거절이 동시에 들어오는 순서를 직접 만들어 재현한다. 한쪽이 행 잠금을 쥔 채
 * 트랜잭션을 열어 두고, 다른 쪽 서비스가 그 사이에 끼어들게 한 뒤 잠금을 풀어 결과를 확인한다.
 */
class AdminConcurrencyTests : IntegrationTestSupport() {
    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var acceptAdminService: AcceptAdminService

    @Autowired
    private lateinit var refuseAdminService: RefuseAdminService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val executor = Executors.newFixedThreadPool(2)
    private var pendingAdminId = 0L

    @BeforeEach
    fun setUp() {
        clearAdmins()
        pendingAdminId =
            adminRepository
                .save(
                    Admin(
                        name = "관리자",
                        nickname = "pending",
                        email = "pending@gsm.hs.kr",
                        password = "encoded",
                        phoneNumber = "01011110002",
                    ),
                ).id!!
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `승인이 진행 중일 때 들어온 거절은 기다렸다가 409이고 승인된 계정을 지우지 않는다`() {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val accepting =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    adminRepository.findByIdForUpdate(pendingAdminId)!!.accept()
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        locked.await(10, TimeUnit.SECONDS) shouldBe true

        val refusing = executor.submit<Throwable?> { runCatching { refuseAdminService.execute(pendingAdminId) }.exceptionOrNull() }
        Thread.sleep(BLOCK_CHECK_MILLIS)
        refusing.isDone shouldBe false

        release.countDown()
        accepting.get(10, TimeUnit.SECONDS)
        val error = refusing.get(10, TimeUnit.SECONDS) as ExpectedException
        error.status shouldBe HttpStatus.CONFLICT
        adminRepository.findById(pendingAdminId).get().status shouldBe Status.ACCEPTED
    }

    @Test
    fun `거절이 진행 중일 때 들어온 승인은 기다렸다가 404이고 계정은 되살아나지 않는다`() {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val refusing =
            executor.submit {
                transactionTemplate.executeWithoutResult {
                    adminRepository.delete(adminRepository.findByIdForUpdate(pendingAdminId)!!)
                    locked.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        locked.await(10, TimeUnit.SECONDS) shouldBe true

        val accepting = executor.submit<Throwable?> { runCatching { acceptAdminService.execute(pendingAdminId) }.exceptionOrNull() }
        Thread.sleep(BLOCK_CHECK_MILLIS)
        accepting.isDone shouldBe false

        release.countDown()
        refusing.get(10, TimeUnit.SECONDS)
        val error = accepting.get(10, TimeUnit.SECONDS) as ExpectedException
        error.status shouldBe HttpStatus.NOT_FOUND
        adminRepository.existsById(pendingAdminId) shouldBe false
    }

    private companion object {
        const val BLOCK_CHECK_MILLIS = 500L
    }
}
