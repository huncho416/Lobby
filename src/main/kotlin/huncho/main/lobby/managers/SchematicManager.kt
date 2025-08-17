package huncho.main.lobby.managers

import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.schematics.SchematicService
import huncho.main.lobby.schematics.SchematicServiceImpl
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Manages schematic operations for the lobby server
 */
class SchematicManager(
    private val configManager: ConfigManager,
    private val dataFolder: File
) {
    private val logger = LoggerFactory.getLogger(SchematicManager::class.java)
    private var _schematicService: SchematicService? = null
    
    val schematicService: SchematicService?
        get() = _schematicService
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Initialize the schematic manager
     */
    suspend fun initialize() {
        try {
            val schematicsConfig = configManager.mainConfig["schematics"] as? Map<String, Any>
            
            if (schematicsConfig == null) {
                logger.warn("No schematics configuration found - schematic features disabled")
                return
            }
            
            val enabled = schematicsConfig["enabled"] as? Boolean ?: false
            if (!enabled) {
                return
            }
            
            logger.info("Initializing schematic service...")
            
            // Ensure schematics directory exists
            val schematicsDir = File(dataFolder, "schematics")
            if (!schematicsDir.exists()) {
                schematicsDir.mkdirs()
            }
            
            // Initialize service
            _schematicService = SchematicServiceImpl(schematicsConfig, dataFolder, coroutineScope)
            
            // Pre-load schematics
            _schematicService?.reload()
            
            // Success will be logged when schematics are actually pasted
            
        } catch (e: Exception) {
            logger.error("Failed to initialize schematic manager", e)
        }
    }
    
    /**
     * Paste startup schematics if configured
     */
    suspend fun pasteStartupSchematics(instance: Instance) {
        val service = _schematicService
        if (service == null) {
            logger.debug("Schematic service not available - skipping startup paste")
            return
        }
        
        try {
            val schematicsConfig = configManager.mainConfig["schematics"] as? Map<String, Any> ?: return
            val pasteOnStartup = schematicsConfig["paste_on_startup"] as? Boolean ?: false
            
            if (!pasteOnStartup) {
                return
            }
            
            val filesConfig = schematicsConfig["files"] as? Map<String, Any> ?: return
            
            var pastedCount = 0
            var totalBlocks = 0
            val startTime = System.currentTimeMillis()
            
            for ((name, config) in filesConfig) {
                if (config !is Map<*, *>) continue
                
                val enabled = config["enabled"] as? Boolean ?: true
                if (!enabled) continue
                
                try {
                    val result = service.pasteSchematic(instance, name)
                    
                    if (result.success) {
                        pastedCount++
                        totalBlocks += result.blocksPlaced
                    } else {
                        logger.error("Failed to paste startup schematic '$name': ${result.error}")
                    }
                    
                } catch (e: Exception) {
                    logger.error("Exception while pasting startup schematic '$name'", e)
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            if (pastedCount > 0) {
                // Green success message
                println("\u001B[32m✓ Successfully loaded $pastedCount schematics ($totalBlocks blocks)\u001B[0m")
            } else {
                logger.warn("No startup schematics were pasted successfully")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to paste startup schematics", e)
        }
    }
    
    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats() = _schematicService?.getCacheStats()
    
    /**
     * Reload schematics configuration and cache
     */
    suspend fun reload() {
        try {
            logger.info("Reloading schematic manager...")
            
            // Shutdown existing service
            if (_schematicService is SchematicServiceImpl) {
                (_schematicService as SchematicServiceImpl).shutdown()
            }
            _schematicService = null
            
            // Re-initialize
            initialize()
            
            logger.info("Schematic manager reloaded successfully")
            
        } catch (e: Exception) {
            logger.error("Failed to reload schematic manager", e)
        }
    }
    
    /**
     * Shutdown the schematic manager
     */
    fun shutdown() {
        try {
            logger.info("Shutting down schematic manager...")
            
            if (_schematicService is SchematicServiceImpl) {
                (_schematicService as SchematicServiceImpl).shutdown()
            }
            _schematicService = null
            
            coroutineScope.cancel()
            
            logger.info("Schematic manager shutdown complete")
            
        } catch (e: Exception) {
            logger.error("Error during schematic manager shutdown", e)
        }
    }
}
