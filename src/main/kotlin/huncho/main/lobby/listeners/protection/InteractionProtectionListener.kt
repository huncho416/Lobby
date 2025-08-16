package huncho.main.lobby.listeners.protection

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block

class InteractionProtectionListener(private val plugin: LobbyPlugin) : 
    EventListener<PlayerBlockInteractEvent> {

    override fun eventType(): Class<PlayerBlockInteractEvent> = PlayerBlockInteractEvent::class.java

    override fun run(event: PlayerBlockInteractEvent): EventListener.Result {
        val player = event.player
        val block = event.block
        
        // Check if block interaction is disabled
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "protection.interaction")) {
            // Allow certain interactions for lobby items
            if (isAllowedInteraction(block)) {
                return EventListener.Result.SUCCESS
            }
            
            // Check bypass permission
            if (!plugin.radiumIntegration.hasPermission(player.uuid, "hub.interact.bypass").get()) {
                val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "protection.interaction")
                MessageUtils.sendMessage(player, message)
                // TODO: Find correct way to cancel event in Minestom
                return EventListener.Result.SUCCESS
            }
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun isAllowedInteraction(block: Block): Boolean {
        // Allow interactions with specific lobby blocks
        return when {
            block.name().contains("button") -> true
            block.name().contains("pressure_plate") -> true
            block.name().contains("lever") -> true
            else -> false
        }
    }
}
