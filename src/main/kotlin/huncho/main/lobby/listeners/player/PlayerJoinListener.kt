package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.JoinItemsUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlinx.coroutines.runBlocking

class PlayerJoinListener(private val plugin: LobbyPlugin) : EventListener<PlayerSpawnEvent> {
    
    override fun eventType(): Class<PlayerSpawnEvent> = PlayerSpawnEvent::class.java
    
    override fun run(event: PlayerSpawnEvent): EventListener.Result {
        val player = event.player
        
        runBlocking {
            handlePlayerJoin(player)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    private suspend fun handlePlayerJoin(player: Player) {
        try {
            // Check if player is banned as a fallback (in case pre-login check missed it)
            val punishmentService = plugin.punishmentService
            if (punishmentService != null) {
                val isBanned = punishmentService.isPlayerBanned(player.uuid).join()
                if (isBanned) {
                    val punishment = punishmentService.getActivePunishment(player.uuid).join()
                    val banReason = if (punishment != null) {
                        "You are banned: ${punishment.getDisplayReason()}"
                    } else {
                        "You are banned from this server!"
                    }
                    player.kick(Component.text(banReason, NamedTextColor.RED))
                    LobbyPlugin.logger.info("Kicked banned player ${player.username} (${player.uuid}) on join")
                    return
                }
            }
            
            // Sync with Radium
            plugin.radiumIntegration.syncPlayerOnJoin(player)
            
            // Check if joining player is vanished
            val isVanished = plugin.radiumIntegration.isPlayerVanished(player.uuid).join()
            
            if (isVanished) {
                // Handle vanished player join - might need special visibility logic
                plugin.logger.debug("Vanished player ${player.username} joined lobby")
            }
            
            // Teleport to spawn
            if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "lobby.teleport-on-join")) {
                plugin.spawnManager.teleportToSpawn(player)
            }
            
            // Set game mode to adventure
            player.gameMode = GameMode.ADVENTURE
            
            // Enable fly if player has permission
            plugin.radiumIntegration.hasPermission(player.uuid, "hub.fly").thenAccept { hasFly ->
                if (hasFly) {
                    player.isAllowFlying = true
                    player.isFlying = true
                }
            }
            
            // Set player speed
            val speed = plugin.configManager.getInt(plugin.configManager.mainConfig, "lobby.auto-speed", 2)
            player.flyingSpeed = speed * 0.1f
            // Set walking speed (implement if needed)
            // player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = speed * 0.1
            
            // Give join items
            JoinItemsUtil.giveJoinItems(player, plugin)
            
            // Send join message
            sendJoinMessage(player)
            
            // Enable scoreboard
            if (plugin.configManager.getBoolean(plugin.configManager.scoreboardConfig, "scoreboard.enabled")) {
                plugin.scoreboardManager.addPlayer(player)
            }
            
            // Set tab list with MythicHub style (respects Radium)
            plugin.tabListManager.onPlayerJoin(player)
            
            // Update visibility for all players considering the new player's vanish status
            plugin.visibilityManager.updateVisibilityForNewPlayer(player)
            
            // Also update vanish-specific visibility
            plugin.visibilityManager.updatePlayerVisibilityForVanish(player)
            
            // Check for updates (staff only)
            plugin.radiumIntegration.hasPermission(player.uuid, "hub.update").thenAccept { hasUpdate ->
                if (hasUpdate) {
                    // Send update notification if available
                    val updateMessage = plugin.configManager.getString(plugin.configManager.messagesConfig, "updates.available", "")
                    if (updateMessage.isNotEmpty()) {
                        MessageUtils.sendMessage(player, updateMessage)
                    }
                }
            }
            
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Error handling player join for ${player.username}", e)
        }
    }
    
    private fun sendJoinMessage(player: Player) {
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "features.join-messages")) {
            return
        }
        
        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "join-message")
            .replace("{player}", player.username)
        
        // Broadcast to all players
        plugin.lobbyInstance.players.forEach { onlinePlayer ->
            MessageUtils.sendMessage(onlinePlayer, message)
        }
    }
    
    private suspend fun updateTabList(player: Player) {
        val displayName = plugin.radiumIntegration.getTabListName(player)
        player.displayName = MessageUtils.colorize(displayName)
        
        // Update tab list for all players
        plugin.lobbyInstance.players.forEach { _ ->
            // This would be where you update the tab list display
            // Implementation depends on your tab list system
        }
    }
}
