package huncho.main.lobby.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.VanishData
import huncho.main.lobby.models.VanishLevel
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerPluginMessageEvent
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles plugin messages from Radium proxy for vanish state synchronization
 * Channel: radium:vanish
 */
class VanishPluginMessageListener(private val plugin: LobbyPlugin) : EventListener<PlayerPluginMessageEvent> {
    
    private val gson = Gson()
    private val vanishedPlayers = ConcurrentHashMap<UUID, VanishData>()
    
    override fun eventType(): Class<PlayerPluginMessageEvent> = PlayerPluginMessageEvent::class.java

    override fun run(event: PlayerPluginMessageEvent): EventListener.Result {
        // Check if this is a vanish message
        if (event.identifier != "radium:vanish") {
            return EventListener.Result.SUCCESS
        }
        
        try {
            val messageData = String(event.message)
            plugin.logger.debug("Received vanish plugin message: $messageData")
            
            val jsonObject = gson.fromJson(messageData, JsonObject::class.java)
            val action = jsonObject.get("action")?.asString ?: jsonObject.get("type")?.asString ?: return EventListener.Result.SUCCESS
            
            when (action) {
                "set_vanish", "VANISH_STATE", "vanish" -> handleVanishStateChange(jsonObject)
                "batch_update", "VANISH_BATCH_UPDATE" -> handleBatchUpdate(jsonObject)
                "remove_vanish", "unvanish" -> handleUnvanish(jsonObject)
                else -> plugin.logger.debug("Unknown vanish action: $action")
            }
            
        } catch (e: Exception) {
            plugin.logger.error("Error processing vanish plugin message", e)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Handle individual vanish state change
     * FIXED: Enhanced entity visibility updates for proper vanish functionality
     */
    private fun handleVanishStateChange(data: JsonObject) {
        try {
            // Handle both "player_id" and "player" keys for compatibility
            val playerUuid = UUID.fromString(
                data.get("player_id")?.asString ?: data.get("player")?.asString ?: return
            )
            val vanished = data.get("vanished")?.asBoolean ?: return
            val levelString = data.get("level")?.asString
            val levelInt = data.get("level")?.asInt
            val vanishedByString = data.get("vanishedBy")?.asString ?: data.get("vanished_by")?.asString
            val reason = data.get("reason")?.asString
            
            plugin.logger.debug("Processing vanish state change: player=$playerUuid, vanished=$vanished, level=${levelString ?: levelInt}")
            
            if (vanished) {
                // Player is being vanished - handle both string and integer levels
                val level = when {
                    levelString != null -> VanishLevel.fromString(levelString)
                    levelInt != null -> VanishLevel.fromLevel(levelInt)
                    else -> VanishLevel.HELPER
                }
                val vanishedBy = vanishedByString?.let { UUID.fromString(it) }
                
                val vanishData = VanishData.create(
                    playerId = playerUuid,
                    level = level,
                    vanishedBy = vanishedBy,
                    reason = reason
                )
                
                vanishedPlayers[playerUuid] = vanishData
                plugin.logger.info("Player $playerUuid vanished at level ${level.displayName}")
            } else {
                // Player is being unvanished
                vanishedPlayers.remove(playerUuid)
                plugin.logger.info("Player $playerUuid unvanished")
            }
            
            // CRITICAL: Update entity visibility immediately for all players  
            updatePlayerVisibilityComprehensive(playerUuid)
            
        } catch (e: Exception) {
            plugin.logger.error("Error handling vanish state change", e)
        }
    }
    
    /**
     * Handle batch vanish updates for performance
     */
    private fun handleBatchUpdate(data: JsonObject) {
        try {
            val updates = data.getAsJsonArray("updates") ?: return
            
            updates.forEach { updateElement ->
                val update = updateElement.asJsonObject
                // Handle both "player_id" and "player" keys
                val playerUuid = UUID.fromString(
                    update.get("player_id")?.asString ?: update.get("player")?.asString ?: return@forEach
                )
                val vanished = update.get("vanished")?.asBoolean ?: return@forEach
                
                if (vanished) {
                    // Handle both string and integer levels
                    val level = when {
                        update.get("level")?.asString != null -> VanishLevel.valueOf(update.get("level").asString.uppercase())
                        update.get("level")?.asInt != null -> VanishLevel.fromLevel(update.get("level").asInt)
                        else -> VanishLevel.HELPER
                    }
                    val vanishData = VanishData.create(playerUuid, level)
                    vanishedPlayers[playerUuid] = vanishData
                } else {
                    vanishedPlayers.remove(playerUuid)
                }
                
                // Update visibility for this player
                updatePlayerVisibility(playerUuid)
            }
            
            plugin.logger.debug("Processed batch vanish update with ${updates.size()} changes")
            
        } catch (e: Exception) {
            plugin.logger.error("Error handling batch vanish update", e)
        }
    }
    
    /**
     * Handle unvanish message
     */
    private fun handleUnvanish(data: JsonObject) {
        try {
            // Handle both "player_id" and "player" keys
            val playerUuid = UUID.fromString(
                data.get("player_id")?.asString ?: data.get("player")?.asString ?: return
            )
            vanishedPlayers.remove(playerUuid)
            updatePlayerVisibility(playerUuid)
            plugin.logger.info("Player $playerUuid unvanished via remove message")
        } catch (e: Exception) {
            plugin.logger.error("Error handling unvanish message", e)
        }
    }
    
    /**
     * Update visibility for a specific player with enhanced refresh logic
     */
    private fun updatePlayerVisibility(changedPlayerUuid: UUID) {
        runBlocking {
            val changedPlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == changedPlayerUuid }
            
            // Update visibility for the changed player if they're online
            if (changedPlayer != null) {
                plugin.visibilityManager.updatePlayerVisibilityForVanish(changedPlayer)
                plugin.tabListManager.updatePlayerTabList(changedPlayer)
                
                // For each other player, update their visibility to the changed player
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                    if (viewer.uuid != changedPlayerUuid) {
                        updateVisibilityBetweenPlayers(viewer, changedPlayer)
                    }
                }
            }
            
            // Update visibility for all other players to see the changed player correctly
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                if (viewer.uuid != changedPlayerUuid) {
                    plugin.visibilityManager.updatePlayerVisibilityForVanish(viewer)
                    plugin.tabListManager.updatePlayerTabList(viewer)
                    
                    // Force refresh entity visibility specifically for the changed player
                    if (changedPlayer != null) {
                        refreshEntityVisibility(viewer, changedPlayer)
                    }
                }
            }
            
            // Force complete tab list refresh for major vanish changes
            plugin.tabListManager.refreshAllTabLists()
            
            plugin.logger.debug("Updated visibility for all players after vanish change for ${changedPlayer?.username ?: changedPlayerUuid}")
        }
    }
    
    /**
     * ENHANCED: Comprehensive visibility update for critical vanish functionality
     * Fixes the core issue where unvanished players don't reappear properly
     */
    private fun updatePlayerVisibilityComprehensive(changedPlayerUuid: UUID) {
        runBlocking {
            val changedPlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == changedPlayerUuid }
            val isVanished = isPlayerVanished(changedPlayerUuid)
            
            plugin.logger.debug("Comprehensive visibility update for ${changedPlayer?.username ?: changedPlayerUuid} (vanished: $isVanished)")
            
            if (changedPlayer != null) {
                // CRITICAL: Update visibility for all online players regarding this player
                updatePlayerVisibilityForAllViewers(changedPlayer, isVanished)
                
                // Update the changed player's own visibility and tab
                plugin.visibilityManager.updatePlayerVisibilityForVanish(changedPlayer)
            }
            
            // CRITICAL: Force complete refresh to ensure consistency (with new tab refresh method)
            plugin.tabListManager.refreshAllTabLists()
            
            // CRITICAL FIX: Also force individual tab refresh for the changed player
            if (changedPlayer != null) {
                plugin.tabListManager.forcePlayerTabRefresh(changedPlayer)
            }
            
            plugin.logger.info("✅ Completed comprehensive visibility update for ${changedPlayer?.username ?: changedPlayerUuid}")
        }
    }
    
    /**
     * ENHANCED: Update visibility for all viewers regarding a specific player
     * Ensures proper entity visibility and tab list entries
     */
    private suspend fun updatePlayerVisibilityForAllViewers(targetPlayer: Player, isVanished: Boolean) {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
            if (viewer.uuid != targetPlayer.uuid) {
                if (isVanished) {
                    // Player is vanished - check if viewer can see them
                    val canSee = canSeeVanished(viewer, targetPlayer.uuid)
                    if (canSee) {
                        // Show vanished player to authorized viewers (staff) with [V] indicator
                        showPlayerToViewer(viewer, targetPlayer)
                        plugin.tabListManager.showPlayerWithVanishIndicator(targetPlayer, viewer)
                        plugin.logger.debug("✅ Showing vanished ${targetPlayer.username} to authorized ${viewer.username}")
                    } else {
                        // Hide vanished player from unauthorized viewers (defaults)
                        hidePlayerFromViewer(viewer, targetPlayer)
                        plugin.tabListManager.hidePlayerFromTab(targetPlayer, viewer)
                        plugin.logger.debug("❌ Hiding vanished ${targetPlayer.username} from ${viewer.username}")
                    }
                } else {
                    // Player is not vanished - ensure visible to everyone
                    showPlayerToViewer(viewer, targetPlayer)
                    plugin.tabListManager.showPlayerInTab(targetPlayer, viewer)
                    plugin.logger.debug("✅ Showing unvanished ${targetPlayer.username} to ${viewer.username}")
                }
            }
        }
    }
    
    /**
     * Show player to specific viewer using enhanced entity visibility
     * CRITICAL FIX: Correct viewer/target relationship
     */
    private suspend fun showPlayerToViewer(viewer: Player, target: Player) {
        try {
            // FIXED: Add target to viewer's sight (not viewer to target's list)
            if (!viewer.viewers.contains(target)) {
                viewer.addViewer(target)
                plugin.logger.debug("✅ Showed ${target.username} to ${viewer.username}")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to show ${target.username} to ${viewer.username}", e)
        }
    }
    
    /**
     * Hide player from specific viewer using enhanced entity visibility
     * CRITICAL FIX: Correct viewer/target relationship
     */
    private suspend fun hidePlayerFromViewer(viewer: Player, target: Player) {
        try {
            // FIXED: Remove target from viewer's sight (not viewer from target's list)
            if (viewer.viewers.contains(target)) {
                viewer.removeViewer(target)
                plugin.logger.debug("❌ Hidden ${target.username} from ${viewer.username}")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to hide ${target.username} from ${viewer.username}", e)
        }
    }
    
    /**
     * Update visibility between two specific players based on vanish status
     */
    private suspend fun updateVisibilityBetweenPlayers(viewer: Player, target: Player) {
        try {
            val targetVanished = isPlayerVanished(target.uuid)
            
            if (targetVanished) {
                val canSee = canSeeVanished(viewer, target.uuid)
                if (canSee) {
                    // Ensure target is visible to viewer
                    if (!target.viewers.contains(viewer)) {
                        target.addViewer(viewer)
                        plugin.logger.debug("Added ${target.username} to ${viewer.username}'s view (staff can see vanished)")
                    }
                } else {
                    // Ensure target is hidden from viewer
                    if (target.viewers.contains(viewer)) {
                        target.removeViewer(viewer)
                        plugin.logger.debug("Removed ${target.username} from ${viewer.username}'s view (hidden)")
                    }
                }
            } else {
                // Target is not vanished - ensure visible
                if (!target.viewers.contains(viewer)) {
                    target.addViewer(viewer)
                    plugin.logger.debug("Added ${target.username} to ${viewer.username}'s view (not vanished)")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error updating visibility between ${viewer.username} and ${target.username}", e)
        }
    }
    
    /**
     * Force refresh entity visibility between two specific players
     */
    private suspend fun refreshEntityVisibility(viewer: Player, target: Player) {
        try {
            val shouldSeeTarget = if (isPlayerVanished(target.uuid)) {
                canSeeVanished(viewer, target.uuid)
            } else {
                true // Non-vanished players should always be visible
            }
            
            if (shouldSeeTarget) {
                // Ensure target is visible to viewer
                if (!target.viewers.contains(viewer)) {
                    target.addViewer(viewer)
                }
            } else {
                // Ensure target is hidden from viewer
                if (target.viewers.contains(viewer)) {
                    target.removeViewer(viewer)
                }
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error refreshing entity visibility between ${viewer.username} and ${target.username}", e)
        }
    }
    
    /**
     * Check if a player is vanished
     */
    fun isPlayerVanished(playerUuid: UUID): Boolean {
        return vanishedPlayers.containsKey(playerUuid)
    }
    
    /**
     * Get vanish data for a player
     */
    fun getVanishData(playerUuid: UUID): VanishData? {
        return vanishedPlayers[playerUuid]
    }
    
    /**
     * Check if viewer can see vanished player
     */
    suspend fun canSeeVanished(viewer: Player, vanishedPlayerUuid: UUID): Boolean {
        val vanishData = getVanishData(vanishedPlayerUuid) ?: return true
        
        try {
            // Get viewer's permissions from Radium
            val viewerData = plugin.radiumIntegration.getPlayerData(viewer.uuid).join()
            val viewerPermissions = viewerData?.permissions?.toSet() ?: emptySet()
            
            return VanishLevel.canSeeVanished(viewerPermissions, vanishData.level)
        } catch (e: Exception) {
            plugin.logger.warn("Error checking vanish visibility for ${viewer.username}", e)
            return false // Default to not showing vanished players on error
        }
    }
    
    /**
     * Get all vanished players
     */
    fun getVanishedPlayers(): Map<UUID, VanishData> {
        return vanishedPlayers.toMap()
    }
    
    /**
     * Clear all vanish data (for shutdown)
     */
    fun clear() {
        vanishedPlayers.clear()
    }
}
