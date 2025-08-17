package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class BuildCommand(private val plugin: LobbyPlugin) : Command("build", "buildmode") {
    
    init {
        setCondition { sender, _ ->
            if (sender !is Player) return@setCondition true // Allow console
            
            try {
                val hasBuild = plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.build").get()
                val hasLobbyAdmin = plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                return@setCondition hasBuild || hasLobbyAdmin
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
            
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.build").thenAccept { hasBuild ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept adminCheck@{ hasAdmin ->
                    if (!hasBuild && !hasAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command!")
                        return@adminCheck
                    }
                    
                    val uuid = sender.uuid.toString()
                    val newBuildState = plugin.protectionManager.toggleBuildMode(uuid)
                    
                    val message = if (newBuildState) {
                        "&aBuild mode enabled!"
                    } else {
                        "&cBuild mode disabled!"
                    }
                    
                    MessageUtils.sendMessage(sender, message)
                }
            }
        }
    }
}
