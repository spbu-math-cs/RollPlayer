package db

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.*
import java.time.Instant
import java.util.Random

const val dbPath = "./data/roll_player"
const val dbTestPath = "./data/test_db"
const val mapsFolder = "./maps"
const val texturesFolder = "./textures"

object DBOperator {
    // =================
    // SERVICE FUNCTIONS
    // =================

    private fun extractFileName(fileName: String) =
        fileName
            .removeSuffix(".db")
            .removeSuffix(".mv")
            .removeSuffix(".trace")
            .removeSuffix(".sql")
            .removeSuffix(".jpg")
            .removeSuffix(".png")
            .removeSuffix(".bmp")
            .removeSuffix(".json")
            .removeSuffix(".tmj")
            .removeSuffix(".tsj")

    private fun createDatabase(filePath: String) {
        val exists = File("$filePath.mv.db").isFile

        Database.connect("jdbc:h2:$filePath")

        if (!exists) {
            // create DB schema
            transaction {
                SchemaUtils.create(UserTable)
                SchemaUtils.create(TextureTable)
                SchemaUtils.create(MapTable)
                SchemaUtils.create(SessionTable)
                SchemaUtils.create(CharacterTable)
            }
        }

        // создать папки с картами и текстурами, если их нет
        val mapDir = File(mapsFolder)
        val txtrDir = File(texturesFolder)
        mapDir.mkdirs()
        txtrDir.mkdirs()
    }

    fun connectOrCreate() =
        createDatabase(dbPath)

    fun createDBForTests() =
        createDatabase(dbTestPath)

    private fun deleteFileIfExists(filePath: String) =
        (File(filePath)).let { file ->
            if (file.exists())
                file.delete()
        }

    private fun deleteDBByFileName(fileName: String) {
        deleteFileIfExists("$fileName.mv.db")
        deleteFileIfExists("$fileName.trace.db")
    }

    fun deleteDatabase() = deleteDBByFileName(dbPath)

    fun deleteTestDatabase() = deleteDBByFileName(dbTestPath)

    // =============
    // GET FUNCTIONS
    // =============

    fun getAllTextures() = transaction { TextureData.all().map { it.raw() } }
    fun getAllUsers() = transaction { UserData.all().map { it.raw() } }
    fun getAllMapInfos() = transaction { MapData.all().map { it.raw() } }
    fun getAllSessions() = transaction { SessionData.all().map { it.raw() } }
    fun getActiveSessions() = transaction {
        SessionData.find(SessionTable.active eq true)
            .map { it.raw() }
    }
    fun getAllCharacters() = transaction { CharacterData.all().map { it.raw() } }

    fun getUserByID(id: UInt) = transaction { UserData.findById(id.toInt())?.raw() }
    fun getTextureByID(id: UInt) = transaction { TextureData.findById(id.toInt())?.raw() }
    fun getMapByID(id: UInt) = transaction { MapData.findById(id.toInt())?.raw() }
    fun getSessionByID(id: UInt) = transaction { SessionData.findById(id.toInt())?.raw() }
    fun getCharacterByID(id: UInt) = transaction { CharacterData.findById(id.toInt())?.raw() }

    fun getUserByLogin(login: String): UserInfo? = transaction {
        UserData.find(UserTable.login eq login)
            .map { it.raw() }
            .firstOrNull()
    }

    fun getUserByEmail(email: String): UserInfo? = transaction {
        UserData.find(UserTable.email eq email)
            .map { it.raw() }
            .firstOrNull()
    }

    fun getUsersInSession(sessionId: UInt) = transaction {
        SessionData.findById(sessionId.toInt())?.players?.map { it.raw() }
            ?: throw IllegalArgumentException("Session #$sessionId does not exist")
    }

    fun getAllCharactersInSession(sessionId: UInt) = transaction {
        CharacterData.find(CharacterTable.sessionID eq sessionId.toInt())
            .map { it.raw() }
    }

    fun getAllCharactersOfUser(userId: UInt) = transaction {
        CharacterData.find(
            CharacterTable.userID eq userId.toInt())
            .map { it.raw() }
    }

    fun getAllCharactersOfUserInSession(userId: UInt, sessionId: UInt) = transaction {
        CharacterData.find(
            CharacterTable.userID eq userId.toInt() and
                    (CharacterTable.sessionID eq sessionId.toInt()))
            .map { it.raw() }
    }

    fun getAllSessionsWithUser(userId: UInt) = transaction {
        UserData.findById(userId.toInt())?.sessions?.map { it.raw() }
            ?: throw IllegalArgumentException("User #$userId does not exist")
    }

    // ================
    // CREATE FUNCTIONS
    // ================

