package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryPreClickEvent

import net.minestom.server.item.Material

class InventoryProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<InventoryPreClickEvent> {
    
    override fun eventType(): Class<InventoryPreClickEvent> = InventoryPreClickEvent::class.java
    
    override fun run(event: InventoryPreClickEvent): EventListener.Result {
        val player = event.player
        val clickedItem = event.clickedItem
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }
        
        // Check if this is a player's inventory interaction
        if (event.inventory == null || event.inventory == player.inventory) {
            // This is the player's own inventory - check if they're trying to move join items
            if (clickedItem != null && clickedItem.material() != Material.AIR) {
                // Use material-based identification since component access is problematic
                if (monitor.isJoinItemByMaterial(clickedItem.material())) {
                    plugin.logger.debug("[InventoryProtection] Prevented ${player.username} from moving join item: ${clickedItem.material().name()}")
                    return EventListener.Result.INVALID
                }
            }
            
            // ADDITIONAL CHECK: Handle number key swaps (1-9 keys)
            // Check if they're trying to swap with a join item slot
            if (event.slot >= 9) { // Only check inventory slots (not hotbar)
                // Check all hotbar slots for join items and cancel if any exist
                for (i in 0..8) {
                    val hotbarItem = player.inventory.getItemStack(i)
                    if (hotbarItem != null && hotbarItem.material() != Material.AIR) {
                        // Use material-based identification since component access is problematic
                        if (monitor.isJoinItemByMaterial(hotbarItem.material())) {
                            // Found a join item in hotbar - cancel any inventory click that could swap with it
                            plugin.logger.debug("[InventoryProtection] Prevented ${player.username} from swapping with join item slot")
                            return EventListener.Result.INVALID
                        }
                    }
                }
            }
        } else {
            // This is a custom inventory (GUI) - allow all interactions
            return EventListener.Result.SUCCESS
        }
        
        return EventListener.Result.SUCCESS
    }
}
