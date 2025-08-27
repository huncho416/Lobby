package huncho.main.lobby.redis

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.MuteUpdate
import huncho.main.lobby.models.PunishmentUpdate
import huncho.main.lobby.models.UpdateAction
import io.lettuce.core.pubsub.RedisPubSubListener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Listens for real-time punishment and mute updates from Redis
 */
class RedisEventListener(
    private val plugin: LobbyPlugin,
    private val redisCache: RedisCache
) : RedisPubSubListener<String, String> {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RedisEventListener::class.java)
    }
    
    private val gson = Gson()
    
    override fun message(channel: String, message: String) {
        try {
            when (channel) {
                getPunishmentChannel() -> handlePunishmentUpdate(message)
                getMuteChannel() -> handleMuteUpdate(message)
                else -> logger.debug("Received message on unknown channel: $channel")
            }
        } catch (e: Exception) {
            logger.error("Error processing Redis message on channel $channel", e)
        }
    }
    
    override fun message(pattern: String, channel: String, message: String) {
        // Handle pattern-based subscriptions if needed
        message(channel, message)
    }
    
    override fun subscribed(channel: String, count: Long) {
        logger.info("Subscribed to Redis channel: $channel (total subscriptions: $count)")
    }
    
    override fun psubscribed(pattern: String, count: Long) {
        logger.info("Pattern subscribed to Redis pattern: $pattern (total subscriptions: $count)")
    }
    
    override fun unsubscribed(channel: String, count: Long) {
        logger.info("Unsubscribed from Redis channel: $channel (remaining subscriptions: $count)")
    }
    
    override fun punsubscribed(pattern: String, count: Long) {
        logger.info("Pattern unsubscribed from Redis pattern: $pattern (remaining subscriptions: $count)")
    }
    
    /**
     * Handle punishment updates from Redis
     */
    private fun handlePunishmentUpdate(message: String) {
        try {
            val update = gson.fromJson(message, PunishmentUpdate::class.java)
            val punishment = update.punishment
            
            logger.debug("Received punishment update: ${update.action} for player ${punishment.playerName}")
            
            when (update.action) {
                UpdateAction.CREATE, UpdateAction.UPDATE -> {
                    // Cache the new/updated punishment
                    redisCache.cacheActivePunishment(punishment)
                    
                    // If it's an active ban, disconnect the player if they're online
                    if (punishment.isCurrentlyActive() && punishment.type.preventsJoin()) {
                        disconnectPlayer(punishment.playerUuid, punishment.getDisplayReason())
                    }
                }
                UpdateAction.DELETE, UpdateAction.EXPIRE -> {
                    // Remove from cache
                    redisCache.removeActivePunishment(punishment.playerUuid)
                }
            }
            
        } catch (e: JsonSyntaxException) {
            logger.warn("Failed to parse punishment update from Redis: $message", e)
        } catch (e: Exception) {
            logger.error("Error handling punishment update", e)
        }
    }
    
    /**
     * Handle mute updates from Redis
     */
    private fun handleMuteUpdate(message: String) {
        try {
            val update = gson.fromJson(message, MuteUpdate::class.java)
            val mute = update.mute
            
            logger.debug("Received mute update: ${update.action} for player ${mute.playerName}")
            
            when (update.action) {
                UpdateAction.CREATE, UpdateAction.UPDATE -> {
                    // Cache the new/updated mute
                    redisCache.cacheActiveMute(mute)
                    
                    // Notify the player if they're online
                    if (mute.isCurrentlyActive()) {
                        notifyPlayerMuted(mute.playerUuid, mute.getDisplayReason())
                    }
                }
                UpdateAction.DELETE, UpdateAction.EXPIRE -> {
                    // Remove from cache
                    redisCache.removeActiveMute(mute.playerUuid)
                    
                    // Notify the player if they're online
                    notifyPlayerUnmuted(mute.playerUuid)
                }
            }
            
        } catch (e: JsonSyntaxException) {
            logger.warn("Failed to parse mute update from Redis: $message", e)
        } catch (e: Exception) {
            logger.error("Error handling mute update", e)
        }
    }
    
    /**
     * Disconnect a player due to ban
     */
    private fun disconnectPlayer(playerUuid: UUID, reason: String) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
            if (player != null) {
                val kickMessage = Component.text()
                    .append(Component.text("You have been banned!", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Reason: ", NamedTextColor.GRAY))
                    .append(Component.text(reason, NamedTextColor.WHITE))
                    .build()
                
                player.kick(kickMessage)
                logger.info("Disconnected player ${player.username} due to ban: $reason")
            }
        }
    }
    
    /**
     * Notify a player they have been muted
     */
    private fun notifyPlayerMuted(playerUuid: UUID, reason: String) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
            if (player != null) {
                val muteMessage = Component.text()
                    .append(Component.text("You have been muted!", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Reason: ", NamedTextColor.GRAY))
                    .append(Component.text(reason, NamedTextColor.WHITE))
                    .build()
                
                player.sendMessage(muteMessage)
                logger.info("Notified player ${player.username} of mute: $reason")
            }
        }
    }
    
    /**
     * Notify a player they have been unmuted
     */
    private fun notifyPlayerUnmuted(playerUuid: UUID) {
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
            if (player != null) {
                val unmuteMessage = Component.text("You have been unmuted!", NamedTextColor.GREEN)
                player.sendMessage(unmuteMessage)
                logger.info("Notified player ${player.username} of unmute")
            }
        }
    }
    
    /**
     * Get the punishment update channel from config
     */
    private fun getPunishmentChannel(): String {
        return plugin.configManager.getString(plugin.configManager.mainConfig, "redis.channels.punishment_updates", "radium:punishment:updates")
    }
    
    /**
     * Get the mute update channel from config
     */
    private fun getMuteChannel(): String {
        return plugin.configManager.getString(plugin.configManager.mainConfig, "redis.channels.mute_updates", "radium:mute:updates")
    }
}
