package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerSwapItemEvent

/**
 * Protects lobby items in player inventory from being swapped with F key
 */
class HotbarProtectionListener(private val plugin: LobbyPlugin) : EventListener<PlayerSwapItemEvent> {
    
    override fun eventType(): Class<PlayerSwapItemEvent> = PlayerSwapItemEvent::class.java
    
    override fun run(event: PlayerSwapItemEvent): EventListener.Result {
        val player = event.player
        
        // Check if inventory movement is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.inventory_move")) {
            // Check bypass permission
            val hasBypass = try {
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.inventory").get() ||
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get()
            } catch (e: Exception) {
                false
            }
            
            if (!hasBypass) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.inventory_move", "&cInventory interactions are disabled!")
                MessageUtils.sendMessage(player, message)
                return EventListener.Result.INVALID
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
