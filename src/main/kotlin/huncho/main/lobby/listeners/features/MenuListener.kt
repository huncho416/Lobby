package huncho.main.lobby.listeners.features

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.features.menus.ServerSelectorMenu
import huncho.main.lobby.features.menus.PlayerVisibilityMenu
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.Material

class MenuListener(private val plugin: LobbyPlugin) : EventListener<PlayerUseItemEvent> {
    
    override fun eventType(): Class<PlayerUseItemEvent> = PlayerUseItemEvent::class.java
    
    override fun run(event: PlayerUseItemEvent): EventListener.Result {
        val player = event.player
        val item = event.itemStack
        
        when (item.material()) {
            Material.COMPASS -> {
                // Open server selector
                ServerSelectorMenu(plugin).open(player)
            }
            
            Material.REDSTONE -> {
                // Open player visibility menu
                PlayerVisibilityMenu(plugin).open(player)
            }
            
            Material.BARRIER -> {
                // Leave server / disconnect
                player.kick("&cYou have left the server!")
            }
            
            else -> return EventListener.Result.SUCCESS
        }
        
        return EventListener.Result.SUCCESS
    }
}
