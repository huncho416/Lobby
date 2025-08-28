package huncho.main.lobby.features.visibility

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.MinecraftServer
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerSpawnEvent
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

enum class VisibilityMode {
    ALL,     // Show all players
    STAFF,   // Show only staff
    NONE     // Hide all players
}

class VisibilityManager(private val plugin: LobbyPlugin) {
    
    private val playerVisibility = ConcurrentHashMap<String, VisibilityMode>()
    
    /**
     * Register event handlers for vanish visibility
     */
    fun registerEvents(eventHandler: GlobalEventHandler) {
        eventHandler.addListener(PlayerSpawnEvent::class.java) { event ->
            // Update visibility for the spawning player
            CompletableFuture.runAsync {
                runBlocking {
                    updatePlayerVisibilityForVanish(event.player)
                    // Also update other players' visibility to this player
                    updateVisibilityForAll()
                }
            }
        }
    }
    
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
     * Update player visibility based on vanish status - CRITICAL for in-game vanish
     * ENHANCED: Now ensures proper entity hiding/showing for all viewers
     */
    suspend fun updatePlayerVisibilityForVanish(player: Player) {
        try {
            val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(player.uuid)
            
            // Get all players in the same instance
            val allPlayers = player.instance?.players ?: emptySet()
            
            plugin.logger.debug("Updating vanish visibility for ${player.username} (vanished: $isVanished) to ${allPlayers.size} viewers")
            
            for (otherPlayer in allPlayers) {
                if (otherPlayer.uuid != player.uuid) {
                    if (isVanished) {
                        val canSee = plugin.vanishPluginMessageListener.canSeeVanished(otherPlayer, player.uuid)
                        if (!canSee) {
                            // CRITICAL: Hide vanished player from viewers who can't see them (defaults)
                            hidePlayerFromViewer(otherPlayer, player)
                        } else {
                            // Show vanished player to viewers who can see them (staff)
                            showPlayerToViewer(otherPlayer, player)
                        }
                    } else {
                        // CRITICAL: Player is not vanished - ensure they're visible to everyone
                        showPlayerToViewer(otherPlayer, player)
                    }
                    
                    // Also check visibility in the reverse direction
                    updatePlayerVisibilityBidirectional(player, otherPlayer)
                }
            }
            
            // Force tab list refresh for this player
            plugin.tabListManager.updatePlayerTabList(player)
            
            plugin.logger.debug("Completed vanish visibility update for ${player.username}")
        } catch (e: Exception) {
            plugin.logger.error("Error updating vanish visibility for ${player.username}", e)
        }
    }
    
    /**
     * Update visibility bidirectionally between two players
     */
    private suspend fun updatePlayerVisibilityBidirectional(player1: Player, player2: Player) {
        try {
            // Check if player1 can see player2
            val player2Vanished = plugin.vanishPluginMessageListener.isPlayerVanished(player2.uuid)
            if (player2Vanished) {
                val canSeePlayer2 = plugin.vanishPluginMessageListener.canSeeVanished(player1, player2.uuid)
                if (canSeePlayer2) {
                    showPlayerToViewer(player1, player2)
                } else {
                    hidePlayerFromViewer(player1, player2)
                }
            } else {
                showPlayerToViewer(player1, player2)
            }
            
            // Check if player2 can see player1  
            val player1Vanished = plugin.vanishPluginMessageListener.isPlayerVanished(player1.uuid)
            if (player1Vanished) {
                val canSeePlayer1 = plugin.vanishPluginMessageListener.canSeeVanished(player2, player1.uuid)
                if (canSeePlayer1) {
                    showPlayerToViewer(player2, player1)
                } else {
                    hidePlayerFromViewer(player2, player1)
                }
            } else {
                showPlayerToViewer(player2, player1)
            }
        } catch (e: Exception) {
            plugin.logger.warn("Error updating bidirectional visibility between ${player1.username} and ${player2.username}", e)
        }
    }
    
    /**
     * Update visibility for all online players - called when vanish status changes
     */
    private suspend fun updateVisibilityForAll() {
        try {
            val allPlayers = MinecraftServer.getConnectionManager().onlinePlayers
            
            for (player in allPlayers) {
                updatePlayerVisibilityForVanish(player)
            }
            
            plugin.logger.debug("Updated visibility for all ${allPlayers.size} online players")
        } catch (e: Exception) {
            plugin.logger.error("Error updating visibility for all players", e)
        }
    }
    
    /**
     * Hide a player from a specific viewer using Minestom's entity visibility
     * ENHANCED: Ensures proper entity hiding for vanished players
     */
    private fun hidePlayerFromViewer(viewer: Player, target: Player) {
        try {
            // Use Minestom's entity visibility system - remove viewer from target's viewers
            if (target.viewers.contains(viewer)) {
                target.removeViewer(viewer)
                plugin.logger.debug("🚫 Hidden ${target.username} from ${viewer.username} (vanish)")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to hide ${target.username} from ${viewer.username}", e)
        }
    }
    
    /**
     * Show a player to a specific viewer using Minestom's entity visibility
     * ENHANCED: Ensures proper entity showing for unvanished players
     */
    private fun showPlayerToViewer(viewer: Player, target: Player) {
        try {
            // Use Minestom's entity visibility system - add viewer to target's viewers
            if (!target.viewers.contains(viewer)) {
                target.addViewer(viewer)
                plugin.logger.debug("✅ Shown ${target.username} to ${viewer.username} (visible)")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to show ${target.username} to ${viewer.username}", e)
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