    fun createNewMap(fileName: String, mapName: String) = transaction {
        val mapPath = "$mapsFolder/${extractFileName(fileName)}.json"
        val mapFile = File(mapPath)
        if (!mapFile.createNewFile())
            throw FileAlreadyExistsException(mapFile, reason = "Map $fileName already exists")
        if (!MapData.find(MapTable.pathToJson eq mapPath).empty())
            throw FileAlreadyExistsException(mapFile, reason = "Map $fileName already recorded in the database")

        @Language("JSON") val emptyMap = """
            {
                "name": "$mapName"
            }
        """.trimIndent()

        mapFile.writeText(emptyMap)

        return@transaction MapData.new {
            pathToJson = mapPath
        }.raw()
    }

    fun addTexture(pathToFile: String) = transaction {
        if (!TextureData.find(TextureTable.pathToFile eq pathToFile).empty())
            throw IllegalArgumentException("Texture `${pathToFile}` already recorded in the database")

        TextureData.new {
            this.pathToFile = pathToFile
        }.id.value
    }

    fun addMap(pathToJson: String) = transaction {
        if (!MapData.find(MapTable.pathToJson eq pathToJson).empty())
            throw IllegalArgumentException("Map `${pathToJson}` already recorded in the database")

        MapData.new {
            this.pathToJson = pathToJson
        }.id.value
    }

    fun addSession(mapID: UInt, active: Boolean, started: Instant) = transaction {
        SessionData.new {
            map = MapData.findById(mapID.toInt())
                ?: throw IllegalArgumentException("Map #${mapID} does not exist")
            this.active = active
            this.started = started
        }.id.value
    }

    fun addCharacter(userId: UInt,
                     sessionId: UInt,
                     name: String,
                     x: Int = 0,
                     y: Int = 0) = transaction {
        CharacterData.new {
//            session = SessionData.findById(sessionId.toInt())
//                ?: throw IllegalArgumentException("Session #$sessionId does not exist")
            user = UserData.findById(userId.toInt())
                ?: throw IllegalArgumentException("User #$userId does not exist")
            this.name = name
            row = x
            col = y
        }.raw()
    }

    // =================
    // USER MANIPULATION
    // =================

