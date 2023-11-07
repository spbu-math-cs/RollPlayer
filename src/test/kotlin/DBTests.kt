import db.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.IllegalArgumentException
import kotlin.NoSuchElementException

private const val TEST_FOLDER = "for_tests"
private val sampleMapFiles = listOf(
    "sample_map_for_test_A",
    "sample_map_for_test_B"
)

class DBTests {
    @Test
    fun sampleUserTest() {
        DBOperator.addUser("Vasia", "vasia@mail.ru", "vasia12345")
        DBOperator.addUser("Petya", "petya@yandex.ru","petya09876")
        DBOperator.addUser("Clara", "clara@gmail.com","zmxncbva")
        DBOperator.addUser("Dendy", "dendy100100_0101@404.com","zmxncbva")
        DBOperator.addUser("Arben", "arben@postashqiptare.al","qwertyzx")

        assertThrows<IllegalArgumentException> { DBOperator.addUser("12345", "12345@mail.ru","abc") }
        assertThrows<IllegalArgumentException> { DBOperator.addUser("Clara", "clara2@gmail.com","loginalreadyexists") }
        assertThrows<IllegalArgumentException> { DBOperator.addUser("Kim", "kim@mail.ru","비밀번호에잘못된문자가있습니다") }

        assertThrows<IllegalArgumentException> { DBOperator.addUser("12346", "petya@yandex.ru","emailalreadyexists") }
        assertThrows<IllegalArgumentException> { DBOperator.addUser("Wendy", "h@ck3r@O_o.|&|","emailincorrect") }
        assertThrows<IllegalArgumentException> { DBOperator.addUser("Kim", "kimmail.ru","emailincorrect") }

        val users = DBOperator.getAllUsers()
        assert(users.any { it.login == "Vasia" })
        assert(users.any { it.login == "Clara" })
        assert(users.any { it.login == "Dendy" })

        val userVasia = DBOperator.getUserByLogin("Vasia")
        assertNotNull(userVasia)
        assertEquals(userVasia!!.login, "Vasia")
        assertEquals(userVasia.email, "vasia@mail.ru")
        assertEquals("Vasia", DBOperator.getUserByID(userVasia.id)?.login)
        assertEquals("Vasia", DBOperator.getUserByEmail(userVasia.email)?.login)
        assertNull(DBOperator.getUserByID(DBOperator.getAllUsers().maxOf { it.id } + 1u))

        DBOperator.deleteUserByID(userVasia.id)
        assertNull(DBOperator.getUserByID(userVasia.id))
        assertNull(DBOperator.getUserByLogin("Vasia"))
        assertNull(DBOperator.getUserByEmail("vasia@mail.ru"))

        DBOperator.getAllUsers()
            .forEach { DBOperator.deleteUserByID(it.id) }
        assertNull(DBOperator.getUserByLogin("Clara"))
    }

