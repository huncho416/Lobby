package huncho.main.lobby.listeners.features

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block

class LaunchPadListener(private val plugin: LobbyPlugin) : EventListener<PlayerMoveEvent> {
    
    override fun eventType(): Class<PlayerMoveEvent> = PlayerMoveEvent::class.java
    
    override fun run(event: PlayerMoveEvent): EventListener.Result {
        val player = event.player
        val newPosition = event.newPosition
        
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.launch-pads")) {
            return EventListener.Result.SUCCESS
        }
        
        checkLaunchPad(player, newPosition)
        
        return EventListener.Result.SUCCESS
    }
    
    private fun checkLaunchPad(player: net.minestom.server.entity.Player, position: Pos) {
        val blockBelow = player.instance!!.getBlock(
            position.blockX(), 
            (position.blockY() - 1).toInt(), 
            position.blockZ()
        )
        
        // Check for pressure plate launch pads
        if (isLaunchPadBlock(blockBelow)) {
            launchPlayer(player)
        }
    }
    
    private fun isLaunchPadBlock(block: Block): Boolean {
        return when {
            block.name().contains("pressure_plate") -> true
            block.name().contains("redstone_block") -> true
            block.name().contains("slime_block") -> true
            else -> false
        }
    }
    
    private fun launchPlayer(player: net.minestom.server.entity.Player) {
        val launchPower = 1.5
        val currentVelocity = player.velocity
        
        // Launch player upward and slightly forward
        player.velocity = currentVelocity.withY(launchPower)
        
        // Optional: Play sound effect
        // player.playSound(SoundEvent.ENTITY_ENDER_DRAGON_FLAP, player.position, 1.0f, 1.0f)
    }
}
