package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class BanCommand(private val plugin: LobbyPlugin) : Command("ban") {
    
    init {
        // Remove complex condition check for now - handle permissions in execution
        val targetArg = ArgumentType.Word("target")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /ban <target> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /ban <player> <reason>")
                return@addSyntax
            }
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.ban").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.command.ban or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the command if permission check passes
                    executeRadiumCommand(sender, "ban $target $reason")
                }.exceptionally { ex ->
                    MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                    null
                }
            }.exceptionally { ex ->
                MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                null
            }
            
        }, targetArg, reasonArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MessageUtils.sendMessage(sender, "&cUsage: /ban <player> <reason>")
            }
        }
    }
    
    private fun executeRadiumCommand(player: Player, command: String) {
        GlobalScope.launch {
            val result = plugin.radiumCommandForwarder.executeCommand(player.username, command)
            
            // Send feedback to the player based on success/failure
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
