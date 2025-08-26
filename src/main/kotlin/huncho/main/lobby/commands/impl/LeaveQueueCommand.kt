package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class LeaveQueueCommand(private val plugin: LobbyPlugin) : Command("leavequeue") {
    
    init {
        // Remove condition check - all players should be able to leave queue
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            plugin.queueManager.leaveQueue(sender)
        }
    }
}
