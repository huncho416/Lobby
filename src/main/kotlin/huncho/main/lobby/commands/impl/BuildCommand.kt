package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class BuildCommand(private val plugin: LobbyPlugin) : Command("build", "buildmode") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            plugin.radiumIntegration.hasPermission(sender.uuid, "hub.command.build").thenAccept { hasPermission ->
                if (!hasPermission) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.no-permission")
                    MessageUtils.sendMessage(sender, message)
                    return@thenAccept
                }
                
                val uuid = sender.uuid.toString()
                val newBuildState = plugin.protectionManager.toggleBuildMode(uuid)
                
                val message = if (newBuildState) {
                    plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.build-enabled")
                } else {
                    plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.build-disabled")
                }
                
                MessageUtils.sendMessage(sender, message)
            }
        }
    }
}
