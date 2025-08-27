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
                    plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.check").get() ||
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
            plugin.radiumIntegration.hasPermission(sender.uuid, "radium.punish.check").thenAccept { hasRadiumPerm ->
                plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").thenAccept { hasLobbyAdmin ->
                    if (!hasRadiumPerm && !hasLobbyAdmin) {
                        MessageUtils.sendMessage(sender, "&cYou don't have permission to use this command.")
                        MessageUtils.sendMessage(sender, "&7Required: radium.punish.check or lobby.admin")
                        return@thenAccept
                    }
                    
                    // Execute the command if permission check passes
                    checkPlayerPunishments(sender, target)
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
    
    private fun checkPlayerPunishments(player: Player, target: String) {
        GlobalScope.launch {
            try {
                MessageUtils.sendMessage(player, "&7Fetching punishment history for &e$target&7...")
                
                val punishments = plugin.radiumPunishmentAPI.getActivePunishments(target)
                
                if (punishments.isEmpty()) {
                    MessageUtils.sendMessage(player, "&aNo active punishments found for &e$target&a.")
                    return@launch
                }
                
                MessageUtils.sendMessage(player, "&6=== Punishment History for &e$target &6===")
                
                punishments.forEach { punishment ->
                    val status = if (punishment.isCurrentlyActive()) "&cACTIVE" else "&7EXPIRED"
                    val duration = if (punishment.expiresAt != null) {
                        val remaining = punishment.expiresAt - System.currentTimeMillis()
                        if (remaining > 0) {
                            "&7(${formatDuration(remaining)} remaining)"
                        } else {
                            "&7(Expired)"
                        }
                    } else {
                        "&7(Permanent)"
                    }
                    
                    MessageUtils.sendMessage(player, "&7• &e${punishment.type.name} $status $duration")
                    MessageUtils.sendMessage(player, "&7  Reason: &f${punishment.reason}")
                    MessageUtils.sendMessage(player, "&7  By: &f${punishment.punisherName ?: "Unknown"}")
                    MessageUtils.sendMessage(player, "&7  Date: &f${formatDate(punishment.createdAt)}")
                    MessageUtils.sendMessage(player, "")
                }
                
                LobbyPlugin.logger.info("${player.username} checked punishment history for $target")
                
            } catch (e: Exception) {
                MessageUtils.sendMessage(player, "&cAn error occurred while checking punishments: ${e.message}")
                LobbyPlugin.logger.error("Error checking punishments for $target by ${player.username}", e)
            }
        }
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return formatter.format(date)
    }
}
