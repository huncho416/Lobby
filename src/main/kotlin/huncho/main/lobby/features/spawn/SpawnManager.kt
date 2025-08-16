package huncho.main.lobby.features.spawn

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import kotlinx.coroutines.runBlocking
import org.bson.Document

class SpawnManager(private val plugin: LobbyPlugin) {
    
    private var spawnLocation: Pos? = null
    
    init {
        loadSpawnLocation()
    }
    
    /**
     * Load spawn location from database
     */
    private fun loadSpawnLocation() {
        runBlocking {
            val spawnData = plugin.mongoManager.getLobbyData("spawn")
            if (spawnData != null) {
                val x = spawnData.getDouble("x") ?: 0.5
                val y = spawnData.getDouble("y") ?: 65.0
                val z = spawnData.getDouble("z") ?: 0.5
                val yaw = spawnData.getDouble("yaw")?.toFloat() ?: 0.0f
                val pitch = spawnData.getDouble("pitch")?.toFloat() ?: 0.0f
                
                spawnLocation = Pos(x, y, z, yaw, pitch)
                LobbyPlugin.logger.info("Loaded spawn location: $spawnLocation")
            } else {
                // Use default spawn from config
                loadDefaultSpawn()
            }
        }
    }
    
    /**
     * Load default spawn from config
     */
    private fun loadDefaultSpawn() {
        val config = plugin.configManager.mainConfig
        val x = plugin.configManager.getString(config, "lobby.spawn.x", "0.5").toDoubleOrNull() ?: 0.5
        val y = plugin.configManager.getString(config, "lobby.spawn.y", "65.0").toDoubleOrNull() ?: 65.0
        val z = plugin.configManager.getString(config, "lobby.spawn.z", "0.5").toDoubleOrNull() ?: 0.5
        val yaw = plugin.configManager.getString(config, "lobby.spawn.yaw", "0.0").toFloatOrNull() ?: 0.0f
        val pitch = plugin.configManager.getString(config, "lobby.spawn.pitch", "0.0").toFloatOrNull() ?: 0.0f
        
        spawnLocation = Pos(x, y, z, yaw, pitch)
        LobbyPlugin.logger.info("Using default spawn location: $spawnLocation")
    }
    
    /**
     * Set new spawn location
     */
    fun setSpawnLocation(location: Pos) {
        spawnLocation = location
        saveSpawnLocation()
    }
    
    /**
     * Set spawn location from player's current position
     */
    fun setSpawnLocation(player: Player): Boolean {
        return try {
            setSpawnLocation(player.position)
            true
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to set spawn location", e)
            false
        }
    }
    
    /**
     * Save spawn location to database
     */
    private fun saveSpawnLocation() {
        val location = spawnLocation ?: return
        
        runBlocking {
            val spawnData = Document().apply {
                append("x", location.x)
                append("y", location.y)
                append("z", location.z)
                append("yaw", location.yaw)
                append("pitch", location.pitch)
                append("lastUpdated", System.currentTimeMillis())
            }
            
            plugin.mongoManager.saveLobbyData("spawn", spawnData)
            LobbyPlugin.logger.info("Saved spawn location: $location")
        }
    }
    
    /**
     * Get current spawn location
     */
    fun getSpawnLocation(): Pos {
        return spawnLocation ?: Pos(0.5, 65.0, 0.5, 0.0f, 0.0f)
    }
    
    /**
     * Teleport player to spawn
     */
    fun teleportToSpawn(player: Player) {
        val spawn = getSpawnLocation()
        player.teleport(spawn).thenRun {
            // Apply protection settings after teleport
            plugin.protectionManager.applyProtections(player)
        }
    }
    
    /**
     * Teleport player to spawn with message
     */
    fun teleportToSpawnWithMessage(player: Player) {
        teleportToSpawn(player)
        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.spawn-teleport")
        MessageUtils.sendMessage(player, message)
    }
    
    /**
     * Check if location is near spawn
     */
    fun isNearSpawn(location: Pos, radius: Double = 10.0): Boolean {
        val spawn = getSpawnLocation()
        return spawn.distance(location) <= radius
    }
    
    /**
     * Get spawn location info for display
     */
    fun getSpawnInfo(): Map<String, Any> {
        val spawn = getSpawnLocation()
        return mapOf(
            "x" to String.format("%.2f", spawn.x),
            "y" to String.format("%.2f", spawn.y),
            "z" to String.format("%.2f", spawn.z),
            "yaw" to String.format("%.2f", spawn.yaw),
            "pitch" to String.format("%.2f", spawn.pitch),
            "world" to "lobby"
        )
    }
    
    /**
     * Reset spawn to default
     */
    fun resetSpawn() {
        loadDefaultSpawn()
        saveSpawnLocation()
    }
}
