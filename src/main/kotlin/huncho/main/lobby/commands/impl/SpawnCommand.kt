package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class SpawnCommand(private val plugin: LobbyPlugin) : Command("spawn") {
    
    init {
        // Only allow admins to use spawn command
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true // Allow console
            
            try {
                val hasAdmin = plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                val hasSpawn = plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.spawn").get()
                return@setCondition hasAdmin || hasSpawn
            } catch (e: Exception) {
                LobbyPlugin.logger.debug("Permission check failed for ${sender.username}: ${e.message}")
                return@setCondition false
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            // Check permissions before execution
            plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasAdmin ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.spawn").thenAccept { hasSpawn ->
                    if (hasAdmin || hasSpawn) {
                        plugin.spawnManager.teleportToSpawnWithMessage(sender)
                    } else {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command!")
                    }
                }
            }
        }
    }
}

