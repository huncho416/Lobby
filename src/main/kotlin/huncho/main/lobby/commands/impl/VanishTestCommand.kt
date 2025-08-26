package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import kotlinx.coroutines.runBlocking

/**
 * Administrative command for testing and managing vanish system integration
 */
class VanishTestCommand(private val plugin: LobbyPlugin) : Command("vanishtest") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players")
                return@setDefaultExecutor
            }
            
            showVanishStatus(sender)
        }
        
        val playerArg = ArgumentType.String("player")
        
        // /vanishtest status [player] - Check vanish status
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players")
                return@addSyntax
            }
            
            val targetName = context.get(playerArg)
            val target = plugin.lobbyInstance.players.find { it.username.equals(targetName, true) }
            
            if (target == null) {
                MessageUtils.sendMessage(sender, "&cPlayer not found!")
                return@addSyntax
            }
            
            checkPlayerVanishStatus(sender, target)
        }, ArgumentType.Literal("status"), playerArg)
        
        // /vanishtest refresh - Force refresh vanish statuses
        addSyntax({ sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players")
                return@addSyntax
            }
            
            refreshVanishStatuses(sender)
        }, ArgumentType.Literal("refresh"))
        
        // /vanishtest visibility [player] - Check if you can see a player
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players")
                return@addSyntax
            }
            
            val targetName = context.get(playerArg)
            val target = plugin.lobbyInstance.players.find { it.username.equals(targetName, true) }
            
            if (target == null) {
                MessageUtils.sendMessage(sender, "&cPlayer not found!")
                return@addSyntax
            }
            
            checkVanishVisibility(sender, target)
        }, ArgumentType.Literal("visibility"), playerArg)
        
        // /vanishtest monitor - Show vanish monitor stats
        addSyntax({ sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players")
                return@addSyntax
            }
            
            showMonitorStats(sender)
        }, ArgumentType.Literal("monitor"))
        
        // Set permission requirement
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition false
            
            return@setCondition runBlocking {
                plugin.radiumIntegration.hasPermission(sender.uuid, "hub.vanishtest").join()
            }
        }
    }
    
    private fun showVanishStatus(player: Player) {
        runBlocking {
            try {
                val isVanished = plugin.radiumIntegration.isPlayerVanished(player.uuid).join()
                val status = if (isVanished) "&cVanished" else "&aVisible"
                
                MessageUtils.sendMessage(player, "&7Your vanish status: $status")
                
                // Show who can see you if vanished
                if (isVanished) {
                    MessageUtils.sendMessage(player, "&7Players who can see you:")
                    plugin.lobbyInstance.players.forEach { otherPlayer ->
                        if (otherPlayer != player) {
                            val canSee = plugin.radiumIntegration.canSeeVanishedPlayer(otherPlayer.uuid, player.uuid).join()
                            val visibility = if (canSee) "&a✓" else "&c✗"
                            MessageUtils.sendMessage(player, "&7  $visibility ${otherPlayer.username}")
                        }
                    }
                }
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&cError checking vanish status: ${e.message}")
            }
        }
    }
    
    private fun checkPlayerVanishStatus(sender: Player, target: Player) {
        runBlocking {
            try {
                val isVanished = plugin.radiumIntegration.isPlayerVanished(target.uuid).join()
                val status = if (isVanished) "&cVanished" else "&aVisible"
                
                MessageUtils.sendMessage(sender, "&7${target.username} is: $status")
                
                if (isVanished) {
                    val canSee = plugin.radiumIntegration.canSeeVanishedPlayer(sender.uuid, target.uuid).join()
                    val visibility = if (canSee) "&aYou can see them" else "&cYou cannot see them"
                    MessageUtils.sendMessage(sender, "&7$visibility")
                }
            } catch (e: Exception) {
                MessageUtils.sendMessage(sender, "&cError checking vanish status: ${e.message}")
            }
        }
    }
    
    private fun checkVanishVisibility(sender: Player, target: Player) {
        runBlocking {
            try {
                val isVanished = plugin.radiumIntegration.isPlayerVanished(target.uuid).join()
                
                if (!isVanished) {
                    MessageUtils.sendMessage(sender, "&7${target.username} is not vanished")
                    return@runBlocking
                }
                
                val canSee = plugin.radiumIntegration.canSeeVanishedPlayer(sender.uuid, target.uuid).join()
                val visibility = if (canSee) "&aYou can see ${target.username}" else "&cYou cannot see ${target.username}"
                
                MessageUtils.sendMessage(sender, "&7$visibility")
            } catch (e: Exception) {
                MessageUtils.sendMessage(sender, "&cError checking visibility: ${e.message}")
            }
        }
    }
    
    private fun refreshVanishStatuses(sender: Player) {
        runBlocking {
            try {
                MessageUtils.sendMessage(sender, "&7Refreshing vanish statuses...")
                
                plugin.vanishEventListener.refreshAllVanishStatuses()
                
                MessageUtils.sendMessage(sender, "&aVanish statuses refreshed!")
            } catch (e: Exception) {
                MessageUtils.sendMessage(sender, "&cError refreshing vanish statuses: ${e.message}")
            }
        }
    }
    
    private fun showMonitorStats(sender: Player) {
        try {
            val stats = plugin.vanishStatusMonitor.getTrackingInfo()
            
            MessageUtils.sendMessage(sender, "&7=== Vanish Monitor Stats ===")
            MessageUtils.sendMessage(sender, "&7Enabled: &f${stats["enabled"]}")
            MessageUtils.sendMessage(sender, "&7Tracked Players: &f${stats["tracked_players"]}")
            MessageUtils.sendMessage(sender, "&7Vanished Count: &f${stats["vanished_count"]}")
            
            val onlineCount = plugin.lobbyInstance.players.size
            MessageUtils.sendMessage(sender, "&7Online Players: &f$onlineCount")
        } catch (e: Exception) {
            MessageUtils.sendMessage(sender, "&cError getting monitor stats: ${e.message}")
        }
    }
}
