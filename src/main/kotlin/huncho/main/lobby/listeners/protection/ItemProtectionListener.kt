package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent

import net.minestom.server.item.Material

class ItemProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<ItemDropEvent> {
    
    override fun eventType(): Class<ItemDropEvent> = ItemDropEvent::class.java
    
    override fun run(event: ItemDropEvent): EventListener.Result {
        val entity = event.entity
        
        if (entity is Player) {
            // Skip protection for admins (using Radium integration)
            try {
                val hasBypass = plugin.radiumIntegration.hasPermission(entity.uuid, "lobby.admin").get() ||
                               plugin.radiumIntegration.hasPermission(entity.uuid, "lobby.bypass.protection").get()
                if (hasBypass) return EventListener.Result.SUCCESS
            } catch (e: Exception) {
                // If permission check fails, don't skip protection
            }
            
            val droppedItem = event.itemStack
            
            // Check if trying to drop a join item
            if (droppedItem != null && droppedItem.material() != Material.AIR) {
                // Use material-based identification since component access is problematic
                if (monitor.isJoinItemByMaterial(droppedItem.material())) {
                    plugin.logger.debug("[ItemProtection] Prevented ${entity.username} from dropping join item: ${droppedItem.material().name()}")
                    return EventListener.Result.INVALID
                }
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
        if (entity !is Player) {
            return EventListener.Result.SUCCESS
        }
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(entity.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(entity.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }
        
        // For now, prevent all item pickups for non-admins to keep lobby clean
        plugin.logger.debug("[ItemProtection] Prevented ${entity.username} from picking up item")
        return EventListener.Result.INVALID
    }
}