    @Test
    fun userManipulationTest() {
        assertTrue(DBOperator.checkLoginAvailability("Vasia"))
        assertTrue(DBOperator.checkLoginAvailability("Petya"))

        DBOperator.addUser("Vasia", "vasia@mail.ru", "vasia12345")
        DBOperator.addUser("Petya", "petya@yandex.ru","petya09876")
        DBOperator.addUser("Clara", "clara@gmail.com","zmxncbva")
        DBOperator.addUser("Dendy", "dendy@yahoo.com","zmxncbva")
        DBOperator.addUser("Arben", "arben@postashqiptare.al","qwertyzx")

        assertFalse(DBOperator.checkLoginAvailability("Vasia"))
        assertFalse(DBOperator.checkLoginAvailability("Petya"))
        assertFalse(DBOperator.checkLoginAvailability(""))
        assertTrue(DBOperator.checkLoginAvailability("Kira"))
        assertTrue(DBOperator.checkLoginAvailability("Jumbo"))

        assertFalse(DBOperator.checkEmailAvailability("dendy@yahoo.com"))
        assertFalse(DBOperator.checkEmailAvailability("arben@postashqiptare.al"))
        assertFalse(DBOperator.checkEmailAvailability("napoléon@gmail.com"))
        assertFalse(DBOperator.checkEmailAvailability("petya_yandex.ru"))
        assertFalse(DBOperator.checkEmailAvailability("petya@yandexru"))
        assertFalse(DBOperator.checkEmailAvailability(""))

        assertTrue(DBOperator.checkEmailAvailability("kira@gmail.com"))
        assertTrue(DBOperator.checkEmailAvailability("jumbo@mumbo.jumbo"))
        assertTrue(DBOperator.checkEmailAvailability("dendy@yandex.ru"))

        val userIds = DBOperator.getAllUsers()
            .associateBy({ it.login }) { it.id }

        assertTrue(DBOperator.checkUserPassword(userIds["Vasia"]!!, "vasia12345"))
        assertTrue(DBOperator.checkUserPassword(userIds["Clara"]!!, "zmxncbva"))
        assertTrue(DBOperator.checkUserPassword(userIds["Dendy"]!!, "zmxncbva"))

        assertFalse(DBOperator.checkUserPassword(userIds["Vasia"]!!, "petya09876"))
        assertFalse(DBOperator.checkUserPassword(userIds["Clara"]!!, "zmxncbvz"))
        assertFalse(DBOperator.checkUserPassword(userIds["Arben"]!!, "qwertyz"))

        assertNotNull(DBOperator.getUserByLogin("Vasia"))

        DBOperator.updateUserLogin(userIds["Vasia"]!!, "Basil")

        assertThrows<IllegalArgumentException> { DBOperator.updateUserLogin(
            DBOperator.getAllUsers().maxOf { it.id } + 1u,
            "DoesNotExist"
        ) }

        assertNotNull(DBOperator.getUserByLogin("Basil"))
        assertNull(DBOperator.getUserByLogin("Vasia"))
        assertEquals(userIds["Vasia"]!!, DBOperator.getUserByLogin("Basil")!!.id)
        assertTrue(DBOperator.checkUserPassword(userIds["Vasia"]!!, "vasia12345"))

        assertTrue(DBOperator.checkUserPassword(userIds["Petya"]!!, "petya09876"))

        DBOperator.updateUserPassword(userIds["Petya"]!!, "imthecoolest")

        assertFalse(DBOperator.checkUserPassword(userIds["Petya"]!!, "petya09876"))
        assertTrue(DBOperator.checkUserPassword(userIds["Petya"]!!, "imthecoolest"))

        assertThrows<IllegalArgumentException> { DBOperator.updateUserLogin(userIds["Clara"]!!, "Arben") }
        assertThrows<IllegalArgumentException> { DBOperator.updateUserLogin(userIds["Clara"]!!, "") }

        assertThrows<IllegalArgumentException> { DBOperator.updateUserPassword(userIds["Clara"]!!, "qwe") }
        assertThrows<IllegalArgumentException> { DBOperator.updateUserPassword(userIds["Arben"]!!, "karaktere_të_pavlefshme") }

        assertDoesNotThrow { DBOperator.updateUserPassword(userIds["Dendy"]!!, "vasia12345") }
    }

    @Test
    fun passwordValidationTest() {
        assertTrue(DBOperator.checkPasswordValidity("qlaksocifunre"))
        assertTrue(DBOperator.checkPasswordValidity("imthecoolest"))
        assertTrue(DBOperator.checkPasswordValidity("|-|e's_/\\_h@Ck3R"))
        assertTrue(DBOperator.checkPasswordValidity("%a\$b|c*d?e\"f/g&h~i`j"))

        assertFalse(DBOperator.checkPasswordValidity(""))
        assertFalse(DBOperator.checkPasswordValidity("short"))
        assertFalse(DBOperator.checkPasswordValidity("###"))
        assertFalse(DBOperator.checkPasswordValidity("Jsem_nejlepší"))
        assertFalse(DBOperator.checkPasswordValidity("no spaces in password"))
        assertFalse(DBOperator.checkPasswordValidity("હુંસૌથીશાનદારછું"))

        val hash = DBOperator.hashPassword("imthecoolest", 12345, 67890)
        assertEquals(hash, DBOperator.hashPassword("imthecoolest", 12345, 67890))
        assertNotEquals(hash, DBOperator.hashPassword("imthecoolest", 54321, 67890))
        assertNotEquals(hash, DBOperator.hashPassword("imthecoolest", 12345, 98760))
        assertNotEquals(hash, DBOperator.hashPassword("imthecoolest", 12344, 67890))
        assertNotEquals(hash, DBOperator.hashPassword("imthecoolest", 12345, 67891))
        assertNotEquals(hash, DBOperator.hashPassword("qwertyuiop", 12345, 67890))
        assertNotEquals(hash, DBOperator.hashPassword("હુંસૌથીશાનદારછું", 12345, 67890))
    }

