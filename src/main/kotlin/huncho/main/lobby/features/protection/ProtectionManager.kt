package huncho.main.lobby.features.protection

import huncho.main.lobby.LobbyPlugin
import java.util.concurrent.ConcurrentHashMap

class ProtectionManager(private val plugin: LobbyPlugin) {
    
    private val buildModeUsers = ConcurrentHashMap<String, Boolean>()
    
    /**
     * Toggle build mode for a player
     */
    fun toggleBuildMode(uuid: String): Boolean {
        val newState = !isInBuildMode(uuid)
        setBuildMode(uuid, newState)
        return newState
    }
    
    /**
     * Set build mode for a player
     */
    fun setBuildMode(uuid: String, enabled: Boolean) {
        if (enabled) {
            buildModeUsers[uuid] = true
        } else {
            buildModeUsers.remove(uuid)
        }
    }
    
    /**
     * Check if player is in build mode
     */
    fun isInBuildMode(uuid: String): Boolean {
        return buildModeUsers.getOrDefault(uuid, false)
    }
    
    /**
     * Get all players in build mode
     */
    fun getBuildModeUsers(): Set<String> {
        return buildModeUsers.keys.toSet()
    }
    
    /**
     * Clear build mode for all players
     */
    fun clearAllBuildMode() {
        buildModeUsers.clear()
    }
    
    /**
     * Check if a protection is enabled
     */
    fun isProtectionEnabled(protection: String): Boolean {
        return plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.$protection")
    }
    
    /**
     * Check if player can bypass a protection
     */
    fun canBypass(uuid: String, protection: String): Boolean {
        val permission = when (protection) {
            "block-break", "block-place" -> "hub.command.build"
            "item-drop" -> "hub.options.bypass.drop"
            "item-pickup" -> "hub.options.bypass.pick"
            "chat" -> "hub.options.bypass.chat"
            "inventory-move" -> "hub.inv.bypass"
            "interaction" -> "hub.interact.bypass"
            else -> "hub.bypass.all"
        }
        
        return hasPermissionBlocking(uuid, permission)
    }
    
    /**
     * Apply protection settings to a player
     */
    fun applyProtections(player: net.minestom.server.entity.Player) {
        // Set food level if hunger protection is enabled
        if (isProtectionEnabled("hunger")) {
            player.food = 20
            player.foodSaturation = 20f
        }
        
        // Set health if damage protection is enabled
        if (isProtectionEnabled("damage")) {
            player.heal()
        }
    }
    
    /**
     * Get protection status for display
     */
    fun getProtectionStatus(): Map<String, Boolean> {
        return mapOf(
            "block-break" to isProtectionEnabled("block-break"),
            "block-place" to isProtectionEnabled("block-place"),
            "item-drop" to isProtectionEnabled("item-drop"),
            "item-pickup" to isProtectionEnabled("item-pickup"),
            "damage" to isProtectionEnabled("damage"),
            "hunger" to isProtectionEnabled("hunger"),
            "chat" to isProtectionEnabled("chat"),
            "portal" to isProtectionEnabled("portal"),
            "inventory-move" to isProtectionEnabled("inventory-move"),
            "interaction" to isProtectionEnabled("interaction")
        )
    }
    
    // Blocking permission check method (for backwards compatibility)
    private fun hasPermissionBlocking(uuid: String, permission: String): Boolean {
        // TODO: This should be replaced with async checks everywhere
        // For now, return a default value to allow compilation
        return false
    }
}
