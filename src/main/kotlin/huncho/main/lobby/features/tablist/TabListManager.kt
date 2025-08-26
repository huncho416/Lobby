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
                // Check if target player is vanished
                val isVanished = if (respectVanish) {
                    plugin.radiumIntegration.isPlayerVanished(targetPlayer.uuid).join()
                } else {
                    false
                }
                
                if (isVanished && respectVanish) {
                    // Check if viewer can see vanished player
                    val canSee = plugin.radiumIntegration.canSeeVanishedPlayer(viewer.uuid, targetPlayer.uuid).join()
                    if (!canSee) {
                        // Hide this player from viewer's tab list
                        // Note: In Minestom, we might need to use different methods to hide players
                        // For now, we'll skip updating their display name
                        return@forEach
                    }
                }
                
                var shouldUseRadium = false
                var radiumDisplayName = ""
                
                if (respectRadium) {
                    // Check if Radium provides formatting for this player
                    val radiumData = plugin.radiumIntegration.getPlayerData(targetPlayer.uuid).join()
                    
                    if (radiumData != null && radiumData.rank != null) {
                        // Radium provides formatting, construct the display name
                        val prefix = radiumData.rank.prefix.ifEmpty { "" }
                        val color = radiumData.rank.color.ifEmpty { "&7" }
                        
                        // Add vanish indicator if viewer can see vanished players
                        val vanishIndicator = if (isVanished && showVanishIndicator) " &8(Vanished)" else ""
                        radiumDisplayName = "$prefix$color${targetPlayer.username}$vanishIndicator"
                        shouldUseRadium = true
                    }
                }

                if (shouldUseRadium) {
                    // Use Radium formatting - in Minestom, we set the player's display name
                    // Note: This may not work exactly like Bukkit's sendPlayerListName
                    // We'll need to find the correct way to update tab list names in Minestom
                } else {
                    // Use fallback formatting only if Radium doesn't provide it
                    val fallbackFormat = plugin.configManager.getString(plugin.configManager.mainConfig, "tablist.fallback_format", "&7%player_name%")
                    val vanishIndicator = if (isVanished && showVanishIndicator) " &8(Vanished)" else ""
                    val displayName = fallbackFormat.replace("%player_name%", targetPlayer.username) + vanishIndicator
                }
            } catch (e: Exception) {
                plugin.logger.warn("Error updating tab list name for ${targetPlayer.username}", e)
                // On error, don't change the display name to avoid overriding Radium
            }
        }
    }
    
    /**
     * Conservative method to update player display names - only when absolutely sure Radium isn't handling it
     */
    private suspend fun updatePlayerDisplayNamesConservatively(viewer: Player) {
        // We'll only apply formatting to players who definitely don't have Radium data
        // This prevents any conflicts with Radium's formatting
        
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { targetPlayer ->
            try {
                // Check if Radium has data for this player
                val radiumData = plugin.radiumIntegration.getPlayerData(targetPlayer.uuid).join()
                
                if (radiumData == null || radiumData.rank == null) {
                    // Only apply our formatting if Radium has no data
                    val fallbackFormat = plugin.configManager.getString(plugin.configManager.mainConfig, "tablist.fallback_format", "&7%player_name%")
                    val displayName = fallbackFormat.replace("%player_name%", targetPlayer.username)
                    
                    // Apply fallback formatting (commented out for now - needs proper Minestom implementation)
                    // targetPlayer.setDisplayName(MessageUtils.colorize(displayName))
                } else {
                    // Radium has data, don't touch the display name at all to respect Radium formatting
                }
            } catch (e: Exception) {
                // If we can't check Radium data, don't modify the name to be safe
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
