package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class JoinQueueCommand(private val plugin: LobbyPlugin) : Command("joinqueue") {
    
    init {
        val queueArg = ArgumentType.Word("queue").from("practice", "minigames", "survival", "creative")
        
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val queueName = context.get(queueArg)
            plugin.queueManager.joinQueue(sender, queueName)
            
        }, queueArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val usage = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.usage")
                .replace("{usage}", "/joinqueue <queue>")
            MessageUtils.sendMessage(sender, usage)
        }
    }
}
