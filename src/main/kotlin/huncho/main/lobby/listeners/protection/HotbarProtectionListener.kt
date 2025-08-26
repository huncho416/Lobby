package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerSwapItemEvent

import net.minestom.server.item.Material

/**
 * Protects lobby join items from being swapped with F key (main hand <-> offhand)
 */
class HotbarProtectionListener(private val plugin: LobbyPlugin, private val monitor: JoinItemMonitor) : EventListener<PlayerSwapItemEvent> {
    
    override fun eventType(): Class<PlayerSwapItemEvent> = PlayerSwapItemEvent::class.java
    
    override fun run(event: PlayerSwapItemEvent): EventListener.Result {
        val player = event.player
        
        // Skip protection for admins (using Radium integration)
        try {
            val hasBypass = plugin.radiumIntegration.hasPermission(player.uuid, "lobby.admin").get() ||
                           plugin.radiumIntegration.hasPermission(player.uuid, "lobby.bypass.protection").get()
            if (hasBypass) return EventListener.Result.SUCCESS
        } catch (e: Exception) {
            // If permission check fails, don't skip protection
        }
        
        val mainHandItem = event.mainHandItem
        val offHandItem = event.offHandItem
        
        // Check if trying to swap a join item to offhand
        if (mainHandItem != null && mainHandItem.material() != Material.AIR) {
            // Use material-based identification since component access is problematic
            if (monitor.isJoinItemByMaterial(mainHandItem.material())) {
                plugin.logger.debug("[HotbarProtection] Prevented ${player.username} from swapping join item to offhand: ${mainHandItem.material().name()}")
                return EventListener.Result.INVALID
            }
        }
        
        // Check if trying to swap a join item from offhand
        if (offHandItem != null && offHandItem.material() != Material.AIR) {
            // Use material-based identification since component access is problematic
            if (monitor.isJoinItemByMaterial(offHandItem.material())) {
                plugin.logger.debug("[HotbarProtection] Prevented ${player.username} from swapping join item from offhand: ${offHandItem.material().name()}")
                return EventListener.Result.INVALID
            }
        }
        
        return EventListener.Result.SUCCESS
    }
}
