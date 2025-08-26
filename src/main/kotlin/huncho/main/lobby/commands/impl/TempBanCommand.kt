package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class TempBanCommand(private val plugin: LobbyPlugin) : Command("tempban") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.tempban").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        val targetArg = ArgumentType.Word("target")
        val durationArg = ArgumentType.Word("duration")
        val reasonArg = ArgumentType.StringArray("reason")
        
        // /tempban <target> <duration> <reason...>
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            val target = context.get(targetArg)
            val duration = context.get(durationArg)
            val reasonArray = context.get(reasonArg)
            val reason = reasonArray.joinToString(" ")
            
            if (reason.isBlank()) {
                MessageUtils.sendMessage(sender, "&cUsage: /tempban <player> <duration> <reason>")
                MessageUtils.sendMessage(sender, "&7Duration examples: 1d, 2h, 30m, 1w")
                return@addSyntax
            }
            
            // Use async permission checking
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.command.tempban").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.command.tempban or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the command if permission check passes
                    executeRadiumCommand(sender, "tempban $target $duration $reason")
                }.exceptionally { ex ->
                    MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                    null
                }
            }.exceptionally { ex ->
                MessageUtils.sendMessage(sender, "&cPermission check failed: ${ex.message}")
                null
            }
            
        }, targetArg, durationArg, reasonArg)
        
        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MessageUtils.sendMessage(sender, "&cUsage: /tempban <player> <duration> <reason>")
                MessageUtils.sendMessage(sender, "&7Duration examples: 1d, 2h, 30m, 1w")
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
