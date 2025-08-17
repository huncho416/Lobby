package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent

class ItemProtectionListener(private val plugin: LobbyPlugin) : EventListener<ItemDropEvent> {
    
    override fun eventType(): Class<ItemDropEvent> = ItemDropEvent::class.java
    
    override fun run(event: ItemDropEvent): EventListener.Result {
        val player = event.player
        
        // Check if item dropping is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.anti_drop")) {
            // Check bypass permission
            val hasBypass = try {
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.drop").get() ||
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get()
            } catch (e: Exception) {
                false
            }
            
            if (!hasBypass) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.item_drop", "&cYou cannot drop items here!")
                MessageUtils.sendMessage(player, message)
                return EventListener.Result.INVALID
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}

class ItemPickupListener(private val plugin: LobbyPlugin) : EventListener<PickupItemEvent> {
    
    override fun eventType(): Class<PickupItemEvent> = PickupItemEvent::class.java
    
    override fun run(event: PickupItemEvent): EventListener.Result {
        val entity = event.livingEntity
        
        // Only handle player pickups
        if (entity !is net.minestom.server.entity.Player) {
            return EventListener.Result.SUCCESS
        }
        
        val player = entity as net.minestom.server.entity.Player
        
        // Check if item pickup is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.anti_pickup")) {
            // Check bypass permission
            val hasBypass = try {
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.pickup").get() ||
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get()
            } catch (e: Exception) {
                false
            }
            
            if (!hasBypass) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.item_pickup", "&cYou cannot pickup items here!")
                MessageUtils.sendMessage(player, message)
                return EventListener.Result.INVALID
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
