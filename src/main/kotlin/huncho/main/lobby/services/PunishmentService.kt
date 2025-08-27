package huncho.main.lobby.services

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.integration.RadiumIntegration
import huncho.main.lobby.models.Mute
import huncho.main.lobby.models.Punishment
import huncho.main.lobby.redis.RedisCache
import huncho.main.lobby.redis.RedisManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Service for checking punishments and mutes with Redis cache and HTTP API fallback
 */
class PunishmentService(
    private val plugin: LobbyPlugin,
    private val radiumIntegration: RadiumIntegration,
    private val redisManager: RedisManager,
    private val redisCache: RedisCache
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PunishmentService::class.java)
    }
    
    /**
     * Check if a player is banned (Redis cache first, HTTP API fallback)
     */
    fun isPlayerBanned(playerUuid: UUID): CompletableFuture<Boolean> {
        return if (redisManager.isConnected()) {
            // Try Redis cache first
            redisCache.hasActiveBan(playerUuid)
                .thenCompose { hasBan ->
                    if (hasBan) {
                        CompletableFuture.completedFuture(true)
                    } else {
                        // Fallback to HTTP API
                        checkBanViaAPI(playerUuid)
                    }
                }
                .exceptionally { e ->
                    logger.warn("Error checking ban status for $playerUuid via Redis, falling back to API", e)
                    // Fallback to HTTP API on Redis error
                    checkBanViaAPI(playerUuid).join()
                }
        } else {
            // Redis not available, use HTTP API directly
            checkBanViaAPI(playerUuid)
        }
    }
    
    /**
     * Get active punishment for a player (Redis cache first, HTTP API fallback)
     */
    fun getActivePunishment(playerUuid: UUID): CompletableFuture<Punishment?> {
        return if (redisManager.isConnected()) {
            // Try Redis cache first
            redisCache.getActivePunishment(playerUuid)
                .thenCompose { punishment ->
                    if (punishment != null && punishment.isCurrentlyActive()) {
                        CompletableFuture.completedFuture(punishment)
                    } else {
                        // Fallback to HTTP API
                        fetchPunishmentViaAPI(playerUuid)
                    }
                }
                .exceptionally { e ->
                    logger.warn("Error getting punishment for $playerUuid via Redis, falling back to API", e)
                    // Fallback to HTTP API on Redis error
                    fetchPunishmentViaAPI(playerUuid).join()
                }
        } else {
            // Redis not available, use HTTP API directly
            fetchPunishmentViaAPI(playerUuid)
        }
    }
    
    /**
     * Check if a player is muted (Redis cache first, HTTP API fallback)
     */
    fun isPlayerMuted(playerUuid: UUID): CompletableFuture<Boolean> {
        return if (redisManager.isConnected()) {
            // Try Redis cache first
            redisCache.isMuted(playerUuid)
                .thenCompose { isMuted ->
                    if (isMuted) {
                        CompletableFuture.completedFuture(true)
                    } else {
                        // Fallback to HTTP API
                        checkMuteViaAPI(playerUuid)
                    }
                }
                .exceptionally { e ->
                    logger.warn("Error checking mute status for $playerUuid via Redis, falling back to API", e)
                    // Fallback to HTTP API on Redis error
                    checkMuteViaAPI(playerUuid).join()
                }
        } else {
            // Redis not available, use HTTP API directly
            checkMuteViaAPI(playerUuid)
        }
    }
    
    /**
     * Get active mute for a player (Redis cache first, HTTP API fallback)
     */
    fun getActiveMute(playerUuid: UUID): CompletableFuture<Mute?> {
        return if (redisManager.isConnected()) {
            // Try Redis cache first
            redisCache.getActiveMute(playerUuid)
                .thenCompose { mute ->
                    if (mute != null && mute.isCurrentlyActive()) {
                        CompletableFuture.completedFuture(mute)
                    } else {
                        // Fallback to HTTP API
                        fetchMuteViaAPI(playerUuid)
                    }
                }
                .exceptionally { e ->
                    logger.warn("Error getting mute for $playerUuid via Redis, falling back to API", e)
                    // Fallback to HTTP API on Redis error
                    fetchMuteViaAPI(playerUuid).join()
                }
        } else {
            // Redis not available, use HTTP API directly
            fetchMuteViaAPI(playerUuid)
        }
    }
    
    /**
     * Clear cached data for a player
     */
    fun clearPlayerCache(playerUuid: UUID): CompletableFuture<Boolean> {
        return if (redisManager.isConnected()) {
            redisCache.clearPlayerCache(playerUuid)
        } else {
            CompletableFuture.completedFuture(true)
        }
    }
    
    /**
     * Check ban status via Radium HTTP API using our RadiumPunishmentAPI
     */
    private fun checkBanViaAPI(playerUuid: UUID): CompletableFuture<Boolean> {
        return GlobalScope.future {
            try {
                val punishments = plugin.radiumPunishmentAPI.getActivePunishments(playerUuid.toString())
                punishments.any { it.type.name.equals("BAN", ignoreCase = true) && it.isCurrentlyActive() }
            } catch (e: Exception) {
                logger.error("Error checking ban status via API for $playerUuid", e)
                false
            }
        }
    }
    
    /**
     * Fetch punishment via Radium HTTP API using our RadiumPunishmentAPI
     */
    private fun fetchPunishmentViaAPI(playerUuid: UUID): CompletableFuture<Punishment?> {
        return GlobalScope.future {
            try {
                val punishments = plugin.radiumPunishmentAPI.getActivePunishments(playerUuid.toString())
                val activeBan = punishments.find { 
                    it.type.name.equals("BAN", ignoreCase = true) && it.isCurrentlyActive() 
                }
                activeBan
            } catch (e: Exception) {
                logger.error("Error fetching punishment via API for $playerUuid", e)
                null
            }
        }
    }
    
    /**
     * Check mute status via Radium HTTP API using our RadiumPunishmentAPI
     */
    private fun checkMuteViaAPI(playerUuid: UUID): CompletableFuture<Boolean> {
        return GlobalScope.future {
            try {
                val punishments = plugin.radiumPunishmentAPI.getActivePunishments(playerUuid.toString())
                punishments.any { it.type.name.equals("MUTE", ignoreCase = true) && it.isCurrentlyActive() }
            } catch (e: Exception) {
                logger.error("Error checking mute status via API for $playerUuid", e)
                false
            }
        }
    }
    
    /**
     * Fetch mute via Radium HTTP API using our RadiumPunishmentAPI
     */
    private fun fetchMuteViaAPI(playerUuid: UUID): CompletableFuture<Mute?> {
        return GlobalScope.future {
            try {
                val punishments = plugin.radiumPunishmentAPI.getActivePunishments(playerUuid.toString())
                val activeMute = punishments.find { 
                    it.type.name.equals("MUTE", ignoreCase = true) && it.isCurrentlyActive() 
                }
                // Convert Punishment to Mute if needed
                activeMute?.let { punishment ->
                    Mute(
                        id = punishment.id,
                        playerUuid = punishment.playerUuid,
                        playerName = punishment.playerName,
                        reason = punishment.reason,
                        muterUuid = punishment.punisherUuid,
                        muterName = punishment.punisherName,
                        createdAt = punishment.createdAt,
                        expiresAt = punishment.expiresAt,
                        active = punishment.active,
                        server = punishment.server
                    )
                }
            } catch (e: Exception) {
                logger.error("Error fetching mute via API for $playerUuid", e)
                null
            }
        }
    }
}
