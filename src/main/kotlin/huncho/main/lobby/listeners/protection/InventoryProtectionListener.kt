package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.inventory.AbstractInventory

class InventoryProtectionListener(private val plugin: LobbyPlugin) : EventListener<InventoryClickEvent> {
    
    override fun eventType(): Class<InventoryClickEvent> = InventoryClickEvent::class.java
    
    override fun run(event: InventoryClickEvent): EventListener.Result {
        val player = event.player
        val inventory = event.inventory
        
        // Check if inventory movement is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.inventory-move")) {
            // Allow clicks in custom menus (server selector, gadgets, etc.)
            if (isCustomMenu(inventory)) {
                return EventListener.Result.SUCCESS
            }
            
            // Check bypass permission
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.inv.bypass").get()) {
                // Prevent moving items in player inventory
                if (event.clickType == ClickType.LEFT_CLICK || 
                    event.clickType == ClickType.RIGHT_CLICK ||
                    event.clickType == ClickType.SHIFT_CLICK) {
                    
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.inventory-move")
                    MessageUtils.sendMessage(player, message)
                    return EventListener.Result.SUCCESS
                }
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun isCustomMenu(inventory: AbstractInventory?): Boolean {
        if (inventory == null) return false
        
        // Check if this is a custom menu by checking inventory size and structure
        // Our custom menus are typically 9-slot (visibility) or larger (server selector)
        val size = inventory.size
        
        // For 9-slot inventories (player visibility menu), check if it has items in slots 2, 4, 6
        if (size == 9) {
            val hasVisibilityPattern = inventory.getItemStack(2) != null && 
                                     inventory.getItemStack(4) != null && 
                                     inventory.getItemStack(6) != null
            if (hasVisibilityPattern) return true
        }
        
        // For larger inventories (server selector), check if slots match configured server slots
        if (size >= 27) {
            val items = plugin.configManager.getMap(plugin.configManager.serversConfig, "servers-menu.items")
            val hasServerMenuPattern = items.values.any { itemData ->
                val data = itemData as? Map<String, Any> ?: return@any false
                val slot = when (val slotValue = data["slot"]) {
                    is Int -> slotValue
                    is String -> slotValue.toIntOrNull() ?: -1
                    else -> -1
                }
                slot >= 0 && slot < size && inventory.getItemStack(slot) != null
            }
            if (hasServerMenuPattern) return true
        }
        
        return false
    }
}
