package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class PauseQueueCommand(private val plugin: LobbyPlugin) : Command("pausequeue") {
    
    init {
        val queueArg = ArgumentType.Word("queue").from("practice", "minigames", "survival", "creative")
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            sender.checkPermissionAndExecute("hub.pausequeue") {
                val queueName = context.get(queueArg)
                val success = plugin.queueManager.pauseQueue(queueName)
                
                if (success) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-paused")
                        .replace("{queue}", queueName)
                    MessageUtils.sendMessage(sender, message)
                } else {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-already-paused")
                        .replace("{queue}", queueName)
                    MessageUtils.sendMessage(sender, message)
                }
            }
            
        }, queueArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val usage = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.usage")
                .replace("{usage}", "/pausequeue <queue>")
            MessageUtils.sendMessage(sender, usage)
        }
    }
}

