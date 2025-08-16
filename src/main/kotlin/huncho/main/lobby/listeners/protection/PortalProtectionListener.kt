package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block

class PortalProtectionListener(private val plugin: LobbyPlugin) : EventListener<PlayerMoveEvent> {
    
    override fun eventType(): Class<PlayerMoveEvent> = PlayerMoveEvent::class.java
    
    override fun run(event: PlayerMoveEvent): EventListener.Result {
        val player = event.player
        val newPosition = event.newPosition
        
        // Check if portal protection is enabled
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.portal")) {
            return EventListener.Result.SUCCESS
        }
        
        val block = player.instance!!.getBlock(newPosition.blockX(), newPosition.blockY().toInt(), newPosition.blockZ())
        
        // Check if player is trying to enter a portal
        if (isPortalBlock(block)) {
            // Cancel movement and send message
            event.isCancelled = true
            
            val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.portal")
            MessageUtils.sendMessage(player, message)
            
            // Teleport player back slightly
            val safePosition = newPosition.sub(0.0, 0.0, 1.0)
            player.teleport(safePosition)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun isPortalBlock(block: Block): Boolean {
        return when {
            block.name().contains("nether_portal") -> true
            block.name().contains("end_portal") -> true
            block.name().contains("end_gateway") -> true
            else -> false
        }
    }
}
