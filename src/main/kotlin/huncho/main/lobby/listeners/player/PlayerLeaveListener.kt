package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerDisconnectEvent
import kotlinx.coroutines.runBlocking

class PlayerLeaveListener(private val plugin: LobbyPlugin) : EventListener<PlayerDisconnectEvent> {
    
    override fun eventType(): Class<PlayerDisconnectEvent> = PlayerDisconnectEvent::class.java
    
    override fun run(event: PlayerDisconnectEvent): EventListener.Result {
        val player = event.player
        
        runBlocking {
            handlePlayerLeave(player)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private suspend fun handlePlayerLeave(player: net.minestom.server.entity.Player) {
        try {
            // Sync with Radium
            plugin.radiumIntegration.syncPlayerOnLeave(player)
            
            // Remove from queue if in one
            plugin.queueManager.removePlayerFromQueue(player.uuid)
            
            // Remove scoreboard
            plugin.scoreboardManager.removePlayer(player)
            
            // Update tab lists for remaining players
            plugin.tabListManager.onPlayerQuit(player)
            
            // Send leave message
            sendLeaveMessage(player)
            
            // Clean up player data
            plugin.visibilityManager.removePlayer(player.uuid.toString())
            
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Error handling player leave for ${player.username}", e)
        }
    }
    
    private fun sendLeaveMessage(player: net.minestom.server.entity.Player) {
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.leave-messages")) {
            return
        }
        
        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "leave-message")
            .replace("{player}", player.username)
        
        // Broadcast to remaining players
        plugin.lobbyInstance.players.forEach { onlinePlayer ->
            if (onlinePlayer != player) {
                MessageUtils.sendMessage(onlinePlayer, message)
            }
        }
    }
}
