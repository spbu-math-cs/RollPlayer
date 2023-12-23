import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import db.DBOperator
import db.MapInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import server.ActiveSessionData
import server.JWTParams

import server.logger
import server.routing.*
import java.io.File
import java.time.Duration
import java.util.*
import kotlin.test.assertFails

private fun createErrorResponseMessage(msg: String?) = mapOf(
    "type" to "error", "message" to msg
).toString()

fun Application.extractedT(jwtParams: JWTParams) {
    install(io.ktor.server.plugins.cors.routing.CORS) {
        anyHost()
    }
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = Duration.ofSeconds(2)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(Authentication) {
        jwt ("auth-jwt") {
            realm = jwtParams.myRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtParams.secret))
                    .withAudience(jwtParams.audience)
                    .withIssuer(jwtParams.issuer)
                    .build())
            validate { credential ->
                if (credential.payload.getClaim("id").asString() != "" &&
                    credential.payload.getClaim("login").asString() != "" &&
                    (credential.expiresAt?.time?.minus(System.currentTimeMillis()) ?: 0) > 0
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }

    val activeSessions = Collections.synchronizedMap<UInt, ActiveSessionData>(mutableMapOf())

    DBOperator.connectOrCreate(true)

    logger.info("Server is ready")
    routing {
        staticFiles("", File("static"))

        requestsUser(jwtParams)
        requestsMap()
        requestsPictures()

        gameSession()
        gameSessionConnection(activeSessions)
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpsTest {
    private val server: ApplicationEngine
    val config = HoconApplicationConfig(ConfigFactory.load())
    val secret = config.property("jwt.secret").getString()
    val issuer = config.property("jwt.issuer").getString()
    val audience = config.property("jwt.audience").getString()
    val myRealm = config.property("jwt.realm").getString()
    init {
        server = embeddedServer(Netty, port = 1234) {
            extractedT(JWTParams(secret, issuer, audience, myRealm))

        }
        server.start(wait = false)
    }

    @BeforeAll
    fun setUp() {
        var engine = TestApplicationEngine(createTestEnvironment())
        engine.start(wait = false)
    }
    @AfterAll
    fun tearDown() {
        server.stop(1000, 2000)
        DBOperator.deleteDatabase()
    }
    @Test
    fun `GET request to api-textures returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/textures")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.body<String>()
        assertEquals(
//            "{\"result\":[{\"filepath\":\".\\\\resources\\\\textures\\\\tileset_packed.png\",\"id\":\"1\"},{\"filepath\":\".\\\\resources\\\\textures\\\\tileset_packed_plus.png\",\"id\":\"2\"}],\"type\":\"ok\"}",
            """{"result":[{"filepath":"./resources/textures/tileset_packed.png","id":"2"},{"filepath":"./resources/textures/tileset_packed_plus.png","id":"1"}],"type":"ok"}""",
            responseBody
        )
    }

    @Test
    fun `GET request to api-textures-id returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/textures/1")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: ByteArray = response.body()
//        assertEquals(
//            "[B@10641c09",
//            responseBody.toString()
//        )
    }

    @Test
    fun `GET request with non-cast-to-UInt to api-textures-id returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/textures/1ewd")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"Texture #0 does not exist\"}",
            responseBody
        )
    }

    @Test
    fun `GET request to non-existing api-textures-id returns 400 error`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/textures/999")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"Texture #999 does not exist\"}",
            responseBody
        )
    }

    @Test
    fun `GET request to api-tilesets returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/tilesets")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "ok",
            JSONObject(responseBody).get("type")
        )
    }

    @Test
    fun `GET request to api-tilesets-id returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/tilesets/1")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"columns\": 49, \"image\": \"tileset_packed.png\", \"imageheight\": 352, \"imagewidth\": 784, \"margin\": 0, \"name\": \"tileset_packed\", \"spacing\": 0, \"tilecount\": 1078, \"tiledversion\": \"1.10.2\", \"tileheight\": 16, \"tilewidth\": 16, \"type\": \"tileset\", \"version\": \"1.10\"}".filter { !it.isWhitespace() },
            responseBody.filter { !it.isWhitespace() }
        )
    }

    @Test
    fun `GET request to non-existing api-tilesets returns 400 error`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/tilesets/100")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"Tileset #100 does not exist\"}",
            responseBody
        )
    }

    @Test
    fun `GET request to api-maps returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/maps")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "ok",
            JSONObject(responseBody).get("type")
        )
    }

    @Test
    fun `GET request to api-maps-id returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/maps/3")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            File("./resources/maps/example_map.tmj").readText(),
            responseBody
        )
    }

    @Test
    fun `GET request to api-maps returns expected response returns error`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/maps/999")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"Map #999 does not exist\"}",
            responseBody
        )
    }

    @Test
    fun `GET request to api-users returns expected response`(): Unit = runBlocking {
        val response = HttpClient().get("http://127.0.0.1:1234/api/users")
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "ok",
            JSONObject(responseBody).get("type")
        )
    }

    // проходит нормально ровно один раз - когда впервые запускаешь и юзер создается
    @Test
    fun `POST request to api-register returns expected response`(): Unit = runBlocking {
        val requestBody = """{"login": "testLogin9999", "email": "test9999@email.ru", "password": "test9999Password"}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/register"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "ok",
            JSONObject(responseBody).get("type")
        )
    }

    @Test
    fun `POST request with incorrect password to api-register returns 400 error`(): Unit = runBlocking {
        val requestBody = """{"login": "testLogin", "email": "incorrectEmail", "password": "pass"}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/register"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"User with login `testLogin` already exists\"}",
            responseBody
        )
    }

    @Test
    fun `POST request with existing email to api-register returns 400 error`(): Unit = runBlocking {
        val requestBody = """{"login": "testLogin", "email": "test@email.ru", "password": "testPassword"}"""
        HttpClient().post("http://127.0.0.1:1234/api/register"){
            setBody(requestBody)
        }
        val response = HttpClient().post("http://127.0.0.1:1234/api/register"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"User with login `testLogin` already exists\"}",
            responseBody
        )
    }

    @Test
    fun `POST request with incorrect email to api-register returns 400 error`(): Unit = runBlocking {
        val requestBody = """{"login": "testLogin", "email": "incorrectEmail", "password": "testPassword"}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/register"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"User with login `testLogin` already exists\"}",
            responseBody
        )
    }

    @Test
    fun `POST request to api-login returns expected response`(): Unit = runBlocking {
        val requestBody = """{"login": "testLogin", "password": "testPassword"}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/login"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "User 1 logged in successfully",
            JSONObject(response.bodyAsText()).getString("message")
        )
    }

    @Test
    fun `POST request to non-existing api-login returns 400 error`(): Unit = runBlocking {
        val requestBody = """{"login": "nonExistingLogin", "password": "testPassword"}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/login"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "{\"type\":\"error\",\"message\":\"User with this login/email is not exist\"}",
            responseBody
        )
    }

    @Test
    fun `POST request to api-logout returns expected response`(): Unit = runBlocking {
        val loginBody = """{"login": "testLogin", "password": "testPassword"}"""
        val login = HttpClient().post("http://127.0.0.1:1234/api/login"){
            setBody(loginBody)
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val loginT: String = login.bodyAsText()
//        assertEquals(
//            "{\"result\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwOi8vZXhhbXBsZS5jb20vaGVsbG8iLCJpc3MiOiJodHRwOi8vZXhhbXBsZS5jb20vIiwiaWQiOjMsImxvZ2luIjoidGVzdExvZ2luIiwiZXhwIjoxNzAzMjM4NDYyfQ.wa865YKc_cief1tcrYPf_qoMFCTfzaiwlrRm7EDfZzA\",\"type\":\"ok\",\"message\":\"User 3 logged in successfully\"}",
//            loginT
//        )
        val logoutBody = """{"userId": 1}"""
        val logout: HttpResponse = HttpClient().post("http://127.0.0.1:1234/api/logout") {
            setBody(logoutBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, logout.status)
        val logoutT: String = logout.bodyAsText()
        assertEquals(
            "Token is not valid or has expired",
            logoutT
        )
    }

    @Test
    fun `POST request to api-logout returns error`(): Unit = runBlocking {
        val requestBody = """{"userId": 1}"""
        val response = HttpClient().post("http://127.0.0.1:1234/api/logout"){
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val responseBody: String = response.bodyAsText()
        assertEquals(
            "Token is not valid or has expired",
            responseBody
        )
    }

    @Test
    fun `POST request to api-edit-userId returns expected response`(): Unit = runBlocking {
        val loginBody = """{"login": "testLogin", "email": "1@email.ru", "password": "testPassword"}"""
        val login = HttpClient().post("http://127.0.0.1:1234/api/login"){
            setBody(loginBody)
        }
        //assertEquals(HttpStatusCode.OK, login.status)
//        val token = JSONObject(login.bodyAsText()).get("result").toString()

        val requestBody = """{"login": "testLogin2", "email": "1@email.ru", "password": "testPassword"}"""
        val response: HttpResponse = HttpClient().post("http://127.0.0.1:1234/api/user/edit") {
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(
            "Token is not valid or has expired",
            response.bodyAsText()
        )
    }



    @Test
    fun `GET request to api-userId-sessions returns expected response`(): Unit = runBlocking {
        val loginBody = """{"login": "testLogin", "password": "testPassword"}"""

        val login = HttpClient().post("http://127.0.0.1:1234/api/login"){
            setBody(loginBody)
        }
        assertEquals(HttpStatusCode.OK, login.status)

        val token = JSONObject(login.bodyAsText()).get("result").toString()

        val requestBody = """{"id": 1}"""
        val response = HttpClient().get("http://127.0.0.1:1234/api/user/sessions"){
            headers["Authorization"] = "Bearer $token"
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(
            "{\"result\":[],\"type\":\"ok\"}",
            response.bodyAsText()
        )
    }

    @Test
    fun `GET request to api-userId-sessions returns error`(): Unit = runBlocking {
        val requestBody = """{"id": 1}"""

        val response = HttpClient().get("http://127.0.0.1:1234/api/user/sessions") {
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(
            "Token is not valid or has expired",
            response.bodyAsText()
        )
    }

    @Test
    fun `POST request to api-game-create returns expected response`(): Unit = runBlocking {

        var response = HttpClient().post("http://127.0.0.1:1234/api/game/create") {
            parameter("mapId", 1)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "ok",
            JSONObject(response.bodyAsText()).get("type")
        )
    }

    @Test
    fun `POST request to api-game-create without mapId returns 400 error`(): Unit = runBlocking {
        val response: HttpResponse = HttpClient().post("http://127.0.0.1:1234/api/game/create")

        assertEquals(HttpStatusCode.BadRequest, response.status)

        assertEquals(
            "{\"type\":\"error\",\"message\":\"Request must contain \\\"mapId\\\" query parameter\"}",
            response.bodyAsText()
        )
    }


    @Test
    fun `GET request to non-existing api-game-sessionId-mapId returns 400 error`(): Unit = runBlocking {
        val requestBody = """{"id": 999}"""
        val response: HttpResponse = HttpClient().get("http://127.0.0.1:1234/api/game/999/mapId") {
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "{\"type\":\"error\",\"message\":\"Session #999 does not exist\"}",
            response.bodyAsText()
        )
    }

    @Test
    fun `GET request to api-pictures returns expected response`(): Unit = runBlocking {
        //val requestBody = PictureInfo(1u, "./path/to/picture1.png")
        val response: HttpResponse = HttpClient().get("http://127.0.0.1:1234/api/pictures")
        assertEquals(HttpStatusCode.OK, response.status)

//        assertEquals(
//            "{\"result\":{\"filepath\":\"./resources/pictures/img_2023-12-19T21:55:49.105280400.png\",\"id\":\"8\"},\"type\":\"ok\"}",
//            response.bodyAsText()
//        )
    }

    @Test
    fun `GET request to api-pictures-id returns expected response`(): Unit = runBlocking {
        val requestBody = """{"id": 1}"""
        val response: HttpResponse = HttpClient().get("http://127.0.0.1:1234/api/pictures") {
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
//        assertEquals(
//            "{\"result\":[{\"filepath\":\"./resources/pictures/img_2023-12-19T21:54:40.758162900.png\",\"id\":\"4\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T21:54:53.159829700.png\",\"id\":\"5\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T21:55:05.662137600.png\",\"id\":\"6\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T21:55:30.200976600.png\",\"id\":\"7\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T21:55:49.105280400.png\",\"id\":\"8\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T22:12:15.016607900.png\",\"id\":\"9\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T22:12:48.981366100.png\",\"id\":\"10\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T22:14:01.303129.png\",\"id\":\"11\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T22:20:59.605302.png\",\"id\":\"12\"},{\"filepath\":\"./resources/pictures/img_2023-12-19T22:26:32.474949400.png\",\"id\":\"13\"},{\"filepath\":\"./resources/pictures/img_2023-12-21T12:03:59.439532700.png\",\"id\":\"14\"},{\"filepath\":\"./resources/pictures/img_2023-12-21T12:12:51.205454400.png\",\"id\":\"15\"},{\"filepath\":\"./resources/pictures/img_2023-12-21T12:29:28.991237.png\",\"id\":\"16\"},{\"filepath\":\"./resources/pictures/img_2023-12-21T12:29:58.590485700.png\",\"id\":\"17\"},{\"filepath\":\"./resources/pictures/img_2023-12-21T12:32:24.953699900.png\",\"id\":\"18\"},{\"filepath\":\".\\\\resources\\\\pictures\\\\avatar01.png\",\"id\":\"1\"},{\"filepath\":\".\\\\resources\\\\pictures\\\\avatar02.png\",\"id\":\"2\"},{\"filepath\":\".\\\\resources\\\\pictures\\\\avatar03.png\",\"id\":\"3\"}],\"type\":\"ok\"}",
//            response.bodyAsText()
//        )

    }


    @Test
    fun `POST request to api-pictures returns expected response`(): Unit = runBlocking {
        //val requestBody = """{"id": 999}"""
        val requestBody = "0x10, 0x10, 0x01, 0x11, 0x11, 0x11, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff"
        val response: HttpResponse = HttpClient().post("http://127.0.0.1:1234/api/pictures") {
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET request to api-user returns expected response`(): Unit = runBlocking {
        val loginBody = """{"login": "testLogin", "password": "testPassword"}"""
        val login: HttpResponse = HttpClient().post("http://127.0.0.1:1234/api/login") {
            setBody(loginBody)
        }
//        assertEquals(HttpStatusCode.OK, login.status)
        val requestBody = """{"id": 1}"""

        val token = JSONObject(login.bodyAsText()).get("result").toString()

        val response: HttpResponse = HttpClient().get("http://127.0.0.1:1234/api/user") {
            headers["Authorization"] = "Bearer $token"
            setBody(requestBody)
        }

//        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(
            "Get userdata successfully",
            JSONObject(response.bodyAsText()).get("message")
        )
    }


    @Test
    fun `GET request to api-user returns error`(): Unit = runBlocking {
        val requestBody = """{"id": 1}"""

        val response: HttpResponse = HttpClient().get("http://127.0.0.1:1234/api/user") {
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

}