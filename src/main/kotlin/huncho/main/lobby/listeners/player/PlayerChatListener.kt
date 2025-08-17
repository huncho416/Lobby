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
}
