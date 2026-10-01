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
    @Column(nullable = false, length = 10)
    val name: String,
    @Column(nullable = false, unique = true, length = 50)
    val nickname: String,
    @Column(nullable = false, unique = true, length = 100)
    val email: String,
    @Column(nullable = false, length = 100)
    val password: String,
    @Column(nullable = false, unique = true, length = 15)
    val phoneNumber: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var authority: Authority = Authority.ROLE_STANDARD,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: Status = Status.PENDING,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    fun accept() {
        authority = Authority.ROLE_ADMIN
        status = Status.ACCEPTED
    }
}
