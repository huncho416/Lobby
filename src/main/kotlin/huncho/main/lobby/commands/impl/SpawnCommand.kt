package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class SpawnCommand(private val plugin: LobbyPlugin) : Command("spawn") {
    
    init {
        // Remove condition check - all players should be able to use /spawn
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            // All players can use /spawn
            plugin.spawnManager.teleportToSpawnWithMessage(sender)
        }
    }
}

