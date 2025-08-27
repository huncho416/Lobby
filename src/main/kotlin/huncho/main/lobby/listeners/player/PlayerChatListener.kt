package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.MinecraftServer
import kotlinx.coroutines.runBlocking

class PlayerChatListener(private val plugin: LobbyPlugin) : EventListener<PlayerChatEvent> {
    
    override fun eventType(): Class<PlayerChatEvent> = PlayerChatEvent::class.java
    
    override fun run(event: PlayerChatEvent): EventListener.Result {
        val player = event.player
        
        // Always cancel the event first to handle formatting
        event.setCancelled(true)
        
        runBlocking {
            try {
                // Check if player is muted first
                val punishmentService = plugin.punishmentService
                if (punishmentService != null) {
                    val isMuted = punishmentService.isPlayerMuted(player.uuid).join()
                    
                    if (isMuted) {
                        // Get the active mute for details
                        val mute = punishmentService.getActiveMute(player.uuid).join()
                        
                        val muteMessage = if (mute != null) {
                            buildMuteMessage(mute.getDisplayReason(), mute.expiresAt)
                        } else {
                            "&cYou are muted and cannot send messages!"
                        }
                        
                        MessageUtils.sendMessage(player, muteMessage)
                        return@runBlocking
                    }
                } else {
                    LobbyPlugin.logger.warn("PunishmentService not available, skipping mute check for ${player.username}")
                }
                
                // Check if player has permission to chat
                val hasPermission = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.chat").join()
                val hasAdmin = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").join()
                
                if (!hasPermission && !hasAdmin) {
                    val noPermMessage = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.no_permission", "&cYou don't have permission to chat!")
                    MessageUtils.sendMessage(player, noPermMessage)
                    return@runBlocking
                }
                
                // Check if chat is disabled for protection (only applies to non-bypass players)
                if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.anti_chat", false)) {
                    val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.chat").join()
                    val hasAdminBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").join()
                    if (!hasBypass && !hasAdminBypass) {
                        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.chat", "&cChat is currently disabled!")
                        MessageUtils.sendMessage(player, message)
                        return@runBlocking
                    }
                }
                
                // Apply chat formatting if enabled
                if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.chat_formatting", true)) {
                    // Format chat with Radium integration
                    val formattedMessage = plugin.radiumIntegration.formatChatMessage(
                        player,
                        event.rawMessage
                    )
                    
                    // Broadcast the formatted message to all players
                    val finalMessage = MessageUtils.colorize(formattedMessage)
                    MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                        onlinePlayer.sendMessage(finalMessage)
                    }
                } else {
                    // Fallback to default formatting
                    val fallbackMessage = "&7${player.username}: &f${event.rawMessage}"
                    MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                        onlinePlayer.sendMessage(MessageUtils.colorize(fallbackMessage))
                    }
                }
            } catch (e: Exception) {
                LobbyPlugin.logger.error("Error handling chat for ${player.username}", e)
                // Fallback to default formatting
                val fallbackMessage = "&7${player.username}: &f${event.rawMessage}"
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { onlinePlayer ->
                    onlinePlayer.sendMessage(MessageUtils.colorize(fallbackMessage))
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Build a mute message with reason and expiration details
     */
    private fun buildMuteMessage(reason: String, expiresAt: Long?): String {
        val sb = StringBuilder()
        sb.append("&cYou are muted!")
        sb.append("\n&7Reason: &f").append(reason)
        
        if (expiresAt != null) {
            val remaining = expiresAt - System.currentTimeMillis()
            if (remaining > 0) {
                sb.append("\n&7Expires in: &e").append(formatDuration(remaining))
            }
        } else {
            sb.append("\n&4This mute is permanent.")
        }
        
        return sb.toString()
    }
    
    /**
     * Format duration in a human-readable format
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
