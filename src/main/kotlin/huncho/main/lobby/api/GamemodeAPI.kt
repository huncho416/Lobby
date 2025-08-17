package huncho.main.lobby.api

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.config.ConfigManager
import io.javalin.Javalin
import io.javalin.http.Context
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * HTTP API for handling gamemode changes from Radium proxy
 * Replaces Redis-based communication for gamemode synchronization
 */
class GamemodeAPI(private val configManager: ConfigManager) {
    
    private val logger: Logger = LoggerFactory.getLogger(GamemodeAPI::class.java)
    private var javalinApp: Javalin? = null
    
    fun initialize() {
        logger.info("Initializing Gamemode HTTP API...")
        
        val apiEnabled = configManager.getBoolean(configManager.mainConfig, "http_api.enabled", true)
        if (!apiEnabled) {
            logger.info("HTTP API is disabled in config")
            return
        }
        
        val host = configManager.getString(configManager.mainConfig, "http_api.host", "0.0.0.0")
        val port = configManager.getInt(configManager.mainConfig, "http_api.port", 8081)
        
        try {
            javalinApp = Javalin.create { config ->
                config.showJavalinBanner = false
                // Simple CORS configuration for Javalin 5.x
                config.plugins.enableCors { cors ->
                    cors.add { corsPluginConfig ->
                        corsPluginConfig.anyHost()
                    }
                }
            }.start(host, port)
            
            setupRoutes()
            
            logger.info("Gamemode HTTP API started on $host:$port")
        } catch (e: Exception) {
            logger.error("Failed to start HTTP API", e)
            throw e
        }
    }
    
    private fun setupRoutes() {
        val gamemodeEndpoint = configManager.getString(configManager.mainConfig, "http_api.endpoints.gamemode", "/api/gamemode")
        
        javalinApp?.post(gamemodeEndpoint) { ctx ->
            handleGamemodeChange(ctx)
        }
        
        // Health check endpoint
        javalinApp?.get("/api/health") { ctx ->
            ctx.json(mapOf(
                "status" to "ok",
                "server" to "lobby",
                "players" to MinecraftServer.getConnectionManager().onlinePlayers.size
            ))
        }
        
        logger.info("API routes configured:")
        logger.info("  POST $gamemodeEndpoint - Gamemode changes")
        logger.info("  GET /api/health - Health check")
    }
    
    private fun handleGamemodeChange(ctx: Context) {
        try {
            // Parse request body
            val requestBody = ctx.body()
            if (requestBody.isBlank()) {
                ctx.status(400).json(mapOf("error" to "Request body is required"))
                return
            }
            
            val data = if (requestBody.startsWith("{")) {
                // JSON format
                parseJsonRequest(requestBody)
            } else {
                // Simple format: playerId=UUID,gamemode=creative,staff=StaffName
                parseSimpleRequest(requestBody)
            }
            
            if (data == null) {
                ctx.status(400).json(mapOf("error" to "Invalid request format"))
                return
            }
            
            val result = applyGamemodeChange(data)
            
            if (result.success) {
                ctx.status(200).json(mapOf(
                    "success" to true,
                    "message" to result.message
                ))
            } else {
                ctx.status(404).json(mapOf(
                    "success" to false,
                    "error" to result.message
                ))
            }
            
        } catch (e: Exception) {
            logger.error("Error handling gamemode change request", e)
            ctx.status(500).json(mapOf(
                "success" to false,
                "error" to "Internal server error: ${e.message}"
            ))
        }
    }
    
    private fun parseJsonRequest(json: String): GamemodeChangeRequest? {
        return try {
            // Simple JSON parsing without Jackson dependency
            val playerId = extractJsonValue(json, "playerId") ?: extractJsonValue(json, "target")
            val gamemode = extractJsonValue(json, "gamemode")
            val staff = extractJsonValue(json, "staff") ?: extractJsonValue(json, "executor")
            
            if (playerId != null && gamemode != null) {
                GamemodeChangeRequest(playerId, gamemode, staff)
            } else null
        } catch (e: Exception) {
            logger.error("Failed to parse JSON request: $json", e)
            null
        }
    }
    
    private fun parseSimpleRequest(request: String): GamemodeChangeRequest? {
        return try {
            // Parse: playerId=UUID,gamemode=creative,staff=StaffName
            val data = request.split(",").associate { 
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else parts[0].trim() to ""
            }
            
            val playerId = data["playerId"]
            val gamemode = data["gamemode"]
            val staff = data["staff"]
            
            if (playerId != null && gamemode != null) {
                GamemodeChangeRequest(playerId, gamemode, staff)
            } else null
        } catch (e: Exception) {
            logger.error("Failed to parse simple request: $request", e)
            null
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        return try {
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
            val regex = Regex(pattern)
            val match = regex.find(json)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun applyGamemodeChange(request: GamemodeChangeRequest): GamemodeChangeResult {
        try {
            val targetUuid = UUID.fromString(request.playerId)
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetUuid)
            
            if (player == null) {
                val message = "Player ${request.playerId} not found on this server"
                logger.debug(message)
                return GamemodeChangeResult(false, message)
            }
            
            val gamemode = parseGamemode(request.gamemode)
            if (gamemode == null) {
                val message = "Invalid gamemode: ${request.gamemode}"
                logger.warn(message)
                return GamemodeChangeResult(false, message)
            }
            
            // Apply the gamemode change
            player.gameMode = gamemode
            
            val message = if (request.staff != null) {
                "Gamemode changed to ${gamemode.name.lowercase()} for ${player.username} by ${request.staff}"
            } else {
                "Gamemode changed to ${gamemode.name.lowercase()} for ${player.username}"
            }
            
            logger.info(message)
            return GamemodeChangeResult(true, message)
            
        } catch (e: IllegalArgumentException) {
            val message = "Invalid UUID format: ${request.playerId}"
            logger.warn(message)
            return GamemodeChangeResult(false, message)
        } catch (e: Exception) {
            val message = "Failed to apply gamemode change: ${e.message}"
            logger.error(message, e)
            return GamemodeChangeResult(false, message)
        }
    }
    
    private fun parseGamemode(gamemodeString: String): GameMode? {
        return when (gamemodeString.lowercase()) {
            "survival", "s", "0" -> GameMode.SURVIVAL
            "creative", "c", "1" -> GameMode.CREATIVE
            "adventure", "a", "2" -> GameMode.ADVENTURE
            "spectator", "sp", "3" -> GameMode.SPECTATOR
            else -> null
        }
    }
    
    fun shutdown() {
        logger.info("Shutting down Gamemode HTTP API...")
        javalinApp?.stop()
        javalinApp = null
        logger.info("Gamemode HTTP API shut down")
    }
    
    private data class GamemodeChangeRequest(
        val playerId: String,
        val gamemode: String,
        val staff: String?
    )
    
    private data class GamemodeChangeResult(
        val success: Boolean,
        val message: String
    )
}