    private const val MIN_PASSWORD_CHARACTERS = 8
    private const val PASSWORD_SPECIAL_CHARS = ",.!@#$%^&*()\":;\'_-+=[]|{}~`<>?/\\"
    private const val  EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z.-]+\$"
    private val PASSWORD_CHAR_LIST = sequence<Char> {
        yieldAll('A'..'Z')
        yieldAll('a'..'z')
        yieldAll('0'..'9')
        yieldAll(PASSWORD_SPECIAL_CHARS.toList())
    }.toList()
    private const val PASSWORD_HASH_MODULUS = 2147483647L // простое число; модуль должен быть простым

    private fun failOnInvalidLogin(login: String) {
        if (login.isBlank())
            throw IllegalArgumentException("Cannot create user with blank login")

        if (!UserData.find(UserTable.login eq login).empty())
            throw IllegalArgumentException("User with login `$login` already exists")
    }

    private fun failOnInvalidEmail(email: String) {
        if (!email.matches(EMAIL_REGEX.toRegex()))
            throw IllegalArgumentException("Email does not match regex $EMAIL_REGEX")
        if (!UserData.find(UserTable.email eq email).empty())
            throw IllegalArgumentException("User with email `$email` already exists")
    }

    private fun failOnInvalidPassword(password: String) {
        if (password.length < MIN_PASSWORD_CHARACTERS)
            throw IllegalArgumentException("User password must have at least $MIN_PASSWORD_CHARACTERS characters")

        password.forEach {
            if (it !in PASSWORD_CHAR_LIST)
                throw IllegalArgumentException("Invalid character `$it` in password:" +
                        " password must contain only characters" +
                        " A-Z a-z 0-9 $PASSWORD_SPECIAL_CHARS")
        }
    }

    // для тестирования
    fun checkLoginAvailability(login: String) = transaction {
        try {
            failOnInvalidLogin(login)
            true
        } catch (e: Exception) {
            false
        }
    }

    // для тестирования
    fun checkEmailAvailability(email: String) = transaction {
        try {
            failOnInvalidEmail(email)
            true
        } catch (e: Exception) {
            false
        }
    }

    // для тестирования
    fun checkPasswordValidity(password: String) = try {
        failOnInvalidPassword(password)
        true
    } catch (_: Exception) { false }

    fun hashPassword(password: String, pswInit: Int, pswFactor: Int): Int {
        fun mathMod(l: Long, m: Long) =
            (l % m).let {
                if (it < 0) m - it
                else it
            }

        var hash: Long = pswInit.toLong()
        for (ch in password)
            hash = mathMod((hash * pswFactor + ch.code), PASSWORD_HASH_MODULUS)

        return hash.toInt()
    }

    fun addUser(login: String, email: String, password: String) = transaction {
        failOnInvalidLogin(login)
        failOnInvalidEmail(email)
        failOnInvalidPassword(password)

        val rand = Random()
        val pswInit = rand.nextInt(PASSWORD_HASH_MODULUS.toInt())
        val pswFactor = rand.nextInt(PASSWORD_HASH_MODULUS.toInt())

        UserData.new {
            this.login = login
            this.email = email
            this.passwordHash = hashPassword(password, pswInit, pswFactor)
            this.pswHashInitial = pswInit
            this.pswHashFactor = pswFactor
        }.id.value.toUInt()
    }

    fun checkUserPassword(userId: UInt, password: String) = transaction {
        val user = UserData.findById(userId.toInt())
            ?: throw IllegalArgumentException("User #$userId does not exist")

        hashPassword(password, user.pswHashInitial, user.pswHashFactor) == user.passwordHash
    }

    fun updateUserLogin(userId: UInt, newLogin: String) = transaction {
        val user = UserData.findById(userId.toInt())
            ?: throw IllegalArgumentException("User #$userId does not exist")

        failOnInvalidLogin(newLogin)

        user.login = newLogin
    }

    fun updateUserEmail(userId: UInt, newEmail: String) = transaction {
        val user = UserData.findById(userId.toInt())
            ?: throw IllegalArgumentException("User #$userId does not exist")

        failOnInvalidEmail(newEmail)

        user.email = newEmail
    }

    fun updateUserPassword(userId: UInt, newPassword: String) = transaction {
        val user = UserData.findById(userId.toInt())
            ?: throw IllegalArgumentException("User #$userId does not exist")

        failOnInvalidPassword(newPassword)

        user.passwordHash = hashPassword(newPassword, user.pswHashInitial, user.pswHashFactor)
    }

    // ====================
    // SESSION MANIPULATION
    // ====================

    fun setSessionActive(sessionId: UInt, active: Boolean) = transaction {
        (SessionData.findById(sessionId.toInt())
                ?: throw IllegalArgumentException("Session #$sessionId does not exist"))
            .active = active
    }

    // =============================
    // PLAYER CHARACTER MANIPULATION
    // =============================

    fun moveCharacter(characterId: UInt, newRow: Int, newCol: Int) = transaction {
        CharacterData
            .findById(characterId.toInt())
            ?.apply {
                row = newRow
                col = newCol
            }?.raw()
    }

    // ================
    // DELETE FUNCTIONS
    // ================

    fun deleteAllTextures() = transaction {
        TextureData.all()
            .forEach { it.delete() }
    }

    fun deleteAllSessions() = transaction {
        CharacterData.all()
            .forEach { it.delete() }
        SessionData.all()
            .forEach { it.delete() }
    }

    // ВНИМАНИЕ: также этот пользователь выйдет из всех сессий
    fun deleteUserByID(id: UInt): Boolean = transaction {
        CharacterData.find(CharacterTable.userID eq id.toInt())
            .forEach { it.delete() }
        UserData.findById(id.toInt())
            ?.delete() ?: return@transaction false
        true
    }
    
    fun deleteTextureByID(id: UInt): Boolean = transaction {
        TextureData.findById(id.toInt())
            ?.delete() ?: return@transaction false
        true
    }

    // ВНИМАНИЕ: также удалит все сессии на этой карте
    // Это делается для ненарушения ссылочной целостности
    fun deleteMapInfoByID(id: UInt): Boolean = transaction {
        SessionData.find(SessionTable.mapID eq id.toInt())
            .forEach {
                CharacterData.find(CharacterTable.sessionID eq it.id)
                    .forEach { it.delete() }
                it.delete()
            }
        MapData.findById(id.toInt())
            ?.delete() ?: return@transaction false
        true
    }
    
    fun deleteSessionByID(id: UInt): Boolean = transaction {
        CharacterData.find(CharacterTable.sessionID eq id.toInt())
            .forEach { it.delete() }
        SessionData.findById(id.toInt())
            ?.delete() ?: return@transaction false
        true
    }

    fun deleteCharacterById(id: UInt): Boolean = transaction {
        CharacterData.findById(id.toInt())
            ?.delete() ?: return@transaction false
        true
    }

    fun deleteAllCharactersOfUserFromSession(uId: UInt, sId: UInt) = transaction {
        CharacterData.find(
            CharacterTable.sessionID eq sId.toInt() and
                    (CharacterTable.userID eq uId.toInt())
        ).forEach {
            it.delete()
        }
    }
}