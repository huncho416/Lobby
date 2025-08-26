package huncho.main.lobby.features.vanish

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.TaskSchedule
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Monitors vanish status changes for online players and updates visibility accordingly
 */
class VanishStatusMonitor(private val plugin: LobbyPlugin) {
    
    private val lastKnownVanishStatus = ConcurrentHashMap<UUID, Boolean>()
    private var isEnabled = false
    
    fun initialize() {
        isEnabled = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "radium.vanish.respect_status", true)
        
        if (!isEnabled) {
            plugin.logger.info("Vanish status monitoring is disabled")
            return
        }
        
        startMonitoringTask()
        plugin.logger.info("Vanish status monitor initialized")
    }
    
    fun shutdown() {
        lastKnownVanishStatus.clear()
        plugin.logger.info("Vanish status monitor shut down")
    }
    
    /**
     * Start the recurring task to check vanish status changes
     */
    private fun startMonitoringTask() {
        val scheduler = MinecraftServer.getSchedulerManager()
        
        scheduler.submitTask {
            if (isEnabled) {
                runBlocking {
                    checkVanishStatusChanges()
                }
            }
            TaskSchedule.tick(60) // Check every 3 seconds (60 ticks)
        }
    }
    
    /**
     * Check for vanish status changes among online players
     */
    private suspend fun checkVanishStatusChanges() {
        try {
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            
            onlinePlayers.forEach { player ->
                try {
                    // Get current vanish status
                    val currentVanishStatus = plugin.radiumIntegration.isPlayerVanished(player.uuid).join()
                    val lastKnownStatus = lastKnownVanishStatus[player.uuid]
                    
                    // Check if status changed
                    if (lastKnownStatus == null || lastKnownStatus != currentVanishStatus) {
                        lastKnownVanishStatus[player.uuid] = currentVanishStatus
                        
                        // Use the vanish event listener to handle the change
                        val eventMessage = "uuid=${player.uuid},vanished=$currentVanishStatus,action=${if (currentVanishStatus) "vanish" else "unvanish"}"
                        plugin.vanishEventListener.handleVanishEvent(eventMessage)
                        
                        plugin.logger.debug("Vanish status change detected for ${player.username}: ${if (currentVanishStatus) "vanished" else "visible"}")
                    }
                } catch (e: Exception) {
                    plugin.logger.warn("Error checking vanish status for ${player.username}", e)
                }
            }
            
            // Clean up offline players from tracking
            val onlineUuids = onlinePlayers.map { it.uuid }.toSet()
            lastKnownVanishStatus.keys.retainAll(onlineUuids)
            
        } catch (e: Exception) {
            plugin.logger.error("Error during vanish status monitoring", e)
        }
    }
    
    /**
     * Force check vanish status for a specific player
     */
    suspend fun forceCheckPlayer(playerUuid: UUID) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player != null) {
                val currentVanishStatus = plugin.radiumIntegration.isPlayerVanished(playerUuid).join()
                lastKnownVanishStatus[playerUuid] = currentVanishStatus
                
                // Use the vanish event listener to handle the change
                plugin.vanishEventListener.processVanishStatusChange(playerUuid)
                
                plugin.logger.debug("Force checked vanish status for ${player.username}: ${if (currentVanishStatus) "vanished" else "visible"}")
            }
        } catch (e: Exception) {
            plugin.logger.error("Error force checking vanish status for $playerUuid", e)
        }
    }
    
    /**
     * Remove player from tracking when they leave
     */
    fun removePlayer(playerUuid: UUID) {
        lastKnownVanishStatus.remove(playerUuid)
    }
    
    /**
     * Get current vanish status tracking information
     */
    fun getTrackingInfo(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "tracked_players" to lastKnownVanishStatus.size,
            "vanished_count" to lastKnownVanishStatus.values.count { it }
        )
    }
}
