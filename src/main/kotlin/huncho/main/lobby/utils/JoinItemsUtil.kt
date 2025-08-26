package huncho.main.lobby.utils

import huncho.main.lobby.LobbyPlugin
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object JoinItemsUtil {
    
    fun giveJoinItems(player: Player, plugin: LobbyPlugin) {
        val joinItems = plugin.configManager.getMap(plugin.configManager.mainConfig, "lobby.join-items")
        
        player.inventory.clear()
        
        joinItems.forEach { (slotKey, itemData) ->
            try {
                // Parse slot - handle both String and Int keys
                val slotStr = slotKey.toString()
                val slot = slotStr.toIntOrNull()
                if (slot == null) {
                    LobbyPlugin.logger.warn("Invalid slot key: $slotKey")
                    return@forEach
                }
                
                // Parse item data
                val itemStr = itemData.toString()
                val parts = itemStr.split(":", limit = 2)
                val materialName = parts[0].uppercase()
                
                // Try to find material by name (try both with and without minecraft namespace)
                var material = Material.values().find { it.name() == materialName }
                if (material == null) {
                    // Try with lowercase and minecraft namespace
                    material = Material.values().find { it.name() == "minecraft:${materialName.lowercase()}" }
                }
                
                if (material == null) {
                    LobbyPlugin.logger.warn("Invalid material: $materialName")
                    // Log some common materials that might work instead
                    val suggestions = Material.values().filter { 
                        it.name().contains(materialName, ignoreCase = true)
                    }.take(5).map { it.name() }
                    if (suggestions.isNotEmpty()) {
                        LobbyPlugin.logger.info("Did you mean one of these? $suggestions")
                    }
                    return@forEach
                }
                
                val displayName = if (parts.size > 1) parts[1] else material.name()
                
                val item = ItemStack.builder(material)
                    .customName(MessageUtils.colorize(displayName))
                    .build()
                
                player.inventory.setItemStack(slot, item)
            } catch (e: Exception) {
                LobbyPlugin.logger.warn("Failed to give join item: slot=$slotKey, item=$itemData", e)
            }
        }
    }
}
