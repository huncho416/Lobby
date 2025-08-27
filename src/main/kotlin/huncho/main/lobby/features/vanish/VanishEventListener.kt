package huncho.main.lobby.features.vanish

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Handles vanish events from the Radium proxy and updates player visibility accordingly
 * Since Redis is not being used, this class provides methods for manual vanish event handling
 * that can be integrated with HTTP webhooks or polling systems
 */
class VanishEventListener(private val plugin: LobbyPlugin) {
    
    /**
     * Handle a vanish event message
     * Expected format: "uuid=player-uuid,vanished=true/false,action=vanish/unvanish"
     */
    fun handleVanishEvent(message: String) {
        try {
            val parts = message.split(",").associate { 
                val (key, value) = it.split("=", limit = 2)
                key to value
            }
            
            val uuid = UUID.fromString(parts["uuid"] ?: return)
            val isVanished = parts["vanished"]?.toBoolean() ?: false
            val action = parts["action"] ?: "unknown"
            
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == uuid }
            if (player != null) {
                CompletableFuture.runAsync {
                    runBlocking {
                        // Update player visibility for vanish status
                        plugin.visibilityManager.updatePlayerVisibilityForVanish(player)
                        
                        // Update tab list to reflect vanish status
                        plugin.tabListManager.updatePlayerTabList(player)
                        
                        // Update tab list for all other players to show/hide vanish indicator
                        MinecraftServer.getConnectionManager().onlinePlayers.forEach { otherPlayer ->
                            if (otherPlayer != player) {
                                plugin.tabListManager.updatePlayerTabList(otherPlayer)
                            }
                        }
                        
                        plugin.logger.info("Processed vanish event for ${player.username}: $action (vanished: $isVanished)")
                    }
                }
            } else {
                plugin.logger.debug("Received vanish event for offline player: $uuid")
            }
        } catch (e: Exception) {
            plugin.logger.error("Error handling vanish event: $message", e)
        }
    }
    
    /**
     * Handle a batch of vanish events (for efficiency)
     */
    fun handleVanishEvents(events: List<String>) {
        CompletableFuture.runAsync {
            runBlocking {
                events.forEach { event ->
                    handleVanishEvent(event)
                }
            }
        }
    }
    
    /**
     * Process a vanish status change for a specific player (direct API call)
     */
    suspend fun processVanishStatusChange(playerUuid: UUID) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player != null) {
                val isVanished = plugin.radiumIntegration.isPlayerVanished(playerUuid).join()
                
                // Create synthetic event message
                val eventMessage = "uuid=$playerUuid,vanished=$isVanished,action=${if (isVanished) "vanish" else "unvanish"}"
                handleVanishEvent(eventMessage)
            }
        } catch (e: Exception) {
            plugin.logger.error("Error processing vanish status change for $playerUuid", e)
        }
    }
    
    /**
     * Force refresh vanish status for all online players
     */
    suspend fun refreshAllVanishStatuses() {
        try {
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            plugin.logger.info("Refreshing vanish status for ${onlinePlayers.size} online players")
            
            onlinePlayers.forEach { player ->
                processVanishStatusChange(player.uuid)
            }
        } catch (e: Exception) {
            plugin.logger.error("Error refreshing all vanish statuses", e)
        }
    }
}
