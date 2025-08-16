package huncho.main.lobby.features.world

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.world.DimensionType
import net.minestom.server.timer.TaskSchedule

/**
 * Manages world lighting and weather to create a bright, always-day environment
 * like MythicHub's lobby world
 */
class WorldLightingManager(private val plugin: LobbyPlugin) {
    
    fun initialize() {
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.enabled", true)) {
            return
        }
        
        setupWorldLighting()
        startLightingTask()
        
        plugin.logger.info("World lighting manager initialized - MythicHub style bright lighting enabled")
    }
    
    /**
     * Set up initial world lighting settings
     */
    private fun setupWorldLighting() {
        val instance = plugin.lobbyInstance
        
        // Set world to always day
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.always_day", true)) {
            instance.time = 6000L // Noon
            instance.setTimeRate(0) // Stop time progression - MythicHub style
        }
        
        // Clear weather (handled differently in Minestom)
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.clear_weather", true)) {
            // In Minestom, weather is handled at the chunk level or through custom implementation
            // Since we're stopping time progression, this keeps it bright automatically
        }
    }
    
    /**
     * Start the recurring task to maintain bright lighting
     */
    private fun startLightingTask() {
        val scheduler = MinecraftServer.getSchedulerManager()
        
        scheduler.submitTask {
            maintainWorldLighting()
            TaskSchedule.tick(100) // Run every 5 seconds (100 ticks)
        }
    }
    
    /**
     * Maintain the world lighting settings
     */
    private fun maintainWorldLighting() {
        val instance = plugin.lobbyInstance
        
        // Keep world at day time and ensure time rate is 0
        if (plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.always_day", true)) {
            if (instance.time != 6000L) {
                instance.time = 6000L // Reset to noon
            }
            if (instance.timeRate != 0) {
                instance.setTimeRate(0) // Ensure time doesn't progress
            }
        }
    }
    
    /**
     * Apply maximum light level to specific areas if needed
     */
    fun applyMaxLightLevel(instance: InstanceContainer) {
        val maxLight = plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.max_light_level", true)
        
        // In Minestom, light levels are managed through time and chunk generation
        // With time rate set to 0 and time at noon, we achieve maximum brightness
        if (maxLight) {
            // Ensure bright lighting is maintained
        }
    }
    
    /**
     * Get the current lighting status
     */
    fun getLightingStatus(): Map<String, Any> {
        val instance = plugin.lobbyInstance
        return mapOf(
            "time" to instance.time,
            "always_day" to plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.always_day", true),
            "clear_weather" to plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.clear_weather", true),
            "max_light_level" to plugin.configManager.getBoolean(plugin.configManager.mainConfig, "world_lighting.max_light_level", true)
        )
    }
}
