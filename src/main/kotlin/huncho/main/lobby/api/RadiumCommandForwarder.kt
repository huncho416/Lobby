package huncho.main.lobby.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Forwards commands from the lobby server to the Radium proxy via HTTP API
 * Uses the /api/v1/commands/execute endpoint for command forwarding
 */
class RadiumCommandForwarder(private val radiumApiUrl: String = "http://localhost:8080") {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun executeCommand(playerName: String, command: String): CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                val requestData = CommandRequest(playerName, command)
                val requestBody = gson.toJson(requestData).toRequestBody(jsonMediaType)
                
                val request = Request.Builder()
                    .url("$radiumApiUrl/api/v1/commands/execute-with-response")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val responseBody = response.body?.string()
                            val result = gson.fromJson(responseBody, CommandResponse::class.java)
                            CommandResult(result.success, result.error ?: "Command executed successfully")
                        }
                        response.code == 404 -> {
                            CommandResult(false, "Player not found on proxy - make sure you're connected")
                        }
                        else -> {
                            val errorBody = response.body?.string()
                            CommandResult(false, "Failed: ${response.code} - $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                CommandResult(false, "Connection error: ${e.message}")
            }
        }
    }
}

data class CommandRequest(val player: String, val command: String)
data class CommandResponse(
    val player: String,
    val command: String,
    val success: Boolean,
    val executed: Boolean,
    val error: String? = null
)
data class CommandResult(val success: Boolean, val message: String)
