package huncho.main.lobby.listeners.features

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.features.visibility.VisibilityMode
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.inventory.InventoryClickEvent

class InventoryMenuListener(private val plugin: LobbyPlugin) : EventListener<InventoryClickEvent> {
    
    override fun eventType(): Class<InventoryClickEvent> = InventoryClickEvent::class.java
    
    override fun run(event: InventoryClickEvent): EventListener.Result {
        val player = event.player
        val inventory = event.inventory
        val slot = event.slot
        
        // Simply check if it's a custom menu by checking inventory size and items
        // For server selector menus, check if slot matches configured server slots
        val items = plugin.configManager.getMap(plugin.configManager.serversConfig, "servers-menu.items")
        val isServerMenu = items.values.any { itemData ->
            val data = itemData as Map<String, Any>
            val serverSlot = when (val slotValue = data["slot"]) {
                is Int -> slotValue
                is String -> slotValue.toIntOrNull() ?: -1
                else -> -1
            }
            serverSlot == slot
        }
        
        if (isServerMenu) {
            handleServerSelectorClick(player, slot)
            return EventListener.Result.SUCCESS
        }
        
        // For visibility menu, check if slot is in range 2, 4, 6 (our visibility slots)
        if (slot in listOf(2, 4, 6) && inventory.size == 9) {
            handleVisibilityMenuClick(player, slot)
            return EventListener.Result.SUCCESS
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun handleServerSelectorClick(player: net.minestom.server.entity.Player, slot: Int) {
        val serverEntry = findServerBySlot(slot)
        
        if (serverEntry != null) {
            val serverName = serverEntry.second["server"].toString()
            plugin.queueManager.joinQueue(player, serverName)
            player.closeInventory()
        }
    }
    
    private fun handleVisibilityMenuClick(player: net.minestom.server.entity.Player, slot: Int) {
        when (slot) {
            2 -> plugin.visibilityManager.setVisibility(player, VisibilityMode.ALL)
            4 -> plugin.visibilityManager.setVisibility(player, VisibilityMode.STAFF)
            6 -> plugin.visibilityManager.setVisibility(player, VisibilityMode.NONE)
        }
        
        player.closeInventory()
    }
    
    private fun findServerBySlot(slot: Int): Pair<String, Map<String, Any>>? {
        val items = plugin.configManager.getMap(plugin.configManager.serversConfig, "servers-menu.items")
        
        return items.entries.find { (_, itemData) ->
            val data = itemData as Map<String, Any>
            val serverSlot = when (val slotValue = data["slot"]) {
                is Int -> slotValue
                is String -> slotValue.toIntOrNull() ?: -1
                else -> -1
            }
            serverSlot == slot
        }?.let { it.key to it.value as Map<String, Any> }
    }
}
