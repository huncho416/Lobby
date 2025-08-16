package huncho.main.lobby.features.menus

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.event.EventListener

class ServerSelectorMenu(private val plugin: LobbyPlugin) {
    
    fun open(player: Player) {
        val title = plugin.configManager.getString(plugin.configManager.serversConfig, "servers-menu.title")
        val size = plugin.configManager.getInt(plugin.configManager.serversConfig, "servers-menu.size", 27)
        
        val inventoryType = when (size) {
            9 -> InventoryType.CHEST_1_ROW
            18 -> InventoryType.CHEST_2_ROW
            27 -> InventoryType.CHEST_3_ROW
            36 -> InventoryType.CHEST_4_ROW
            45 -> InventoryType.CHEST_5_ROW
            54 -> InventoryType.CHEST_6_ROW
            else -> InventoryType.CHEST_3_ROW
        }
        
        val inventory = Inventory(inventoryType, MessageUtils.colorize(title))
        
        // Add server items
        val items = plugin.configManager.getMap(plugin.configManager.serversConfig, "servers-menu.items")
        
        items.forEach { (serverKey, itemData) ->
            val data = itemData as Map<String, Any>
            val slot = when (val slotValue = data["slot"]) {
                is Int -> slotValue
                is String -> slotValue.toIntOrNull() ?: return@forEach
                else -> return@forEach
            }
            val materialName = data["material"].toString().uppercase()
            val material = try {
                Material.values().find { it.name() == materialName } ?: Material.STONE
            } catch (e: Exception) {
                Material.STONE
            }
            val name = data["name"].toString()
            val lore = (data["lore"] as? List<*>)?.map { it.toString() } ?: emptyList()
            val serverName = data["server"].toString()
            
            val item = ItemStack.builder(material)
                .customName(MessageUtils.colorize(name))
                .lore(lore.map { MessageUtils.colorize(processServerPlaceholders(it, serverName)) })
                .build()
            
            inventory.setItemStack(slot, item)
        }
        
        player.openInventory(inventory)
    }
    
    private fun processServerPlaceholders(text: String, serverName: String): String {
        var result = text
        
        // Get cached server status (if Redis is enabled)
        val redisManager = plugin.redisManager
        if (redisManager != null) {
            val status = redisManager.getCachedServerStatus(serverName)
            if (status != null) {
                result = result.replace("{${serverName}_online}", status["online"].toString())
                result = result.replace("{${serverName}_status}", status["status"].toString())
            } else {
                result = result.replace("{${serverName}_online}", "?")
                result = result.replace("{${serverName}_status}", "Unknown")
            }
        } else {
            // Redis not available, use default values or get from Radium API
            result = result.replace("{${serverName}_online}", "?")
            result = result.replace("{${serverName}_status}", "Online")
        }
        
        return result
    }
}
