package huncho.main.lobby.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.JoinItemsUtil
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Robust join item protection monitor that periodically ensures join items are in their correct positions.
 * This provides additional protection beyond event-based cancellation by actively restoring misplaced items.
 */
class JoinItemMonitor(private val plugin: LobbyPlugin) {
    
    companion object {
        // Expected hub item positions (slots that should have join items)
        private val HUB_SLOTS = intArrayOf(0, 1, 7, 8)
        
        // Update frequency (every 500ms like MythicHub)
        private const val MONITOR_INTERVAL_MS = 500L
        
        // Materials that are typically used for join items
        private val JOIN_ITEM_MATERIALS = setOf(
            Material.COMPASS, Material.REDSTONE, Material.ENDER_PEARL, 
            Material.LIME_DYE, Material.GRAY_DYE, Material.PLAYER_HEAD
        )
    }
    
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var monitorTask: java.util.concurrent.ScheduledFuture<*>? = null
    
    fun startMonitor() {
        plugin.logger.info("[JoinItemMonitor] Starting periodic join item monitor...")
        
        monitorTask = scheduler.scheduleAtFixedRate(
            { monitorAllPlayers() },
            MONITOR_INTERVAL_MS,
            MONITOR_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
        
        plugin.logger.info("[JoinItemMonitor] Join item monitor started (interval: ${MONITOR_INTERVAL_MS}ms)")
    }
    
    fun stopMonitor() {
        monitorTask?.cancel(false)
        monitorTask = null
        scheduler.shutdown()
        plugin.logger.info("[JoinItemMonitor] Join item monitor stopped")
    }
    
    private fun monitorAllPlayers() {
        try {
            for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
                ensureJoinItemsInCorrectSlots(player)
            }
        } catch (e: Exception) {
            plugin.logger.error("[JoinItemMonitor] Error during monitoring: ${e.message}")
        }
    }
    
    private fun ensureJoinItemsInCorrectSlots(player: Player) {
        // Skip admins with bypass permissions (using permission check through Radium)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }
        
        var needsRefresh = false
        
        // Check if join item slots are empty or don't have the expected items
        for (slot in HUB_SLOTS) {
            val currentItem = player.inventory.getItemStack(slot)
            
            // Check if slot is empty or has wrong item
            if (currentItem == null || currentItem.material() == Material.AIR || 
                !isLikelyJoinItem(currentItem)) {
                needsRefresh = true
                break
            }
        }
        
        // Also check if join items are in wrong slots (moved to inventory)
        for (slot in 9 until 36) { // Check main inventory (not hotbar)
            val item = player.inventory.getItemStack(slot)
            if (item != null && item.material() != Material.AIR && isLikelyJoinItem(item)) {
                needsRefresh = true
                break
            }
        }
        
        // Check hotbar slots 2-6 for misplaced join items
        for (slot in 2..6) {
            if (HUB_SLOTS.contains(slot)) continue // Skip valid join item slots
            
            val item = player.inventory.getItemStack(slot)
            if (item != null && item.material() != Material.AIR && isLikelyJoinItem(item)) {
                needsRefresh = true
                break
            }
        }
        
        // Refresh items if needed
        if (needsRefresh) {
            plugin.logger.debug("[JoinItemMonitor] Restoring join items for player: ${player.username}")
            JoinItemsUtil.giveJoinItems(player, plugin)
        }
    }
    
    /**
     * Helper method to check if an item is likely a join item based on material type
     */
    fun isJoinItem(itemName: String): Boolean {
        return itemName.equals("Server Selector", ignoreCase = true) ||
               itemName.equals("Cosmetics", ignoreCase = true) ||
               itemName.startsWith("Player Visibility", ignoreCase = true) ||
               itemName.equals("Profile", ignoreCase = true)
    }
    
    /**
     * Helper method to check if a material type represents a join item
     */
    fun isJoinItemByMaterial(material: Material): Boolean {
        return JOIN_ITEM_MATERIALS.contains(material)
    }
    
    /**
     * Helper method to check if an item is likely a join item based on material
     */
    private fun isLikelyJoinItem(item: ItemStack): Boolean {
        return JOIN_ITEM_MATERIALS.contains(item.material())
    }
}
