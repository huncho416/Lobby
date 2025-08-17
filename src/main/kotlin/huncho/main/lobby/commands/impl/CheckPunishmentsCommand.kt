package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class CheckPunishmentsCommand(private val plugin: LobbyPlugin) : Command("checkpunishments", "punishments", "history") {
    
    init {
        // Only show in tab completion for staff - Radium will handle actual permission checking
        setCondition { sender, _ ->
            when (sender) {
                is Player -> {
                    try {
                        plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.checkpunishments").get() ||
                        plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        
        // /checkpunishments <target>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            executeRadiumCommand(sender, "checkpunishments $target")
            
        }, targetArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MessageUtils.sendMessage(sender, "&cUsage: /checkpunishments <player>")
            }
        }
    }
    
    private fun executeRadiumCommand(player: Player, command: String) {
        GlobalScope.launch {
            val result = plugin.radiumCommandForwarder.executeCommand(player.username, command)
            
            // For checkpunishments, always show the response (success or error)
            val message = if (result.success) {
                "&a${result.message}"
            } else {
                "&c${result.message}"
            }
            
            MessageUtils.sendMessage(player, message)
            LobbyPlugin.logger.info("${player.username} executed: $command - Success: ${result.success}")
        }
    }
}
