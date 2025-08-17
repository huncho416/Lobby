package huncho.main.lobby.features.spawn

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

class SpawnManager(private val plugin: LobbyPlugin) {
    
    private var spawnLocation: Pos? = null
    
    init {
        loadSpawnLocation()
    }
    
    /**
     * Load spawn location from config
     */
    private fun loadSpawnLocation() {
        // Always use config spawn location since we removed database dependency
        loadDefaultSpawn()
    }
    
    /**
     * Load default spawn from config
     */
    private fun loadDefaultSpawn() {
        val config = plugin.configManager.mainConfig
        val x = plugin.configManager.getDouble(config, "spawn.x", 0.5)
        val y = plugin.configManager.getDouble(config, "spawn.y", 65.0)
        val z = plugin.configManager.getDouble(config, "spawn.z", 0.5)
        val yaw = plugin.configManager.getDouble(config, "spawn.yaw", 0.0).toFloat()
        val pitch = plugin.configManager.getDouble(config, "spawn.pitch", 0.0).toFloat()
        
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
        
        // Note: Spawn location is now saved to config file only
        // Database storage removed - use HTTP API for persistence if needed
        LobbyPlugin.logger.info("Spawn location set: $location (config file update required manually)")
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
        MessageUtils.sendMessage(player, "&aTeleported to spawn!")
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
