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
                if (!enabled) {
                    logger.debug("Skipping disabled schematic: $name")
                    continue
                }
                
                // Pre-validate the schematic file if we can determine its path
                val filePath = config["file"] as? String
                if (filePath != null) {
                    val file = if (File(filePath).isAbsolute) {
                        File(filePath)
                    } else {
                        File(dataFolder, filePath)
                    }
                    
                    val validation = validateSchematicFile(file)
                    if (!validation.isValid) {
                        logger.error("Schematic '$name' validation failed: ${validation.message}")
                        continue
                    }
                }
                
                try {
                    val result = service.pasteSchematic(instance, name)
                    
                    if (result.success) {
                        pastedCount++
                        totalBlocks += result.blocksPlaced
                        logger.info("✓ Pasted schematic '$name' (${result.blocksPlaced} blocks)")
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
                logger.info("If you have schematic files, please check:")
                logger.info("  1. Files exist in the schematics/ folder")
                logger.info("  2. Files are valid .schem or .schematic format")
                logger.info("  3. Files are not corrupted")
                logger.info("  4. Files are enabled in schematics.yml config")
                logger.info("  5. File paths in config match actual file names")
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
    
    /**
     * Validate a schematic file for common issues
     */
    private fun validateSchematicFile(file: File): ValidationResult {
        if (!file.exists()) {
            return ValidationResult(false, "File does not exist: ${file.absolutePath}")
        }
        
        if (!file.canRead()) {
            return ValidationResult(false, "Cannot read file (permissions): ${file.absolutePath}")
        }
        
        if (file.length() == 0L) {
            return ValidationResult(false, "File is empty: ${file.name}")
        }
        
        if (file.length() > 100 * 1024 * 1024) { // 100MB
            return ValidationResult(false, "File is too large (${file.length() / 1024 / 1024}MB): ${file.name}")
        }
        
        val extension = file.extension.lowercase()
        if (extension !in listOf("schem", "schematic")) {
            return ValidationResult(false, "Invalid file extension '$extension': ${file.name}. Expected .schem or .schematic")
        }
        
        // Try to peek at file header
        try {
            file.inputStream().use { stream ->
                val firstBytes = ByteArray(10)
                val bytesRead = stream.read(firstBytes)
                if (bytesRead < 5) {
                    return ValidationResult(false, "File too small or corrupted: ${file.name}")
                }
            }
        } catch (e: Exception) {
            return ValidationResult(false, "Cannot read file header: ${e.message}")
        }
        
        return ValidationResult(true, "File appears valid")
    }
    
    data class ValidationResult(val isValid: Boolean, val message: String)
}
