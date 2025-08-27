package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.VanishLevel
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Administrative command for testing the hybrid vanish system
 * Provides comprehensive debugging and monitoring capabilities
 */
class VanishTestCommand(private val plugin: LobbyPlugin) : Command("vanishtest") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@setDefaultExecutor
            }
            
            runBlocking {
                showVanishStatus(sender, sender)
            }
        }
        
        // /vanishtest status [player]
        val statusArg = ArgumentType.Word("status")
        val playerArg = ArgumentType.Word("player").setDefaultValue("")
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@addSyntax
            }
            
            if (!plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()) {
                sender.sendMessage(Component.text("You don't have permission to use this command", NamedTextColor.RED))
                return@addSyntax
            }
            
            val playerName = context.get(playerArg)
            val targetPlayer = if (playerName.isEmpty()) {
                sender
            } else {
                MinecraftServer.getConnectionManager().onlinePlayers.find { 
                    it.username.equals(playerName, ignoreCase = true) 
                } ?: run {
                    sender.sendMessage(Component.text("Player '$playerName' not found online", NamedTextColor.RED))
                    return@addSyntax
                }
            }
            
            runBlocking {
                showVanishStatus(sender, targetPlayer)
            }
        }, statusArg, playerArg)
        
        // /vanishtest visibility [player]
        val visibilityArg = ArgumentType.Word("visibility")
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@addSyntax
            }
            
            if (!plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()) {
                sender.sendMessage(Component.text("You don't have permission to use this command", NamedTextColor.RED))
                return@addSyntax
            }
            
            val playerName = context.get(playerArg)
            if (playerName.isEmpty()) {
                sender.sendMessage(Component.text("Please specify a player to check visibility for", NamedTextColor.RED))
                return@addSyntax
            }
            
            val targetPlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { 
                it.username.equals(playerName, ignoreCase = true) 
            } ?: run {
                sender.sendMessage(Component.text("Player '$playerName' not found online", NamedTextColor.RED))
                return@addSyntax
            }
            
            runBlocking {
                checkVisibility(sender, targetPlayer)
            }
        }, visibilityArg, playerArg)
        
        // /vanishtest refresh
        val refreshArg = ArgumentType.Word("refresh")
        
        addSyntax({ sender, _ ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@addSyntax
            }
            
            if (!plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()) {
                sender.sendMessage(Component.text("You don't have permission to use this command", NamedTextColor.RED))
                return@addSyntax
            }
            
            runBlocking {
                refreshVanishStatuses(sender)
            }
        }, refreshArg)
        
        // /vanishtest monitor
        val monitorArg = ArgumentType.Word("monitor")
        
        addSyntax({ sender, _ ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@addSyntax
            }
            
            if (!plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()) {
                sender.sendMessage(Component.text("You don't have permission to use this command", NamedTextColor.RED))
                return@addSyntax
            }
            
            showMonitorStats(sender)
        }, monitorArg)
        
        // /vanishtest list
        val listArg = ArgumentType.Word("list")
        
        addSyntax({ sender, _ ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be executed by players", NamedTextColor.RED))
                return@addSyntax
            }
            
            if (!plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()) {
                sender.sendMessage(Component.text("You don't have permission to use this command", NamedTextColor.RED))
                return@addSyntax
            }
            
            runBlocking {
                listVanishedPlayers(sender)
            }
        }, listArg)
    }
    
    /**
     * Show vanish status for a player using hybrid system
     */
    private suspend fun showVanishStatus(viewer: Player, target: Player) {
        try {
            val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(target.uuid)
            val vanishData = plugin.vanishPluginMessageListener.getVanishData(target.uuid)
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            viewer.sendMessage(Component.text("Vanish Status for ${target.username}", NamedTextColor.YELLOW))
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
            if (isVanished && vanishData != null) {
                viewer.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("VANISHED", NamedTextColor.RED)))
                viewer.sendMessage(Component.text("Level: ", NamedTextColor.GRAY)
                    .append(Component.text(vanishData.level.displayName, NamedTextColor.YELLOW)))
                viewer.sendMessage(Component.text("Duration: ", NamedTextColor.GRAY)
                    .append(Component.text(vanishData.getFormattedDuration(), NamedTextColor.GREEN)))
                
                if (vanishData.vanishedBy != null) {
                    val vanisherName = MinecraftServer.getConnectionManager().onlinePlayers
                        .find { it.uuid == vanishData.vanishedBy }?.username ?: "Unknown"
                    viewer.sendMessage(Component.text("Vanished by: ", NamedTextColor.GRAY)
                        .append(Component.text(vanisherName, NamedTextColor.BLUE)))
                }
                
                if (vanishData.reason != null) {
                    viewer.sendMessage(Component.text("Reason: ", NamedTextColor.GRAY)
                        .append(Component.text(vanishData.reason, NamedTextColor.WHITE)))
                }
                
                // Check if viewer can see the vanished player
                val canSee = plugin.vanishPluginMessageListener.canSeeVanished(viewer, target.uuid)
                viewer.sendMessage(Component.text("You can see them: ", NamedTextColor.GRAY)
                    .append(Component.text(if (canSee) "YES" else "NO", if (canSee) NamedTextColor.GREEN else NamedTextColor.RED)))
            } else {
                viewer.sendMessage(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("VISIBLE", NamedTextColor.GREEN)))
            }
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
        } catch (e: Exception) {
            viewer.sendMessage(Component.text("Error checking vanish status: ${e.message}", NamedTextColor.RED))
            plugin.logger.error("Error in vanishtest status command", e)
        }
    }
    
    /**
     * Check if viewer can see a specific player
     */
    private suspend fun checkVisibility(viewer: Player, target: Player) {
        try {
            val isVanished = plugin.vanishPluginMessageListener.isPlayerVanished(target.uuid)
            val canSee = plugin.vanishPluginMessageListener.canSeeVanished(viewer, target.uuid)
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            viewer.sendMessage(Component.text("Visibility Test", NamedTextColor.YELLOW))
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            viewer.sendMessage(Component.text("Target: ", NamedTextColor.GRAY)
                .append(Component.text(target.username, NamedTextColor.WHITE)))
            viewer.sendMessage(Component.text("Is Vanished: ", NamedTextColor.GRAY)
                .append(Component.text(if (isVanished) "YES" else "NO", if (isVanished) NamedTextColor.RED else NamedTextColor.GREEN)))
            viewer.sendMessage(Component.text("You Can See: ", NamedTextColor.GRAY)
                .append(Component.text(if (canSee) "YES" else "NO", if (canSee) NamedTextColor.GREEN else NamedTextColor.RED)))
            
            if (isVanished) {
                val vanishData = plugin.vanishPluginMessageListener.getVanishData(target.uuid)
                if (vanishData != null) {
                    viewer.sendMessage(Component.text("Vanish Level: ", NamedTextColor.GRAY)
                        .append(Component.text(vanishData.level.displayName, NamedTextColor.YELLOW)))
                }
            }
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
        } catch (e: Exception) {
            viewer.sendMessage(Component.text("Error checking visibility: ${e.message}", NamedTextColor.RED))
            plugin.logger.error("Error in vanishtest visibility command", e)
        }
    }
    
    /**
     * Force refresh all vanish statuses
     */
    private suspend fun refreshVanishStatuses(viewer: Player) {
        try {
            viewer.sendMessage(Component.text("Refreshing vanish statuses for all online players...", NamedTextColor.YELLOW))
            
            plugin.vanishEventListener.refreshAllVanishStatuses()
            
            viewer.sendMessage(Component.text("Vanish status refresh completed!", NamedTextColor.GREEN))
            
        } catch (e: Exception) {
            viewer.sendMessage(Component.text("Error refreshing vanish statuses: ${e.message}", NamedTextColor.RED))
            plugin.logger.error("Error in vanishtest refresh command", e)
        }
    }
    
    /**
     * Show monitoring statistics
     */
    private fun showMonitorStats(viewer: Player) {
        try {
            val monitorInfo = plugin.vanishStatusMonitor.getTrackingInfo()
            val vanishedPlayers = plugin.vanishPluginMessageListener.getVanishedPlayers()
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            viewer.sendMessage(Component.text("Hybrid Vanish System Monitor", NamedTextColor.YELLOW))
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
            viewer.sendMessage(Component.text("HTTP Monitor Enabled: ", NamedTextColor.GRAY)
                .append(Component.text(monitorInfo["enabled"].toString(), NamedTextColor.GREEN)))
            viewer.sendMessage(Component.text("Plugin Messages: ", NamedTextColor.GRAY)
                .append(Component.text("ACTIVE", NamedTextColor.GREEN)))
            viewer.sendMessage(Component.text("Tracked Players: ", NamedTextColor.GRAY)
                .append(Component.text(monitorInfo["tracked_players"].toString(), NamedTextColor.WHITE)))
            viewer.sendMessage(Component.text("Currently Vanished: ", NamedTextColor.GRAY)
                .append(Component.text(vanishedPlayers.size.toString(), NamedTextColor.YELLOW)))
            
            // Show vanish level breakdown
            val levelCounts = mutableMapOf<VanishLevel, Int>()
            vanishedPlayers.values.forEach { data ->
                levelCounts[data.level] = (levelCounts[data.level] ?: 0) + 1
            }
            
            if (levelCounts.isNotEmpty()) {
                viewer.sendMessage(Component.text("By Level:", NamedTextColor.GRAY))
                levelCounts.forEach { (level, count) ->
                    viewer.sendMessage(Component.text("  ${level.displayName}: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(count.toString(), NamedTextColor.WHITE)))
                }
            }
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
        } catch (e: Exception) {
            viewer.sendMessage(Component.text("Error showing monitor stats: ${e.message}", NamedTextColor.RED))
            plugin.logger.error("Error in vanishtest monitor command", e)
        }
    }
    
    /**
     * List all vanished players that the viewer can see
     */
    private suspend fun listVanishedPlayers(viewer: Player) {
        try {
            val vanishedPlayers = plugin.vanishPluginMessageListener.getVanishedPlayers()
            
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            viewer.sendMessage(Component.text("Vanished Players (Hybrid System)", NamedTextColor.YELLOW))
            viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
            
            if (vanishedPlayers.isEmpty()) {
                viewer.sendMessage(Component.text("No players are currently vanished", NamedTextColor.GRAY))
            } else {
                var visibleCount = 0
                
                vanishedPlayers.forEach { (playerUuid, vanishData) ->
                    val canSee = plugin.vanishPluginMessageListener.canSeeVanished(viewer, playerUuid)
                    if (canSee) {
                        val player = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == playerUuid }
                        val playerName = player?.username ?: "Unknown"
                        
                        viewer.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(playerName, NamedTextColor.WHITE))
                            .append(Component.text(" (", NamedTextColor.GRAY))
                            .append(Component.text(vanishData.level.displayName, NamedTextColor.YELLOW))
                            .append(Component.text(", ", NamedTextColor.GRAY))
                            .append(Component.text(vanishData.getFormattedDuration(), NamedTextColor.GREEN))
                            .append(Component.text(")", NamedTextColor.GRAY)))
                        
                        visibleCount++
                    }
                }
                
                viewer.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
                viewer.sendMessage(Component.text("Showing $visibleCount of ${vanishedPlayers.size} vanished players", NamedTextColor.GRAY))
            }
            
        } catch (e: Exception) {
            viewer.sendMessage(Component.text("Error listing vanished players: ${e.message}", NamedTextColor.RED))
            plugin.logger.error("Error in vanishtest list command", e)
        }
    }
}
