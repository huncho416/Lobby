package huncho.main.lobby.features.visibility

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.MinecraftServer
import kotlinx.coroutines.runBlocking
import java.util.*
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
     * Check if target is staff using Radium API
     */
    private fun isStaff(player: Player): Boolean {
        return try {
            plugin.radiumIntegration.hasPermission(player.uuid, "radium.staff").get()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update visibility for a new player considering vanish status
     */
    fun updateVisibilityForNewPlayer(newPlayer: Player) {
        runBlocking {
            // Check if the new player is vanished
            val isVanished = plugin.radiumIntegration.isPlayerVanished(newPlayer.uuid).join()
            
            // Update visibility for all existing players
            plugin.lobbyInstance.players.forEach { existingPlayer ->
                if (existingPlayer != newPlayer) {
                    // Check if existing player can see the new player
                    val existingMode = getVisibility(existingPlayer)
                    val shouldShow = when (existingMode) {
                        VisibilityMode.ALL -> {
                            if (isVanished) {
                                // Check if existing player can see vanished players
                                plugin.radiumIntegration.canSeeVanishedPlayer(existingPlayer.uuid, newPlayer.uuid).join()
                            } else {
                                true
                            }
                        }
                        VisibilityMode.STAFF -> {
                            val newPlayerIsStaff = isStaff(newPlayer)
                            if (isVanished) {
                                newPlayerIsStaff && plugin.radiumIntegration.canSeeVanishedPlayer(existingPlayer.uuid, newPlayer.uuid).join()
                            } else {
                                newPlayerIsStaff
                            }
                        }
                        VisibilityMode.NONE -> false
                    }
                    
                    // Apply visibility using Minestom's visibility API
                    if (shouldShow) {
                        showPlayer(existingPlayer, newPlayer)
                    } else {
                        hidePlayer(existingPlayer, newPlayer)
                    }
                    
                    // Also check if new player can see existing player
                    val newPlayerMode = getVisibility(newPlayer)
                    val existingIsVanished = plugin.radiumIntegration.isPlayerVanished(existingPlayer.uuid).join()
                    val newPlayerCanSeeExisting = when (newPlayerMode) {
                        VisibilityMode.ALL -> {
                            if (existingIsVanished) {
                                plugin.radiumIntegration.canSeeVanishedPlayer(newPlayer.uuid, existingPlayer.uuid).join()
                            } else {
                                true
                            }
                        }
                        VisibilityMode.STAFF -> {
                            val existingIsStaff = isStaff(existingPlayer)
                            if (existingIsVanished) {
                                existingIsStaff && plugin.radiumIntegration.canSeeVanishedPlayer(newPlayer.uuid, existingPlayer.uuid).join()
                            } else {
                                existingIsStaff
                            }
                        }
                        VisibilityMode.NONE -> false
                    }
                    
                    if (newPlayerCanSeeExisting) {
                        showPlayer(newPlayer, existingPlayer)
                    } else {
                        hidePlayer(newPlayer, existingPlayer)
                    }
                }
            }
        }
    }
    
    /**
     * Update player visibility based on vanish status and viewer preferences
     */
    suspend fun updatePlayerVisibilityForVanish(player: Player) {
        try {
            // Check if the player is vanished
            val isVanished = plugin.radiumIntegration.isPlayerVanished(player.uuid).join()
            
            // Update visibility for all other players
            plugin.lobbyInstance.players.forEach { viewer ->
                if (viewer != player) {
                    val viewerMode = getVisibility(viewer)
                    val shouldShow = when (viewerMode) {
                        VisibilityMode.ALL -> {
                            if (isVanished) {
                                // Check if viewer can see vanished players
                                plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, player.uuid).join()
                            } else {
                                true
                            }
                        }
                        VisibilityMode.STAFF -> {
                            val playerIsStaff = isStaff(player)
                            if (isVanished) {
                                playerIsStaff && plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, player.uuid).join()
                            } else {
                                playerIsStaff
                            }
                        }
                        VisibilityMode.NONE -> false
                    }
                    
                    // Apply visibility using Minestom's API
                    if (shouldShow) {
                        showPlayer(viewer, player)
                    } else {
                        hidePlayer(viewer, player)
                    }
                }
            }
            
            plugin.logger.debug("Updated visibility for ${if (isVanished) "vanished" else "visible"} player ${player.username}")
        } catch (e: Exception) {
            plugin.logger.error("Error updating vanish visibility for ${player.username}", e)
        }
    }
    
    /**
     * Update visibility for all players when a player's vanish status changes
     */
    suspend fun handleVanishStatusChange(playerUuid: UUID, isVanished: Boolean) {
        try {
            val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
            if (player != null) {
                updatePlayerVisibilityForVanish(player)
                plugin.logger.info("Handled vanish status change for ${player.username}: ${if (isVanished) "vanished" else "visible"}")
            }
        } catch (e: Exception) {
            plugin.logger.error("Error handling vanish status change for $playerUuid", e)
        }
    }

    /**
     * Check if viewer can see target
     */
    private fun canSeePlayer(viewer: Player, target: Player): Boolean {
        // Check if the target player is in the viewer's viewable entities
        return viewer.viewers.contains(target)
    }
    
    /**
     * Show target player to viewer
     */
    private fun showPlayer(viewer: Player, target: Player) {
        // Add the target player to viewer's visible entities
        target.addViewer(viewer)
    }
    
    /**
     * Hide target player from viewer
     */
    private fun hidePlayer(viewer: Player, target: Player) {
        // Remove the target player from viewer's visible entities
        target.removeViewer(viewer)
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
