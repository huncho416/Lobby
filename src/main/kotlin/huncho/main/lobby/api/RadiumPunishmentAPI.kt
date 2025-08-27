package huncho.main.lobby.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import huncho.main.lobby.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * API client for Radium's punishment system
 * Uses the proper /api/punishments endpoints with correct DTOs
 */
class RadiumPunishmentAPI(private val radiumApiUrl: String = "http://localhost:8080") {
    
    private val logger = LoggerFactory.getLogger(RadiumPunishmentAPI::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Issue a punishment using Radium's /api/punishments/issue endpoint
     */
    suspend fun issuePunishment(request: PunishmentRequest): PunishmentApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(request).toRequestBody(jsonMediaType)
                
                val httpRequest = Request.Builder()
                    .url("$radiumApiUrl/api/punishments/issue")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                logger.debug("Issuing punishment via API: ${request.type} for ${request.target} by ${request.staffId}")

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    
                    when {
                        response.isSuccessful -> {
                            try {
                                val punishmentResponse = gson.fromJson(responseBody, PunishmentResponse::class.java)
                                logger.info("Successfully issued ${request.type} for ${request.target}: ${punishmentResponse.message}")
                                PunishmentApiResult.Success(punishmentResponse)
                            } catch (e: JsonSyntaxException) {
                                logger.error("Invalid JSON response from punishment API: $responseBody", e)
                                PunishmentApiResult.Error("Invalid response format from server")
                            }
                        }
                        response.code == 400 -> {
                            try {
                                val errorResponse = gson.fromJson(responseBody, ErrorResponse::class.java)
                                logger.warn("Bad request for punishment: ${errorResponse.message}")
                                PunishmentApiResult.Error(errorResponse.message)
                            } catch (e: JsonSyntaxException) {
                                PunishmentApiResult.Error("Bad request: $responseBody")
                            }
                        }
                        response.code == 404 -> {
                            PunishmentApiResult.Error("Player or staff member not found")
                        }
                        response.code == 500 -> {
                            logger.error("Server error when issuing punishment: $responseBody")
                            PunishmentApiResult.Error("Server error occurred")
                        }
                        else -> {
                            logger.error("Unexpected response from punishment API: ${response.code} - $responseBody")
                            PunishmentApiResult.Error("Unexpected error: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to connect to punishment API", e)
                PunishmentApiResult.Error("Connection error: ${e.message}")
            }
        }
    }

    /**
     * Revoke a punishment using Radium's /api/punishments/revoke endpoint
     */
    suspend fun revokePunishment(request: PunishmentRevokeRequest): PunishmentApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(request).toRequestBody(jsonMediaType)
                
                val httpRequest = Request.Builder()
                    .url("$radiumApiUrl/api/punishments/revoke")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                logger.debug("Revoking punishment via API: ${request.type} for ${request.target} by ${request.staffId}")

                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    
                    when {
                        response.isSuccessful -> {
                            try {
                                val punishmentResponse = gson.fromJson(responseBody, PunishmentResponse::class.java)
                                logger.info("Successfully revoked ${request.type} for ${request.target}: ${punishmentResponse.message}")
                                PunishmentApiResult.Success(punishmentResponse)
                            } catch (e: JsonSyntaxException) {
                                logger.error("Invalid JSON response from punishment API: $responseBody", e)
                                PunishmentApiResult.Error("Invalid response format from server")
                            }
                        }
                        response.code == 400 -> {
                            try {
                                val errorResponse = gson.fromJson(responseBody, ErrorResponse::class.java)
                                logger.warn("Bad request for punishment revocation: ${errorResponse.message}")
                                PunishmentApiResult.Error(errorResponse.message)
                            } catch (e: JsonSyntaxException) {
                                PunishmentApiResult.Error("Bad request: $responseBody")
                            }
                        }
                        response.code == 404 -> {
                            PunishmentApiResult.Error("Player, staff member, or punishment not found")
                        }
                        response.code == 500 -> {
                            logger.error("Server error when revoking punishment: $responseBody")
                            PunishmentApiResult.Error("Server error occurred")
                        }
                        else -> {
                            logger.error("Unexpected response from punishment API: ${response.code} - $responseBody")
                            PunishmentApiResult.Error("Unexpected error: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to connect to punishment API", e)
                PunishmentApiResult.Error("Connection error: ${e.message}")
            }
        }
    }

    /**
     * Get active punishments for a player
     */
    suspend fun getActivePunishments(playerUuid: String): List<Punishment> {
        return withContext(Dispatchers.IO) {
            try {
                val httpRequest = Request.Builder()
                    .url("$radiumApiUrl/api/punishments/player/$playerUuid")
                    .get()
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        try {
                            val punishments = gson.fromJson(responseBody, Array<Punishment>::class.java)
                            punishments?.filter { it.isCurrentlyActive() } ?: emptyList()
                        } catch (e: JsonSyntaxException) {
                            logger.error("Invalid JSON response when getting punishments: $responseBody", e)
                            emptyList()
                        }
                    } else {
                        logger.warn("Failed to get punishments for player $playerUuid: ${response.code}")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to get punishments for player $playerUuid", e)
                emptyList()
            }
        }
    }
}

/**
 * Result wrapper for punishment API operations
 */
sealed class PunishmentApiResult {
    data class Success(val response: PunishmentResponse) : PunishmentApiResult()
    data class Error(val message: String) : PunishmentApiResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getErrorMessage(): String? = (this as? Error)?.message
}
