package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.coordinate.Pos

class PlayerMoveListener(private val plugin: LobbyPlugin) : EventListener<PlayerMoveEvent> {
    
    override fun eventType(): Class<PlayerMoveEvent> = PlayerMoveEvent::class.java
    
    override fun run(event: PlayerMoveEvent): EventListener.Result {
        val player = event.player
        val newPosition = event.newPosition
        
        // Check if player falls into void
        if (newPosition.y <= 0) {
            plugin.spawnManager.teleportToSpawn(player)
            return EventListener.Result.SUCCESS
        }
        
        // Check for launch pads
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.launch-pads")) {
            checkLaunchPads(player, newPosition)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun checkLaunchPads(player: net.minestom.server.entity.Player, position: Pos) {
        val block = player.instance!!.getBlock(position.blockX(), (position.blockY() - 1).toInt(), position.blockZ())
        
        // Check if standing on pressure plate (launch pad)
        if (block.name().contains("pressure_plate")) {
            // Launch player upward
            val velocity = player.velocity
            player.velocity = velocity.withY(1.5)
        }
    }
}
