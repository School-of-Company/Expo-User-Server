package team.startup.expo.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tb_admin")
class Admin(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @field:Column(nullable = false, length = 10)
    val name: String,
    @field:Column(nullable = false, unique = true, length = 50)
    val nickname: String,
    @field:Column(nullable = false, unique = true, length = 100)
    val email: String,
    @field:Column(nullable = false, length = 100)
    val password: String,
    @field:Column(name = "phone_number", nullable = false, unique = true, length = 15)
    val phoneNumber: String,
    authority: Authority = Authority.ROLE_STANDARD,
    status: Status = Status.PENDING,
) {
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var authority: Authority = authority
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: Status = status
        protected set

    fun accept() {
        authority = Authority.ROLE_ADMIN
        status = Status.ACCEPTED
    }
}
