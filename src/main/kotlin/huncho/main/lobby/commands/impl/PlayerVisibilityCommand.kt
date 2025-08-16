package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class PlayerVisibilityCommand(private val plugin: LobbyPlugin) : Command("playervis", "visibility") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.playervis") {
                plugin.visibilityManager.toggleVisibility(player)
            }
        }
    }
}

