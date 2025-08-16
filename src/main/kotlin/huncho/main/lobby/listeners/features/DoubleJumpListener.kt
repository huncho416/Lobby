package huncho.main.lobby.listeners.features

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.coordinate.Vec
import java.util.concurrent.ConcurrentHashMap

class DoubleJumpListener(private val plugin: LobbyPlugin) : EventListener<PlayerMoveEvent> {
    
    private val lastJumpTime = ConcurrentHashMap<String, Long>()
    private val jumpCooldown = 1000L // 1 second cooldown
    
    override fun eventType(): Class<PlayerMoveEvent> = PlayerMoveEvent::class.java
    
    override fun run(event: PlayerMoveEvent): EventListener.Result {
        val player = event.player
        
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.double-jump")) {
            return EventListener.Result.SUCCESS
        }
        
        // Check if player is flying (different mechanic)
        if (player.isFlying) {
            return EventListener.Result.SUCCESS
        }
        
        // Check if player just jumped (velocity change detection)
        if (isJumping(player)) {
            handleDoubleJump(player)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private fun isJumping(player: Player): Boolean {
        val velocity = player.velocity
        return velocity.y > 0.3 && !player.isOnGround
    }
    
    private fun handleDoubleJump(player: Player) {
        val uuid = player.uuid.toString()
        val currentTime = System.currentTimeMillis()
        val lastJump = lastJumpTime.getOrDefault(uuid, 0L)
        
        if (currentTime - lastJump < jumpCooldown) {
            return
        }
        
        // Apply double jump boost
        val boostPower = 0.8
        val currentVelocity = player.velocity
        player.velocity = currentVelocity.withY(boostPower)
        
        lastJumpTime[uuid] = currentTime
    }
}
