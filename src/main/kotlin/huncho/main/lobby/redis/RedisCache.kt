package huncho.main.lobby.redis

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.Mute
import huncho.main.lobby.models.Punishment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Redis cache helper for punishment and mute data
 */
class RedisCache(
    private val plugin: LobbyPlugin,
    private val redisManager: RedisManager
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RedisCache::class.java)
        private const val PUNISHMENT_KEY_PREFIX = "radium:punishment:"
        private const val MUTE_KEY_PREFIX = "radium:mute:"
        private const val ACTIVE_PUNISHMENT_KEY_PREFIX = "radium:active_punishment:"
        private const val ACTIVE_MUTE_KEY_PREFIX = "radium:active_mute:"
    }
    
    private val gson = Gson()
    
    /**
     * Get active punishment for a player from cache
     */
    fun getActivePunishment(playerUuid: UUID): CompletableFuture<Punishment?> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync null
                val key = "$ACTIVE_PUNISHMENT_KEY_PREFIX$playerUuid"
                val json = commands.get(key) ?: return@supplyAsync null
                
                gson.fromJson(json, Punishment::class.java)
            } catch (e: JsonSyntaxException) {
                logger.warn("Failed to parse punishment from cache for player $playerUuid", e)
                null
            } catch (e: Exception) {
                logger.error("Error getting punishment from cache for player $playerUuid", e)
                null
            }
        }
    }
    
    /**
     * Cache active punishment for a player
     */
    fun cacheActivePunishment(punishment: Punishment): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync false
                val key = "$ACTIVE_PUNISHMENT_KEY_PREFIX${punishment.playerUuid}"
                val json = gson.toJson(punishment)
                
                val ttl = plugin.configManager.getLong(plugin.configManager.mainConfig, "redis.cache.punishment_ttl", 300)
                
                commands.setex(key, ttl, json)
                true
            } catch (e: Exception) {
                logger.error("Error caching punishment for player ${punishment.playerUuid}", e)
                false
            }
        }
    }
    
    /**
     * Get active mute for a player from cache
     */
    fun getActiveMute(playerUuid: UUID): CompletableFuture<Mute?> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync null
                val key = "$ACTIVE_MUTE_KEY_PREFIX$playerUuid"
                val json = commands.get(key) ?: return@supplyAsync null
                
                gson.fromJson(json, Mute::class.java)
            } catch (e: JsonSyntaxException) {
                logger.warn("Failed to parse mute from cache for player $playerUuid", e)
                null
            } catch (e: Exception) {
                logger.error("Error getting mute from cache for player $playerUuid", e)
                null
            }
        }
    }
    
    /**
     * Cache active mute for a player
     */
    fun cacheActiveMute(mute: Mute): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync false
                val key = "$ACTIVE_MUTE_KEY_PREFIX${mute.playerUuid}"
                val json = gson.toJson(mute)
                
                val ttl = plugin.configManager.getLong(plugin.configManager.mainConfig, "redis.cache.mute_ttl", 300)
                
                commands.setex(key, ttl, json)
                true
            } catch (e: Exception) {
                logger.error("Error caching mute for player ${mute.playerUuid}", e)
                false
            }
        }
    }
    
    /**
     * Remove punishment from cache
     */
    fun removeActivePunishment(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync false
                val key = "$ACTIVE_PUNISHMENT_KEY_PREFIX$playerUuid"
                commands.del(key) > 0
            } catch (e: Exception) {
                logger.error("Error removing punishment from cache for player $playerUuid", e)
                false
            }
        }
    }
    
    /**
     * Remove mute from cache
     */
    fun removeActiveMute(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync false
                val key = "$ACTIVE_MUTE_KEY_PREFIX$playerUuid"
                commands.del(key) > 0
            } catch (e: Exception) {
                logger.error("Error removing mute from cache for player $playerUuid", e)
                false
            }
        }
    }
    
    /**
     * Check if player has any active punishment (ban)
     */
    fun hasActiveBan(playerUuid: UUID): CompletableFuture<Boolean> {
        return getActivePunishment(playerUuid).thenApply { punishment ->
            punishment?.let { it.isCurrentlyActive() && it.type.preventsJoin() } ?: false
        }
    }
    
    /**
     * Check if player is currently muted
     */
    fun isMuted(playerUuid: UUID): CompletableFuture<Boolean> {
        return getActiveMute(playerUuid).thenApply { mute ->
            mute?.isCurrentlyActive() ?: false
        }
    }
    
    /**
     * Clear all cached data for a player
     */
    fun clearPlayerCache(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val commands = redisManager.getCommands() ?: return@supplyAsync false
                val punishmentKey = "$ACTIVE_PUNISHMENT_KEY_PREFIX$playerUuid"
                val muteKey = "$ACTIVE_MUTE_KEY_PREFIX$playerUuid"
                
                commands.del(punishmentKey, muteKey)
                true
            } catch (e: Exception) {
                logger.error("Error clearing cache for player $playerUuid", e)
                false
            }
        }
    }
}