    @Test
    fun sampleMapTest() {
        val fileName = sampleMapFiles[0]
        val filePath = "$mapsFolder/$fileName.json"

        val anotherFileName = UUID.randomUUID().toString()
        val anotherFilePath = "$mapsFolder/$TEST_FOLDER/$anotherFileName.json"

        DBOperator.createNewMap(fileName, "TestMap")
        assertEquals(
            """
                    {
                        "name": "TestMap"
                    }
                """.trimIndent(), File(filePath)
                .readText()
        )

        DBOperator.addMap(anotherFilePath)

        val maps = DBOperator.getAllMaps()
        val existingMap = maps.firstOrNull {
            it.pathToJson == filePath
        } ?: fail()
        val nonExistingMap = maps.firstOrNull {
            it.pathToJson == anotherFilePath
        } ?: fail()

        assertEquals(filePath, DBOperator.getMapByID(existingMap.id)?.pathToJson)
        assertEquals(anotherFilePath, DBOperator.getMapByID(nonExistingMap.id)?.pathToJson)
        DBOperator.deleteMapByID(nonExistingMap.id)
        assertNull(DBOperator.getMapByID(nonExistingMap.id))

        // Также удалит все сессии
        DBOperator.getAllMaps()
            .forEach { DBOperator.deleteMapByID(it.id) }
        assertNull(DBOperator.getMapByID(existingMap.id))
    }

    @Test
    fun sampleTextureTest() {
        val fileName = UUID.randomUUID().toString()
        val filePath = "$texturesFolder/$TEST_FOLDER/$fileName.png"

        DBOperator.addTexture(filePath)

        val textures = DBOperator.getAllTextures()
        assertEquals(1, textures.count())
        assertEquals(filePath, textures[0].pathToFile)
        assertEquals(filePath, DBOperator.getTextureByID(textures[0].id)?.pathToFile)

        DBOperator.deleteTextureByID(textures[0].id)
        assertNull(DBOperator.getTextureByID(textures[0].id))
        DBOperator.deleteAllTextures()
        assert(DBOperator.getAllTextures().isEmpty())
    }

