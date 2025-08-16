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
                val footer = getTabListFooter()
                
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
            replacePlaceholders(line)
        }
    }
    
    /**
     * Get the tab list footer with placeholders replaced
     */
    private fun getTabListFooter(): List<String> {
        val footerLines = plugin.configManager.getList(plugin.configManager.mainConfig, "tablist.footer")
        return footerLines.filterIsInstance<String>().map { line ->
            replacePlaceholders(line)
        }
    }
    
    /**
     * Update player display names in tab list - only if Radium doesn't provide formatting
     */
    private suspend fun updatePlayerDisplayNames(viewer: Player) {
        val respectRadium = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "tablist.respect_radium_formatting", true)
        
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { targetPlayer ->
            try {
                var shouldUseRadium = false
                var radiumDisplayName = ""
                
                if (respectRadium) {
                    // Check if Radium provides formatting for this player
                    val radiumData = plugin.radiumIntegration.getPlayerData(targetPlayer.uuid).join()
                    
                    if (radiumData != null && radiumData.rank != null) {
                        // Radium provides formatting, construct the display name
                        val prefix = radiumData.rank.prefix.ifEmpty { "" }
                        val color = radiumData.rank.color.ifEmpty { "&7" }
                        
                        // Build display name: prefix + color + name (no suffix in Radium data model)
                        radiumDisplayName = "$prefix$color${targetPlayer.username}"
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
                    val displayName = fallbackFormat.replace("%player_name%", targetPlayer.username)
                }
            } catch (e: Exception) {
                plugin.logger.warn("Error setting tab list name for ${targetPlayer.username} - keeping default", e)
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
    private fun replacePlaceholders(text: String): String {
        var result = text
        
        // Replace basic placeholders
        result = result.replace("%online_players%", MinecraftServer.getConnectionManager().onlinePlayerCount.toString())
        result = result.replace("%max_players%", "100") // Or get from config
        
        // Add more placeholders as needed
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
