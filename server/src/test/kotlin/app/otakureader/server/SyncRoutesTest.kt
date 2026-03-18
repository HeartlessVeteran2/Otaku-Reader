package app.otakureader.server

import app.otakureader.server.config.AppConfig
import app.otakureader.server.model.SnapshotResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest

class SyncRoutesTest {

    private lateinit var testStoragePath: String
    private val testToken = "test-auth-token"

    private fun ApplicationTestBuilder.setupTestApp() {
        // Create unique temp directory for this test run
        testStoragePath = createTempDirectory("otaku-reader-test-").toFile().absolutePath

        application {
            val config = AppConfig(
                host = "localhost",
                port = 8080,
                authToken = testToken,
                storagePath = testStoragePath
            )
            module(config)
        }
    }

    @AfterTest
    fun cleanup() {
        // Clean up temp directory after each test
        if (::testStoragePath.isInitialized) {
            File(testStoragePath).deleteRecursively()
        }
    }

    @Test
    fun `health check returns OK without auth`() = testApplication {
        setupTestApp()

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun `upload snapshot requires auth`() = testApplication {
        setupTestApp()

        val response = client.post("/sync/upload") {
            contentType(ContentType.Application.Json)
            setBody("""{"data":"test","timestamp":12345}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `upload and download snapshot works with auth`() = testApplication {
        setupTestApp()

        val testData = "dGVzdCBkYXRh" // base64 for "test data"
        val timestamp = 1234567890L

        // Upload
        val uploadResponse = client.post("/sync/upload") {
            header("Authorization", "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"data":"$testData","timestamp":$timestamp}""")
        }

        assertEquals(HttpStatusCode.OK, uploadResponse.status)

        // Download
        val downloadResponse = client.get("/sync/download") {
            header("Authorization", "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.OK, downloadResponse.status)
        val body = downloadResponse.bodyAsText()
        assertTrue(body.contains(testData))
        assertTrue(body.contains("$timestamp"))
    }

    @Test
    fun `delete snapshot works with auth`() = testApplication {
        setupTestApp()

        // First upload
        client.post("/sync/upload") {
            header("Authorization", "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody("""{"data":"test","timestamp":12345}""")
        }

        // Delete
        val deleteResponse = client.delete("/sync") {
            header("Authorization", "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify deleted by parsing JSON response
        val downloadResponse = client.get("/sync/download") {
            header("Authorization", "Bearer $testToken")
        }

        val snapshotResponse = downloadResponse.body<SnapshotResponse>()
        assertFalse(snapshotResponse.exists, "Snapshot should not exist after deletion")
    }
}
