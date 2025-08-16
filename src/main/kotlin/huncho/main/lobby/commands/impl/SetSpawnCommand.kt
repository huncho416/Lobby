package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class SetSpawnCommand(private val plugin: LobbyPlugin) : Command("setspawn") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.setspawn") {
                if (plugin.spawnManager.setSpawnLocation(player)) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.spawn-set")
                    MessageUtils.sendMessage(player, message)
                    
                    val spawnInfo = plugin.spawnManager.getSpawnInfo()
                    MessageUtils.sendMessage(player, "&7Location: &b${spawnInfo["x"]}, ${spawnInfo["y"]}, ${spawnInfo["z"]}")
                } else {
                    MessageUtils.sendMessage(player, "&cFailed to set spawn location!")
                }
            }
        }
    }
}

