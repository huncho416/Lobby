package huncho.main.lobby.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.models.VanishData
import huncho.main.lobby.models.VanishLevel
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerPluginMessageEvent
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles plugin messages from Radium proxy for vanish state synchronization
 * Channel: radium:vanish
 */
class VanishPluginMessageListener(private val plugin: LobbyPlugin) : EventListener<PlayerPluginMessageEvent> {
    
    private val gson = Gson()
    private val vanishedPlayers = ConcurrentHashMap<UUID, VanishData>()
    
    override fun eventType(): Class<PlayerPluginMessageEvent> = PlayerPluginMessageEvent::class.java

    override fun run(event: PlayerPluginMessageEvent): EventListener.Result {
        // Check if this is a vanish message
        if (event.identifier != "radium:vanish") {
            return EventListener.Result.SUCCESS
        }
        
        try {
            val messageData = String(event.message)
            plugin.logger.debug("Received vanish plugin message: $messageData")
            
            val jsonObject = gson.fromJson(messageData, JsonObject::class.java)
            val action = jsonObject.get("action")?.asString ?: return EventListener.Result.SUCCESS
            
            when (action) {
                "set_vanish", "VANISH_STATE" -> handleVanishStateChange(jsonObject)
                "batch_update", "VANISH_BATCH_UPDATE" -> handleBatchUpdate(jsonObject)
                "remove_vanish" -> handleUnvanish(jsonObject)
                else -> plugin.logger.debug("Unknown vanish action: $action")
            }
            
        } catch (e: Exception) {
            plugin.logger.error("Error processing vanish plugin message", e)
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Handle individual vanish state change
     */
    private fun handleVanishStateChange(data: JsonObject) {
        try {
            val playerUuid = UUID.fromString(data.get("player")?.asString ?: return)
            val vanished = data.get("vanished")?.asBoolean ?: return
            val levelString = data.get("level")?.asString
            val vanishedByString = data.get("vanishedBy")?.asString
            val reason = data.get("reason")?.asString
            
            if (vanished) {
                // Player is being vanished
                val level = levelString?.let { VanishLevel.valueOf(it) } ?: VanishLevel.HELPER
                val vanishedBy = vanishedByString?.let { UUID.fromString(it) }
                
                val vanishData = VanishData.create(
                    playerId = playerUuid,
                    level = level,
                    vanishedBy = vanishedBy,
                    reason = reason
                )
                
                vanishedPlayers[playerUuid] = vanishData
                plugin.logger.info("Player $playerUuid vanished at level ${level.displayName}")
            } else {
                // Player is being unvanished
                vanishedPlayers.remove(playerUuid)
                plugin.logger.info("Player $playerUuid unvanished")
            }
            
            // Update visibility for all players
            updatePlayerVisibility(playerUuid)
            
        } catch (e: Exception) {
            plugin.logger.error("Error handling vanish state change", e)
        }
    }
    
    /**
     * Handle batch vanish updates for performance
     */
    private fun handleBatchUpdate(data: JsonObject) {
        try {
            val updates = data.getAsJsonArray("updates") ?: return
            
            updates.forEach { updateElement ->
                val update = updateElement.asJsonObject
                val playerUuid = UUID.fromString(update.get("player")?.asString ?: return@forEach)
                val vanished = update.get("vanished")?.asBoolean ?: return@forEach
                
                if (vanished) {
                    val level = update.get("level")?.asString?.let { VanishLevel.valueOf(it) } ?: VanishLevel.HELPER
                    val vanishData = VanishData.create(playerUuid, level)
                    vanishedPlayers[playerUuid] = vanishData
                } else {
                    vanishedPlayers.remove(playerUuid)
                }
                
                // Update visibility for this player
                updatePlayerVisibility(playerUuid)
            }
            
            plugin.logger.debug("Processed batch vanish update with ${updates.size()} changes")
            
        } catch (e: Exception) {
            plugin.logger.error("Error handling batch vanish update", e)
        }
    }
    
    /**
     * Handle unvanish message
     */
    private fun handleUnvanish(data: JsonObject) {
        try {
            val playerUuid = UUID.fromString(data.get("player")?.asString ?: return)
            vanishedPlayers.remove(playerUuid)
            updatePlayerVisibility(playerUuid)
            plugin.logger.info("Player $playerUuid unvanished via remove message")
        } catch (e: Exception) {
            plugin.logger.error("Error handling unvanish message", e)
        }
    }
    
    /**
     * Update visibility for a specific player
     */
    private fun updatePlayerVisibility(changedPlayerUuid: UUID) {
        runBlocking {
            val changedPlayer = MinecraftServer.getConnectionManager().onlinePlayers.find { it.uuid == changedPlayerUuid }
            
            // Update visibility for the changed player if they're online
            if (changedPlayer != null) {
                plugin.visibilityManager.updatePlayerVisibilityForVanish(changedPlayer)
                plugin.tabListManager.updatePlayerTabList(changedPlayer)
            }
            
            // Update visibility for all other players
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { viewer ->
                if (viewer.uuid != changedPlayerUuid) {
                    plugin.visibilityManager.updatePlayerVisibilityForVanish(viewer)
                    plugin.tabListManager.updatePlayerTabList(viewer)
                }
            }
        }
    }
    
    /**
     * Check if a player is vanished
     */
    fun isPlayerVanished(playerUuid: UUID): Boolean {
        return vanishedPlayers.containsKey(playerUuid)
    }
    
    /**
     * Get vanish data for a player
     */
    fun getVanishData(playerUuid: UUID): VanishData? {
        return vanishedPlayers[playerUuid]
    }
    
    /**
     * Check if viewer can see vanished player
     */
    suspend fun canSeeVanished(viewer: Player, vanishedPlayerUuid: UUID): Boolean {
        val vanishData = getVanishData(vanishedPlayerUuid) ?: return true
        
        try {
            // Get viewer's permissions from Radium
            val viewerData = plugin.radiumIntegration.getPlayerData(viewer.uuid).join()
            val viewerPermissions = viewerData?.permissions?.toSet() ?: emptySet()
            
            return VanishLevel.canSeeVanished(viewerPermissions, vanishData.level)
        } catch (e: Exception) {
            plugin.logger.warn("Error checking vanish visibility for ${viewer.username}", e)
            return false // Default to not showing vanished players on error
        }
    }
    
    /**
     * Get all vanished players
     */
    fun getVanishedPlayers(): Map<UUID, VanishData> {
        return vanishedPlayers.toMap()
    }
    
    /**
     * Clear all vanish data (for shutdown)
     */
    fun clear() {
        vanishedPlayers.clear()
    }
}
