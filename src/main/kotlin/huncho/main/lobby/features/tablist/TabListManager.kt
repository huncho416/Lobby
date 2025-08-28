package huncho.main.lobby.features.tablist

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.MinecraftServer
import net.kyori.adventure.text.Component
import kotlinx.coroutines.runBlocking

/**
 * Manages tab list formatting with MythicHub style while respecting Radium prefixes
 */
class TabListManager(private val plugin: LobbyPlugin) {
    
    fun initialize() {
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.enabled", true)) {
            return
        }
        
        startTabListUpdateTask()
        plugin.logger.info("Tab list manager initialized - MythicHub style with Radium integration")
    }
    
    /**
     * Start the recurring task to update tab lists
     */
    private fun startTabListUpdateTask() {
        val scheduler = MinecraftServer.getSchedulerManager()
        
        scheduler.submitTask {
            updateAllTabLists()
            TaskSchedule.tick(100) // Update every 5 seconds
        }
    }
    
    /**
     * Update tab list for a specific player
     */
    fun updatePlayerTabList(player: Player) {
        runBlocking {
            try {
                // Set header and footer - this is safe and won't conflict with Radium
                val header = getTabListHeader()
                val footer = getTabListFooter(player)
                
                player.sendPlayerListHeaderAndFooter(
                    MessageUtils.colorize(header.joinToString("\n")),
                    MessageUtils.colorize(footer.joinToString("\n"))
                )
                
                // Only update player display names if Radium respect is disabled
                // or if we can't get Radium data for players
                val respectRadium = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_radium_formatting", true)
                if (!respectRadium) {
                    updatePlayerDisplayNames(player)
                } else {
                    // Try to update, but be very conservative
                    updatePlayerDisplayNamesConservatively(player)
                }
                
            } catch (e: Exception) {
                plugin.logger.error("Error updating tab list for ${player.username}", e)
            }
        }
    }
    
    /**
     * Update tab lists for all online players
     */
    private fun updateAllTabLists() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            updatePlayerTabList(player)
        }
    }
    
    /**
     * Get the tab list header with placeholders replaced
     */
    private fun getTabListHeader(): List<String> {
        val headerLines = plugin.configManager.getList(plugin.configManager.mainConfig, "tablist.header")
        return headerLines.filterIsInstance<String>().map { line ->
            replacePlaceholders(line, null)
        }
    }
    
    /**
     * Get the tab list footer with placeholders replaced
     */
    private fun getTabListFooter(player: Player? = null): List<String> {
        val footerLines = plugin.configManager.getList(plugin.configManager.mainConfig, "tablist.footer")
        return footerLines.filterIsInstance<String>().map { line ->
            replacePlaceholders(line, player)
        }
    }
    
    /**
     * Update player display names in tab list - respecting vanish status
     */
    private suspend fun updatePlayerDisplayNames(viewer: Player) {
        val respectRadium = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_radium_formatting", true)
        val respectVanish = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_vanish_status", true)
        val showVanishIndicator = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.show_vanish_indicator", true)
        
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { targetPlayer ->
            try {
                // Check if target player is vanished using hybrid system
                val isVanished = if (respectVanish) {
                    plugin.vanishPluginMessageListener.isPlayerVanished(targetPlayer.uuid)
                } else {
                    false
                }
                
                if (isVanished && respectVanish) {
                    // Check if viewer can see vanished player using hybrid system
                    val canSee = plugin.vanishPluginMessageListener.canSeeVanished(viewer, targetPlayer.uuid)
                    if (!canSee) {
                        // Hide this player from viewer's tab list by removing them
                        targetPlayer.removeViewer(viewer)
                        return@forEach
                    }
                }
                
                var shouldUseRadium = false
                var radiumDisplayName = ""
                
                if (respectRadium) {
                    // Check if Radium provides formatting for this player
                    val radiumData = plugin.radiumIntegration.getPlayerData(targetPlayer.uuid).join()
                    
                    if (radiumData != null && radiumData.rank != null) {
                        // Radium provides formatting, construct the display name with proper color parsing
                        val prefix = parseColorCodes(radiumData.rank.prefix.ifEmpty { "" })
                        val color = parseColorCodes(radiumData.rank.color.ifEmpty { "&7" })
                        
                        // FIXED: Add [V] vanish indicator with proper formatting
                        val vanishIndicator = if (isVanished && showVanishIndicator) "&8[V] &r" else ""
                        radiumDisplayName = "$vanishIndicator$prefix$color${targetPlayer.username}"
                        shouldUseRadium = true
                    }
                }

                // Set the display name on the player with enhanced error handling
                if (shouldUseRadium) {
                    // Use Radium formatting with proper color codes
                    try {
                        targetPlayer.displayName = MessageUtils.colorize(radiumDisplayName)
                        plugin.logger.debug("Updated tab display for ${targetPlayer.username}: $radiumDisplayName")
                    } catch (e: Exception) {
                        plugin.logger.warn("Failed to set Radium display name for ${targetPlayer.username}", e)
                    }
                } else {
                    // Use fallback formatting only if Radium doesn't provide it
                    val fallbackFormat = plugin.configManager.getString(plugin.configManager.mainConfig, "tablist.fallback_format", "&7%player_name%")
                    val vanishIndicator = if (isVanished && showVanishIndicator) "&8[V] &r" else ""
                    val displayName = "$vanishIndicator${fallbackFormat.replace("%player_name%", targetPlayer.username)}"
                    try {
                        targetPlayer.displayName = MessageUtils.colorize(displayName)
                        plugin.logger.debug("Updated fallback tab display for ${targetPlayer.username}: $displayName")
                    } catch (e: Exception) {
                        plugin.logger.warn("Failed to set fallback display name for ${targetPlayer.username}", e)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warn("Error updating tab list name for ${targetPlayer.username}", e)
                // On error, don't change the display name to avoid overriding Radium
            }
        }
    }
    
    /**
     * Parse color codes properly to handle both & and § symbols
     */
    private fun parseColorCodes(input: String): String {
        return input
            .replace("&", "§")
            .replace("§l", "§l") // Bold
            .replace("§o", "§o") // Italic
            .replace("§n", "§n") // Underline
            .replace("§m", "§m") // Strikethrough
            .replace("§k", "§k") // Obfuscated
            .replace("§r", "§r") // Reset
    }
    
    /**
     * Conservative method to update player display names - only when absolutely sure Radium isn't handling it
     */
    private suspend fun updatePlayerDisplayNamesConservatively(viewer: Player) {
        // We'll only apply formatting to players who definitely don't have Radium data
        // This prevents any conflicts with Radium's formatting
        val showVanishIndicator = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.show_vanish_indicator", true)
        
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { targetPlayer ->
            try {
                // Check if target player is vanished
                val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(targetPlayer.uuid)
                
                // Check if Radium has data for this player
                val radiumData = plugin.radiumIntegration.getPlayerData(targetPlayer.uuid).join()
                
                if (radiumData == null || radiumData.rank == null) {
                    // Only apply our formatting if Radium has no data
                    val fallbackFormat = plugin.configManager.getString(plugin.configManager.mainConfig, "tablist.fallback_format", "&7%player_name%")
                    val vanishIndicator = if (isVanished && showVanishIndicator) "&8[V]&r " else ""
                    val displayName = "$vanishIndicator${fallbackFormat.replace("%player_name%", targetPlayer.username)}"
                    
                    targetPlayer.displayName = MessageUtils.colorize(displayName)
                } else {
                    // Radium has data, but we can still add vanish indicator if allowed
                    if (isVanished && showVanishIndicator) {
                        val currentName = targetPlayer.displayName?.toString() ?: targetPlayer.username
                        if (!currentName.contains("[V]")) {
                            val vanishIndicator = "&8[V]&r "
                            targetPlayer.displayName = MessageUtils.colorize("$vanishIndicator$currentName")
                        }
                    }
                }
            } catch (e: Exception) {
                // If we can't check Radium data, don't modify the name to be safe
                plugin.logger.debug("Could not check Radium data for ${targetPlayer.username}, skipping tab list update")
            }
        }
    }
    
    /**
     * Replace placeholders in tab list text
     */
    private fun replacePlaceholders(text: String, player: Player? = null): String {
        var result = text
        
        // Replace basic placeholders
        result = result.replace("%online_players%", MinecraftServer.getConnectionManager().onlinePlayerCount.toString())
        result = result.replace("%max_players%", "100") // Or get from config
        
        // Player-specific placeholders
        if (player != null) {
            result = result.replace("%ping%", player.latency.toString())
            result = result.replace("%player_name%", player.username)
        } else {
            // Fallback for non-player specific placeholders
            result = result.replace("%ping%", "0")
            result = result.replace("%player_name%", "Unknown")
        }
        
        // Server-specific placeholders
        result = result.replace("%server_name%", "Lobby")
        
        // Global player count - try to get from Radium, fallback to local count
        try {
            // Attempt to get global player count from Radium
            val servers = plugin.radiumIntegration.getServerList().join()
            val globalCount = servers.sumOf { it.playerCount }
            result = result.replace("%global_players%", globalCount.toString())
        } catch (e: Exception) {
            // Fallback to local count if Radium is unavailable
            val localCount = MinecraftServer.getConnectionManager().onlinePlayerCount
            result = result.replace("%global_players%", localCount.toString())
        }
        
        return result
    }
    
    /**
     * Handle player join for tab list
     */
    fun onPlayerJoin(player: Player) {
        // Update the new player's tab list
        updatePlayerTabList(player)
        
        // Update tab lists for all other players to show the new player
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { otherPlayer ->
            if (otherPlayer != player) {
                updatePlayerTabList(otherPlayer)
            }
        }
    }
    
    /**
     * Handle player quit for tab list
     */
    fun onPlayerQuit(player: Player) {
        // Update tab lists for all remaining players
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { otherPlayer ->
            if (otherPlayer != player) {
                updatePlayerTabList(otherPlayer)
            }
        }
    }
    
    /**
     * ENHANCED: Comprehensive tab list refresh for all players
     * Fixes the unvanish visibility issue by properly updating all tab entries
     */
    fun refreshAllTabLists() {
        try {
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            plugin.logger.debug("Starting comprehensive tab list refresh for ${onlinePlayers.size} players")
            
            // First pass: Update all player entries
            onlinePlayers.forEach { player ->
                updatePlayerTabEntry(player)
            }
            
            // Second pass: Update headers and footers for all players
            onlinePlayers.forEach { player ->
                updatePlayerTabList(player)
            }
            
            plugin.logger.debug("Completed comprehensive tab list refresh for all players")
        } catch (e: Exception) {
            plugin.logger.error("Error during comprehensive tab list refresh", e)
        }
    }
    
    /**
     * ENHANCED: Update individual player tab entry with comprehensive vanish checking
     * Ensures proper visibility and formatting for each player
     */
    private fun updatePlayerTabEntry(player: Player) {
        runBlocking {
            try {
                // Check vanish status first
                val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(player.uuid)
                
                if (isVanished) {
                    // Player is vanished - check who can see them
                    hideOrShowVanishedPlayer(player)
                } else {
                    // Player is not vanished - show to everyone with proper formatting
                    showPlayerInTabForAll(player)
                }
                
                // Update the player's own tab list
                updatePlayerTabList(player)
                
            } catch (e: Exception) {
                plugin.logger.warn("Error updating tab entry for ${player.username}", e)
            }
        }
    }
    
    /**
     * ENHANCED: Handle vanished player visibility in tab list
     * Checks each viewer's permission to see the vanished player
     */
    private suspend fun hideOrShowVanishedPlayer(vanishedPlayer: Player) {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
            if (viewer.uuid != vanishedPlayer.uuid) {
                val canSee = plugin.vanishPluginMessageListener.canSeeVanished(viewer, vanishedPlayer.uuid)
                if (canSee) {
                    // Show with vanish indicator
                    showPlayerWithVanishIndicator(vanishedPlayer, viewer)
                } else {
                    // Hide from tab completely
                    hidePlayerFromTab(vanishedPlayer, viewer)
                }
            }
        }
    }
    
    /**
     * ENHANCED: Show player in tab list normally (for unvanished players) 
     */
    suspend fun showPlayerInTab(targetPlayer: Player, viewer: Player) {
        try {
            // Ensure player is visible to this viewer
            if (!targetPlayer.viewers.contains(viewer)) {
                targetPlayer.addViewer(viewer)
            }
            
            // Update display name without vanish indicator
            updatePlayerDisplayForViewer(targetPlayer, viewer, false)
            plugin.logger.debug("✅ Showing unvanished ${targetPlayer.username} normally to ${viewer.username}")
        } catch (e: Exception) {
            plugin.logger.warn("Failed to show ${targetPlayer.username} to ${viewer.username}", e)
        }
    }
    
    /**
     * Show player in tab for all viewers (helper method)
     */
    private suspend fun showPlayerInTabForAll(player: Player) {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
            if (viewer.uuid != player.uuid) {
                showPlayerInTab(player, viewer)
            }
        }
    }
    
    /**
     * ENHANCED: Show vanished player with [V] indicator to authorized viewers
     */
    fun showPlayerWithVanishIndicator(vanishedPlayer: Player, viewer: Player) {
        runBlocking {
            try {
                // Ensure player is visible to this viewer
                if (!vanishedPlayer.viewers.contains(viewer)) {
                    vanishedPlayer.addViewer(viewer)
                }
                
                // Update display name with vanish indicator
                updatePlayerDisplayForViewer(vanishedPlayer, viewer, true)
                plugin.logger.debug("✅ Showing vanished ${vanishedPlayer.username} with [V] indicator to ${viewer.username}")
            } catch (e: Exception) {
                plugin.logger.warn("Failed to show vanished player ${vanishedPlayer.username} to ${viewer.username}", e)
            }
        }
    }
    
    /**
     * ENHANCED: Hide player from specific viewer's tab list
     */
    fun hidePlayerFromTab(player: Player, viewer: Player) {
        try {
            if (player.viewers.contains(viewer)) {
                player.removeViewer(viewer)
                plugin.logger.debug("❌ Hidden ${player.username} from ${viewer.username}'s tab")
            }
        } catch (e: Exception) {
            plugin.logger.warn("Failed to hide ${player.username} from ${viewer.username}'s tab", e)
        }
    }
    
    /**
     * ENHANCED: Update player display name for specific viewer with vanish indicator option
     */
    private suspend fun updatePlayerDisplayForViewer(player: Player, viewer: Player, showVanishIndicator: Boolean) {
        try {
            val respectRadium = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_radium_formatting", true)
            val showIndicator = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.show_vanish_indicator", true)
            
            if (respectRadium) {
                // Get Radium formatting for this player
                val radiumData = plugin.radiumIntegration.getPlayerData(player.uuid).join()
                
                if (radiumData != null && radiumData.rank != null) {
                    val prefix = parseColorCodes(radiumData.rank.prefix.ifEmpty { "" })
                    val color = parseColorCodes(radiumData.rank.color.ifEmpty { "&7" })
                    
                    val vanishIndicator = if (showVanishIndicator && showIndicator) "&8[V] &r" else ""
                    val displayName = "$vanishIndicator$prefix$color${player.username}"
                    
                    player.displayName = MessageUtils.colorize(displayName)
                    plugin.logger.debug("Updated Radium display for ${player.username} (vanish: $showVanishIndicator): $displayName")
                    return
                }
            }
            
            // Fallback formatting
            val fallbackFormat = plugin.configManager.getString(plugin.configManager.mainConfig, "tablist.fallback_format", "&7%player_name%")
            val vanishIndicator = if (showVanishIndicator && showIndicator) "&8[V] &r" else ""
            val displayName = "$vanishIndicator${fallbackFormat.replace("%player_name%", player.username)}"
            
            player.displayName = MessageUtils.colorize(displayName)
            plugin.logger.debug("Updated fallback display for ${player.username} (vanish: $showVanishIndicator): $displayName")
            
        } catch (e: Exception) {
            plugin.logger.warn("Failed to update display name for ${player.username} for viewer ${viewer.username}", e)
        }
    }
    
    /**
     * Get tab list status information
     */
    fun getTabListStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.enabled", true),
            "respect_radium" to plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_radium_formatting", true),
            "online_players" to MinecraftServer.getConnectionManager().onlinePlayerCount
        )
    }
}
