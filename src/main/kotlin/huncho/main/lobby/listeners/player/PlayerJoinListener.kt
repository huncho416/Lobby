package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
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
            // Sync with Radium
            plugin.radiumIntegration.syncPlayerOnJoin(player)
            
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
            giveJoinItems(player)
            
            // Send join message
            sendJoinMessage(player)
            
            // Enable scoreboard
            if (plugin.configManager.getBoolean(plugin.configManager.scoreboardConfig, "scoreboard.enabled")) {
                plugin.scoreboardManager.addPlayer(player)
            }
            
            // Set tab list with MythicHub style (respects Radium)
            plugin.tabListManager.onPlayerJoin(player)
            
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
    
    private fun giveJoinItems(player: Player) {
        val joinItems = plugin.configManager.getMap(plugin.configManager.mainConfig, "lobby.join-items")
        
        player.inventory.clear()
        
        joinItems.forEach { (slotKey, itemData) ->
            try {
                // Parse slot - handle both String and Int keys
                val slotStr = slotKey.toString()
                val slot = slotStr.toIntOrNull()
                if (slot == null) {
                    LobbyPlugin.logger.warn("Invalid slot key: $slotKey")
                    return@forEach
                }
                
                // Parse item data
                val itemStr = itemData.toString()
                val parts = itemStr.split(":", limit = 2)
                val materialName = parts[0].uppercase()
                
                // Try to find material by name (try both with and without minecraft namespace)
                var material = Material.values().find { it.name() == materialName }
                if (material == null) {
                    // Try with lowercase and minecraft namespace
                    material = Material.values().find { it.name() == "minecraft:${materialName.lowercase()}" }
                }
                
                if (material == null) {
                    LobbyPlugin.logger.warn("Invalid material: $materialName")
                    // Log some common materials that might work instead
                    val suggestions = Material.values().filter { 
                        it.name().contains(materialName, ignoreCase = true)
                    }.take(5).map { it.name() }
                    if (suggestions.isNotEmpty()) {
                        LobbyPlugin.logger.info("Did you mean one of these? $suggestions")
                    }
                    return@forEach
                }
                
                val displayName = if (parts.size > 1) parts[1] else material.name()
                
                val item = ItemStack.builder(material)
                    .customName(MessageUtils.colorize(displayName))
                    .build()
                
                player.inventory.setItemStack(slot, item)
            } catch (e: Exception) {
                LobbyPlugin.logger.warn("Failed to give join item: slot=$slotKey, item=$itemData", e)
            }
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
        plugin.lobbyInstance.players.forEach { onlinePlayer ->
            // This would be where you update the tab list display
            // Implementation depends on your tab list system
        }
    }
}
