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
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.item-drop")) {
            // Check bypass permission
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.options.bypass.drop").get()) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.item-drop")
                MessageUtils.sendMessage(player, message)
                return EventListener.Result.SUCCESS
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
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.item-pickup")) {
            // Check bypass permission
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.options.bypass.pick").get()) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.item-pickup")
                MessageUtils.sendMessage(player, message)
                event.isCancelled = true
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
