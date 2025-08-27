package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.api.PunishmentApiResult
import huncho.main.lobby.models.PunishmentRequest
import huncho.main.lobby.utils.MessageUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class BanCommand(private val plugin: LobbyPlugin) : Command("ban") {
    
    init {
        // Only show command to players with permission
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.ban").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
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
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.ban").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.ban or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the punishment using the proper API
                    executeBanPunishment(sender, target, reason)
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
    
    private fun executeBanPunishment(player: Player, target: String, reason: String) {
        GlobalScope.launch {
            try {
                val request = PunishmentRequest(
                    target = target,
                    type = "BAN",
                    reason = reason,
                    staffId = player.uuid.toString(),
                    duration = null, // Permanent ban
                    silent = false,
                    clearInventory = false,
                    priority = PunishmentRequest.Priority.NORMAL
                )
                
                val result = plugin.radiumPunishmentAPI.issuePunishment(request)
                
                val message = if (result.isSuccess) {
                    val response = (result as PunishmentApiResult.Success).response
                    "&aSuccessfully banned ${response.target}: ${response.message}"
                } else {
                    "&cFailed to ban $target: ${result.getErrorMessage()}"
                }
                
                MessageUtils.sendMessage(player, message)
                LobbyPlugin.logger.info("${player.username} attempted to ban $target - Success: ${result.isSuccess}")
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&cAn error occurred while processing the ban: ${e.message}")
                LobbyPlugin.logger.error("Error processing ban command from ${player.username}", e)
            }
        }
    }
}
