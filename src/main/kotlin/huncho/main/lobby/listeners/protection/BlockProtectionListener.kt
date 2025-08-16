package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent

class BlockProtectionListener(private val plugin: LobbyPlugin) : EventListener<PlayerBlockBreakEvent> {
    
    override fun eventType(): Class<PlayerBlockBreakEvent> = PlayerBlockBreakEvent::class.java
    
    override fun run(event: PlayerBlockBreakEvent): EventListener.Result {
        val player = event.player
        
        // Check if block breaking is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.block-break")) {
            // Check bypass permission or build mode
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.command.build").get() && 
                !plugin.protectionManager.isInBuildMode(player.uuid.toString())) {
                
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.block-break")
                MessageUtils.sendMessage(player, message)
                event.isCancelled = true
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}

class BlockPlaceListener(private val plugin: LobbyPlugin) : EventListener<PlayerBlockPlaceEvent> {
    
    override fun eventType(): Class<PlayerBlockPlaceEvent> = PlayerBlockPlaceEvent::class.java
    
    override fun run(event: PlayerBlockPlaceEvent): EventListener.Result {
        val player = event.player
        
        // Check if block placing is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.block-place")) {
            // Check bypass permission or build mode
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.command.build").get() && 
                !plugin.protectionManager.isInBuildMode(player.uuid.toString())) {
                
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.block-place")
                MessageUtils.sendMessage(player, message)
                event.isCancelled = true
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
