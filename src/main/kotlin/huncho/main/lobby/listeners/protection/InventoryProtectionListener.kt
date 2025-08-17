package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.inventory.AbstractInventory
import net.minestom.server.item.ItemStack

class InventoryProtectionListener(private val plugin: LobbyPlugin) : EventListener<InventoryPreClickEvent> {
    
    override fun eventType(): Class<InventoryPreClickEvent> = InventoryPreClickEvent::class.java
    
    override fun run(event: InventoryPreClickEvent): EventListener.Result {
        val player = event.player
        val inventory = event.inventory
        
        // Check if inventory movement is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.inventory_move")) {
            // Allow clicks in custom menus (server selector, gadgets, etc.)
            if (isCustomMenu(inventory)) {
                return EventListener.Result.SUCCESS
            }
            
            // Check bypass permission first
            val hasBypass = try {
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.inventory").get() ||
                plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get()
            } catch (e: Exception) {
                false
            }
            
            if (!hasBypass) {
                // Check if this is trying to move a join item (compass or redstone)
                val clickedItem = event.clickedItem
                
                if (isJoinItem(clickedItem)) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.inventory_move", "&cYou cannot move lobby items!")
                    MessageUtils.sendMessage(player, message)
                    return EventListener.Result.INVALID
                }
                
                // Prevent any inventory interactions if it's the player's own inventory
                if (inventory == player.inventory) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "messages.protection.inventory_move", "&cInventory interactions are disabled!")
                    MessageUtils.sendMessage(player, message)
                    return EventListener.Result.INVALID
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun isJoinItem(item: ItemStack?): Boolean {
        if (item == null || item.isAir) return false
        
        val material = item.material()
        // Check if it's a compass or redstone (join items)
        return material.name().contains("COMPASS", ignoreCase = true) || 
               material.name().contains("REDSTONE", ignoreCase = true)
    }
    
    private fun isCustomMenu(inventory: AbstractInventory): Boolean {
        // Custom menu detection logic (basic implementation)
        // In a real implementation, you'd check specific menu titles or NBT data
        
        // Check if inventory is not the player's main inventory
        if (inventory.size != 36) {
            return true // Likely a custom menu if it's not standard player inventory size
        }
        
        return false
    }
}
