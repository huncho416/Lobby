package huncho.main.lobby.features.visibility

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

enum class VisibilityMode {
    ALL,     // Show all players
    STAFF,   // Show only staff
    NONE     // Hide all players
}

class VisibilityManager(private val plugin: LobbyPlugin) {
    
    private val playerVisibility = ConcurrentHashMap<String, VisibilityMode>()
    
    /**
     * Set visibility mode for a player
     */
    fun setVisibility(player: Player, mode: VisibilityMode) {
        val uuid = player.uuid.toString()
        playerVisibility[uuid] = mode
        
        // Save to database
        runBlocking {
            updatePlayerSetting(player, "visibility", mode.name.lowercase())
        }
        
        // Apply visibility changes
        updatePlayerVisibility(player)
        
        // Send message
        val message = when (mode) {
            VisibilityMode.ALL -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-all")
            VisibilityMode.STAFF -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-staff")
            VisibilityMode.NONE -> plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.visibility-none")
        }
        MessageUtils.sendMessage(player, message)
    }
    
    /**
     * Toggle visibility mode for a player
     */
    fun toggleVisibility(player: Player) {
        val currentMode = getVisibility(player)
        val newMode = when (currentMode) {
            VisibilityMode.ALL -> VisibilityMode.STAFF
            VisibilityMode.STAFF -> VisibilityMode.NONE
            VisibilityMode.NONE -> VisibilityMode.ALL
        }
        setVisibility(player, newMode)
    }
    
    /**
     * Get visibility mode for a player
     */
    fun getVisibility(player: Player): VisibilityMode {
        val uuid = player.uuid.toString()
        return playerVisibility.getOrDefault(uuid, VisibilityMode.ALL)
    }
    
    /**
     * Load visibility setting from database
     */
    suspend fun loadVisibility(player: Player) {
        val uuid = player.uuid.toString()
        val setting = getPlayerSetting(player, "visibility")
        val mode = when (setting.lowercase()) {
            "staff" -> VisibilityMode.STAFF
            "none" -> VisibilityMode.NONE
            else -> VisibilityMode.ALL
        }
        playerVisibility[uuid] = mode
        updatePlayerVisibility(player)
    }
    
    /**
     * Update visibility for a specific player
     */
    private fun updatePlayerVisibility(viewer: Player) {
        val mode = getVisibility(viewer)
        
        plugin.lobbyInstance.players.forEach { target ->
            if (target == viewer) return@forEach
            
            val shouldShow = when (mode) {
                VisibilityMode.ALL -> true
                VisibilityMode.STAFF -> isStaff(target)
                VisibilityMode.NONE -> false
            }
            
            if (shouldShow && !canSeePlayer(viewer, target)) {
                showPlayer(viewer, target)
            } else if (!shouldShow && canSeePlayer(viewer, target)) {
                hidePlayer(viewer, target)
            }
        }
    }
    
    /**
     * Update visibility for all players when a new player joins
     */
    fun updateVisibilityForNewPlayer(newPlayer: Player) {
        plugin.lobbyInstance.players.forEach { viewer ->
            if (viewer == newPlayer) return@forEach
            
            val mode = getVisibility(viewer)
            val shouldShow = when (mode) {
                VisibilityMode.ALL -> true
                VisibilityMode.STAFF -> isStaff(newPlayer)
                VisibilityMode.NONE -> false
            }
            
            if (shouldShow) {
                showPlayer(viewer, newPlayer)
            } else {
                hidePlayer(viewer, newPlayer)
            }
        }
        
        // Update visibility for the new player
        runBlocking { loadVisibility(newPlayer) }
    }
    
    /**
     * Check if target is staff
     */
    private fun isStaff(player: Player): Boolean {
        // TODO: Implement async permission checking
        // For now, return false to allow compilation
        return false
    }
    
    /**
     * Check if viewer can see target
     */
    private fun canSeePlayer(viewer: Player, target: Player): Boolean {
        // This is a simple implementation - in a real scenario you'd track visibility state
        return true // Minestom handles this internally
    }
    
    /**
     * Show target player to viewer
     */
    private fun showPlayer(viewer: Player, target: Player) {
        // In Minestom, players are visible by default
        // This method would be used if you're tracking hidden players
    }
    
    /**
     * Hide target player from viewer
     */
    private fun hidePlayer(viewer: Player, target: Player) {
        // In Minestom, you would remove the player from the viewer's sight
        // This is a simplified implementation
    }
    
    /**
     * Remove player from tracking
     */
    fun removePlayer(uuid: String) {
        playerVisibility.remove(uuid)
    }
    
    /**
     * Get visibility stats
     */
    fun getVisibilityStats(): Map<VisibilityMode, Int> {
        val stats = mutableMapOf<VisibilityMode, Int>()
        VisibilityMode.values().forEach { mode ->
            stats[mode] = playerVisibility.values.count { it == mode }
        }
        return stats
    }
    
    /**
     * Reset all players to default visibility
     */
    fun resetAllVisibility() {
        playerVisibility.clear()
        plugin.lobbyInstance.players.forEach { player ->
            updatePlayerVisibility(player)
        }
    }
    
    // Stub methods for missing RadiumIntegration calls
    private fun updatePlayerSetting(player: Player, setting: String, value: String) {
        // TODO: Implement with RadiumIntegration HTTP API
    }
    
    private fun getPlayerSetting(player: Player, setting: String): String {
        // TODO: Implement with RadiumIntegration HTTP API
        return "ALL"
    }
}