    @Test
    fun sampleSessionTest() {
        val mapFileName = sampleMapFiles[1]

        DBOperator.addUser("Vasia", "vasia@mail.ru", "vasia12345")
        DBOperator.addUser("Petya", "petya@mail.ru","petya09876")
        DBOperator.addUser("Clara", "clara@mail.ru","zmxncbva")

        val playerIds = DBOperator
            .getAllUsers()
            .associateBy({ it.login }) { it.id }

        DBOperator.createNewMap(mapFileName, "TestMap")
        DBOperator.addMap("$mapsFolder/${UUID.randomUUID()}.json")
        val (mapId1, mapId2) = DBOperator.getAllMaps().map { it.id }

        DBOperator.addSession(mapId1, true, Instant.now())
        DBOperator.addSession(mapId1, true, Instant.EPOCH)
        DBOperator.addSession(mapId2, false, Instant.now())
        val (sId1, sId2, sId3) = DBOperator.getAllSessions().map { it.id }

        assert(DBOperator.getAllSessions().any { it.mapID == mapId2 })
        assert(DBOperator.getActiveSessions().all { it.mapID == mapId1 })

        assertTrue(DBOperator.getSessionByID(sId2)?.active ?: false)
        assertDoesNotThrow { DBOperator.setSessionActive(sId2, false) }
        assertFalse(DBOperator.getSessionByID(sId2)?.active ?: true)
        assertDoesNotThrow { DBOperator.setSessionActive(sId2, true) }
        assertTrue(DBOperator.getSessionByID(sId2)?.active ?: false)

        assertThrows<IllegalArgumentException> {
            DBOperator.setSessionActive(maxOf(sId1, sId2, sId3) + 1u, true)
        }

        DBOperator.addPlayerToSession(sId1, playerIds["Vasia"]!!, 1, 2)
        DBOperator.addPlayerToSession(sId2, playerIds["Vasia"]!!, 1, 3)
        DBOperator.addPlayerToSession(sId1, playerIds["Petya"]!!, 2, 3)
        DBOperator.addPlayerToSession(sId3, playerIds["Petya"]!!, 3, 4)
        DBOperator.addPlayerToSession(sId2, playerIds["Clara"]!!, 5)
        DBOperator.addPlayerToSession(sId3, playerIds["Clara"]!!)

        assert(DBOperator.getUsersInSession(sId1)
            .let { players ->
                players.any { it.login == "Vasia" } &&
                        players.any { it.login == "Petya" } &&
                        players.none { it.login == "Clara" }
            })
        assert(DBOperator.getUsersInSession(sId2)
            .let { players ->
                players.any { it.login == "Vasia" } &&
                        players.none { it.login == "Petya" } &&
                        players.any { it.login == "Clara" }
            })
        assert(DBOperator.getPlayersGameStateOfSession(sId3)
            .let { players ->
                players.none { it.player.login == "Vasia" } &&
                        players.any { it.player.login == "Petya" && it.xPos == 3 && it.yPos == 4 } &&
                        players.any { it.player.login == "Clara" }
            })

        assert(DBOperator.getSessionsOfUser(playerIds["Clara"]!!)
            .let { sessions ->
                sessions.none { it.id == sId1 } &&
                        sessions.any { it.id == sId2 } &&
                        sessions.any { it.id == sId3 }
            })
        assertTrue(DBOperator.removePlayerFromSession(sId3, playerIds["Clara"]!!))
        assert(DBOperator.getSessionsOfUser(playerIds["Clara"]!!)
            .let { sessions ->
                sessions.none { it.id == sId1 } &&
                        sessions.any { it.id == sId2 } &&
                        sessions.none { it.id == sId3 }
            })
        assert(DBOperator.getUsersInSession(sId3)
            .let { players ->
                players.any { it.login == "Petya" } &&
                        players.none { it.login == "Clara" }
            })

        assert(DBOperator.getPlayersGameStateOfSession(sId1)
            .first { it.player.login == "Vasia" }
            .let { it.xPos == 1 && it.yPos == 2 })
        DBOperator.movePlayer(sId1, playerIds["Vasia"]!!, 7, 8)
        assert(DBOperator.getPlayersGameStateOfSession(sId1)
            .first { it.player.login == "Vasia" }
            .let { it.xPos == 7 && it.yPos == 8 })

        DBOperator.deleteSessionByID(sId1)
        assertNull(DBOperator.getSessionByID(sId1))
        assertThrows<IllegalArgumentException> { DBOperator.getUsersInSession(sId1) }
        assertThrows<NoSuchElementException> { DBOperator.getPlayersGameStateOfSession(sId1).first() }

        DBOperator.deleteAllSessions()
        assert(DBOperator.getSessionsOfUser(playerIds["Clara"]!!).isEmpty())
    }

    @AfterEach
    fun clearDatabase() {
        DBOperator.deleteAllSessions()
        DBOperator.getAllMaps().forEach { DBOperator.deleteMapByID(it.id) }
        DBOperator.getAllUsers().forEach { DBOperator.deleteUserByID(it.id) }
        DBOperator.deleteAllTextures()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun createTestDB() {
            DBOperator.createDBForTests()
        }

        @JvmStatic
        @AfterAll
        fun deleteDB() {
            DBOperator.deleteTestDatabase()
            File("$mapsFolder/$TEST_FOLDER")
                .let { file -> if (file.isDirectory) file.delete() }
            sampleMapFiles
                .forEach {
                    File("$mapsFolder/$it.json").apply { if (isFile) delete() }
                }
            File("$texturesFolder/$TEST_FOLDER")
                .let { file -> if (file.isDirectory) file.delete() }
        }
    }
}