package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class FlyCommand(private val plugin: LobbyPlugin) : Command("fly") {
    
    init {
        // Only allow admins to use fly command
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true // Allow console
            
            try {
                val hasAdmin = plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                val hasFly = plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.fly").get()
                return@setCondition hasAdmin || hasFly
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
                plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.fly").thenAccept { hasFly ->
                    if (hasAdmin || hasFly) {
                        val newFlyState = !sender.isFlying
                        sender.isAllowFlying = newFlyState
                        sender.isFlying = newFlyState
                        
                        val message = if (newFlyState) {
                            plugin.configManager.getString(plugin.configManager.mainConfig, "messages.fly_enabled", "&aFly mode enabled!")
                        } else {
                            plugin.configManager.getString(plugin.configManager.mainConfig, "messages.fly_disabled", "&cFly mode disabled!")
                        }
                        
                        MessageUtils.sendMessage(sender, message)
                    } else {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command!")
                    }
                }
            }
        }
    }
}
