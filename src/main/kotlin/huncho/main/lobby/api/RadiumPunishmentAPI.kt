package huncho.main.lobby.api

import com.google.gson.Gson
import huncho.main.lobby.LobbyPlugin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * API client for communicating with Radium proxy punishment system
 */
class RadiumPunishmentAPI(private val baseUrl: String = "http://localhost:8080") {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    
    /**
     * Issue a punishment (ban, mute, warn, etc.)
     */
    fun issuePunishment(request: PunishmentRequest): CompletableFuture<PunishmentResponse> {
        return CompletableFuture.supplyAsync {
            try {
                val body = gson.toJson(request).toRequestBody(jsonMediaType)
                val httpRequest = Request.Builder()
                    .url("$baseUrl/api/v1/punishments/issue")
                    .post(body)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        gson.fromJson(responseBody, PunishmentResponse::class.java)
                    } else {
                        PunishmentResponse(
                            success = false,
                            target = request.target,
                            type = request.type,
                            message = "API Error: ${response.code} - $responseBody"
                        )
                    }
                }
            } catch (e: Exception) {
                LobbyPlugin.logger.error("Failed to issue punishment via API", e)
                PunishmentResponse(
                    success = false,
                    target = request.target,
                    type = request.type,
                    message = "Connection error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Revoke a punishment (unban, unmute, etc.)
     */
    fun revokePunishment(request: PunishmentRevokeRequest): CompletableFuture<PunishmentResponse> {
        return CompletableFuture.supplyAsync {
            try {
                val body = gson.toJson(request).toRequestBody(jsonMediaType)
                val httpRequest = Request.Builder()
                    .url("$baseUrl/api/v1/punishments/revoke")
                    .post(body)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        gson.fromJson(responseBody, PunishmentResponse::class.java)
                    } else {
                        PunishmentResponse(
                            success = false,
                            target = request.target,
                            type = request.type,
                            message = "API Error: ${response.code} - $responseBody"
                        )
                    }
                }
            } catch (e: Exception) {
                LobbyPlugin.logger.error("Failed to revoke punishment via API", e)
                PunishmentResponse(
                    success = false,
                    target = request.target,
                    type = request.type,
                    message = "Connection error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Check punishment history for a player
     */
    fun checkPunishments(playerName: String): CompletableFuture<PunishmentHistoryResponse> {
        return CompletableFuture.supplyAsync {
            try {
                val httpRequest = Request.Builder()
                    .url("$baseUrl/api/v1/punishments/$playerName")
                    .get()
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        gson.fromJson(responseBody, PunishmentHistoryResponse::class.java)
                    } else {
                        PunishmentHistoryResponse(
                            target = playerName,
                            targetId = null,
                            totalPunishments = 0,
                            activePunishments = 0,
                            punishments = emptyList(),
                            error = "API Error: ${response.code} - $responseBody"
                        )
                    }
                }
            } catch (e: Exception) {
                LobbyPlugin.logger.error("Failed to check punishments via API", e)
                PunishmentHistoryResponse(
                    target = playerName,
                    targetId = null,
                    totalPunishments = 0,
                    activePunishments = 0,
                    punishments = emptyList(),
                    error = "Connection error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Execute a command via the Radium proxy
     */
    fun executeCommand(staffPlayer: String, command: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val request = CommandExecuteRequest(staffPlayer, command)
                val body = gson.toJson(request).toRequestBody(jsonMediaType)
                val httpRequest = Request.Builder()
                    .url("$baseUrl/api/v1/commands/execute")
                    .post(body)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                LobbyPlugin.logger.error("Failed to execute command via API", e)
                false
            }
        }
    }
}

// Data classes for API requests and responses
data class PunishmentRequest(
    val target: String,
    val type: String,
    val reason: String,
    val staffId: String,
    val duration: String? = null,
    val silent: Boolean = false,
    val clearInventory: Boolean = false
)

data class PunishmentRevokeRequest(
    val target: String,
    val type: String,
    val reason: String,
    val staffId: String,
    val silent: Boolean = false
)

data class PunishmentResponse(
    val success: Boolean,
    val target: String,
    val type: String,
    val reason: String? = null,
    val staff: String? = null,
    val message: String
)

data class PunishmentHistoryResponse(
    val target: String,
    val targetId: String?,
    val totalPunishments: Int,
    val activePunishments: Int,
    val punishments: List<PunishmentEntry>,
    val error: String? = null
)

data class PunishmentEntry(
    val id: String,
    val type: String,
    val reason: String,
    val issuedBy: String,
    val issuedAt: Long,
    val expiresAt: Long?,
    val active: Boolean,
    val revokedBy: String?,
    val revokedAt: Long?,
    val revokeReason: String?
)

data class CommandExecuteRequest(
    val player: String,
    val command: String
)
