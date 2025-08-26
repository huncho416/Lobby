package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class SetSpawnCommand(private val plugin: LobbyPlugin) : Command("setspawn") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.setspawn").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.setspawn").thenAccept { hasSetSpawn ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept adminCheck@{ hasAdmin ->
                    if (!hasSetSpawn && !hasAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command!")
                        return@adminCheck
                    }
                    
                    if (plugin.spawnManager.setSpawnLocation(sender)) {
                        MessageUtils.sendMessage(sender, "&aSpawn location set!")
                        
                        val spawnInfo = plugin.spawnManager.getSpawnInfo()
                        MessageUtils.sendMessage(sender, "&7Location: &b${spawnInfo["x"]}, ${spawnInfo["y"]}, ${spawnInfo["z"]}")
                    } else {
                        MessageUtils.sendMessage(sender, "&cFailed to set spawn location!")
                    }
                }
            }
        }
    }
}

