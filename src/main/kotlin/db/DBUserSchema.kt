package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*

const val indentifierLength = 255
const val pathLength = 255

object UserTable: IntIdTable("user", "user_id") {
    val login = varchar("login", indentifierLength).uniqueIndex()
    val email = varchar("email", indentifierLength).uniqueIndex()
    val passwordHash = integer("password_hash")
    val pswHashInitial = integer("psw_hash_initial")
    val pswHashFactor = integer("psw_hash_factor")
}

@Serializable
data class UserInfo(
    val id: UInt,
    val login: String,
    val email: String,
    val passwordHash: Int
)

class UserData(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<UserData>(UserTable)

    var login by UserTable.login
    var email by UserTable.email
    var passwordHash by UserTable.passwordHash
    var pswHashInitial by UserTable.pswHashInitial
    var pswHashFactor by UserTable.pswHashFactor

    var sessions by SessionData via SessionPlayerTable

    fun raw(): UserInfo = UserInfo(
        id.value.toUInt(),
        login,
        email,
        passwordHash,
    )
}
