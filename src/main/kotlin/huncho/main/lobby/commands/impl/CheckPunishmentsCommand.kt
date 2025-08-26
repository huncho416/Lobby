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
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.checkpunishments").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
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
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.checkpunishments").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.command.checkpunishments or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the command if permission check passes
                    executeRadiumCommand(sender, "checkpunishments $target")
                }.exceptionally { ex ->
                    MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                    null
                }
            }.exceptionally { ex ->
                MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                null
            }
            
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
