package team.startup.expo.domain.user.repository

import org.springframework.http.HttpStatus
import team.startup.expo.domain.user.entity.Admin
import team.startup.expo.global.exception.ExpectedException

private const val ADMIN_NOT_FOUND_MESSAGE = "해당 유저를 찾을 수 없습니다."

private fun adminNotFound() = ExpectedException(HttpStatus.NOT_FOUND, ADMIN_NOT_FOUND_MESSAGE)

/** 행을 잠그고 관리자를 조회하며, 없으면 404이다. 트랜잭션 안에서 불러야 한다([AdminRepository.findByIdForUpdate]). */
fun AdminRepository.getByIdForUpdate(id: Long): Admin = findByIdForUpdate(id) ?: throw adminNotFound()

/** 내 정보에 필요한 필드만 조회하며, 없으면 404이다. */
fun AdminRepository.getProfileById(id: Long): AdminProfileView = findProfileById(id) ?: throw adminNotFound()
