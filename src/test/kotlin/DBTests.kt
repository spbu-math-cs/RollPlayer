import db.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.util.*
import kotlin.test.*

private const val testFolder = "for_tests"
private const val sampleMap = "sample_map"
private const val sampleMapForSession = "sample_map_for_session"

class DBTests {
    @Test
    fun sampleUserTest() {
        DBOperator.addUser(UserInfo("Vasiliy", "vasia12345"))
        DBOperator.addUser(UserInfo("Petr", "petya09876"))
        DBOperator.addUser(UserInfo("Carl", "zmxncbv"))
        DBOperator.addUser(UserInfo("Dendy", "zmxncbv"))
        DBOperator.addUser(UserInfo("Arben", "qwerty"))

        assertFails { DBOperator.addUser(UserInfo("12345", "")) }
        assertFails { DBOperator.addUser(UserInfo("Carl", "alreadyexists")) }

        val users = DBOperator.getAllUsers()
        assert(users.any { it.login == "Vasiliy" && it.password == "vasia12345" })
        assert(users.any { it.login == "Carl" && it.password == "zmxncbv" })
        assert(users.any { it.login == "Dendy" && it.password == "zmxncbv" })

        val userVasia = DBOperator.getUserByLogin("Vasiliy")
        assertNotNull(userVasia)
        assert(userVasia.login == "Vasiliy" && userVasia.password == "vasia12345")
        assertEquals("Vasiliy", DBOperator.getUserByID(userVasia.id)?.login)
        assertNull(DBOperator.getUserByID(6))
    }

    @Test
    fun sampleMapTest() {
        val fileName = sampleMap
        val filePath = "$mapsFolder/$fileName.json"

        val anotherFileName = UUID.randomUUID().toString()
        val anotherFilePath = "$mapsFolder/$testFolder/$anotherFileName.json"

        DBOperator.createNewMap(fileName, "TestMap")
        assertEquals(
            """
                    {
                        "name": "TestMap"
                    }
                """.trimIndent(), File(filePath)
                .readText()
        )

        DBOperator.addMap(MapInfo(anotherFilePath))

        val maps = DBOperator.getAllMapInfos()
        val existingMap = maps.firstOrNull {
            it.pathToJson == filePath
        } ?: fail()
        val nonExistingMap = maps.firstOrNull {
            it.pathToJson == anotherFilePath
        } ?: fail()

        assertEquals(filePath, DBOperator.getMapByID(existingMap.id)?.pathToJson)
        assertEquals(anotherFilePath, DBOperator.getMapByID(nonExistingMap.id)?.pathToJson)
    }

    @Test
    fun sampleTextureTest() {
        val fileName = UUID.randomUUID().toString()
        val filePath = "$texturesFolder/$testFolder/$fileName.png"

        DBOperator.addTexture(TextureInfo(filePath))

        val textures = DBOperator.getAllTextures()
        assertEquals(1, textures.count())
        assertEquals(filePath, textures[0].pathToFile)
        assertEquals(filePath, DBOperator.getTextureByID(textures[0].id)?.pathToFile)
    }

    @Test
    fun sampleSessionTest() {
        val fileName = sampleMapForSession

        DBOperator.addUser(UserInfo("Vasia", "vasia12345"))
        DBOperator.addUser(UserInfo("Petya", "petya09876"))
        DBOperator.addUser(UserInfo("Clara", "zmxncbv"))

        val playerIds = DBOperator
            .getAllUsers()
            .associateBy({ it.login }) { it.id }

        DBOperator.createNewMap(fileName, "TestMap")
        DBOperator.addMap(MapInfo("$mapsFolder/${UUID.randomUUID()}.json"))
        val (mapId1, mapId2) = DBOperator.getAllMapInfos().map { it.id }

        DBOperator.addSession(SessionInfo(mapId1, true, Instant.now()))
        DBOperator.addSession(SessionInfo(mapId1, true, Instant.EPOCH))
        DBOperator.addSession(SessionInfo(mapId2, false, Instant.now()))
        val (sId1, sId2, sId3) = DBOperator.getAllSessions().map { it.id }

        assert(DBOperator.getAllSessions().any { it.mapID == mapId2 })
        assert(DBOperator.getActiveSessions().all { it.mapID == mapId1 })

        assertTrue(DBOperator.getSessionByID(sId2)?.active ?: false)
        assertTrue(DBOperator.setSessionActive(sId2, false))
        assertFalse(DBOperator.getSessionByID(sId2)?.active ?: true)
        assertTrue(DBOperator.setSessionActive(sId2, true))
        assertTrue(DBOperator.getSessionByID(sId2)?.active ?: false)

        DBOperator.addPlayerToSession(sId1, playerIds["Vasia"]!!, 1, 2)
        DBOperator.addPlayerToSession(sId2, playerIds["Vasia"]!!, 1, 3)
        DBOperator.addPlayerToSession(sId1, playerIds["Petya"]!!, 2, 3)
        DBOperator.addPlayerToSession(sId3, playerIds["Petya"]!!, 3, 4)
        DBOperator.addPlayerToSession(sId2, playerIds["Clara"]!!, 5)
        DBOperator.addPlayerToSession(sId3, playerIds["Clara"]!!)

        assert(DBOperator.getPlayersOfSession(sId1)
            .let { players ->
                players.any { it.login == "Vasia" } &&
                        players.any { it.login == "Petya" } &&
                        players.none { it.login == "Clara" }
            })
        assert(DBOperator.getPlayersOfSession(sId2)
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
        assert(DBOperator.getPlayersOfSession(sId3)
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
            File("$mapsFolder/$testFolder")
                .let { file -> if (file.isDirectory) file.delete() }
            File("$mapsFolder/$sampleMap.json")
                .let { file -> if (file.isFile) file.delete() }
            File("$mapsFolder/$sampleMapForSession.json")
                .let { file -> if (file.isFile) file.delete() }
            File("$texturesFolder/$testFolder")
                .let { file -> if (file.isDirectory) file.delete() }
        }
    }
}