package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class LeaveQueueCommand(private val plugin: LobbyPlugin) : Command("leavequeue") {
    
    init {
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "hub.command.queue").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.player").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    true // Allow by default for queue commands
                }
                else -> false
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            plugin.queueManager.leaveQueue(sender)
        }
    }
}
