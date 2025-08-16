package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class FlyCommand(private val plugin: LobbyPlugin) : Command("fly") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            sender.checkPermissionAndExecute("hub.command.fly") {
                val newFlyState = !sender.isFlying
                sender.isAllowFlying = newFlyState
                sender.isFlying = newFlyState
                
                val message = if (newFlyState) {
                    plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.fly-enabled")
                } else {
                    plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.fly-disabled")
                }
                
                MessageUtils.sendMessage(sender, message)
            }
        }
    }
}
