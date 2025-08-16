package huncho.main.lobby.features.menus

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.features.visibility.VisibilityMode
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class PlayerVisibilityMenu(private val plugin: LobbyPlugin) {
    
    fun open(player: Player) {
        val title = plugin.configManager.getString(plugin.configManager.messagesConfig, "menus.visibility")
        val inventory = Inventory(InventoryType.CHEST_1_ROW, MessageUtils.colorize(title))
        
        val currentMode = plugin.visibilityManager.getVisibility(player)
        
        // All players option
        val allItem = ItemStack.builder(Material.LIME_DYE)
            .customName(MessageUtils.colorize("&aShow All Players"))
            .lore(listOf(
                MessageUtils.colorize("&7Show all players in the lobby"),
                MessageUtils.colorize(""),
                if (currentMode == VisibilityMode.ALL) 
                    MessageUtils.colorize("&a✓ Currently selected") 
                else 
                    MessageUtils.colorize("&eClick to select")
            ))
            .build()
        
        // Staff only option
        val staffItem = ItemStack.builder(Material.YELLOW_DYE)
            .customName(MessageUtils.colorize("&eShow Staff Only"))
            .lore(listOf(
                MessageUtils.colorize("&7Show only staff members"),
                MessageUtils.colorize(""),
                if (currentMode == VisibilityMode.STAFF) 
                    MessageUtils.colorize("&a✓ Currently selected") 
                else 
                    MessageUtils.colorize("&eClick to select")
            ))
            .build()
        
        // Hide all option
        val hideItem = ItemStack.builder(Material.RED_DYE)
            .customName(MessageUtils.colorize("&cHide All Players"))
            .lore(listOf(
                MessageUtils.colorize("&7Hide all players from view"),
                MessageUtils.colorize(""),
                if (currentMode == VisibilityMode.NONE) 
                    MessageUtils.colorize("&a✓ Currently selected") 
                else 
                    MessageUtils.colorize("&eClick to select")
            ))
            .build()
        
        inventory.setItemStack(2, allItem)
        inventory.setItemStack(4, staffItem)
        inventory.setItemStack(6, hideItem)
        
        player.openInventory(inventory)
    }
}
