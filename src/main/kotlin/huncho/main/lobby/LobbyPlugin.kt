package huncho.main.lobby

import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.database.MongoManager
import huncho.main.lobby.database.RedisManager
import huncho.main.lobby.listeners.EventManager
import huncho.main.lobby.commands.CommandManager
import huncho.main.lobby.features.queue.QueueManager
import huncho.main.lobby.features.scoreboard.ScoreboardManager
import huncho.main.lobby.features.visibility.VisibilityManager
import huncho.main.lobby.features.spawn.SpawnManager
import huncho.main.lobby.features.protection.ProtectionManager
import huncho.main.lobby.features.world.WorldLightingManager
import huncho.main.lobby.features.tablist.TabListManager
import huncho.main.lobby.managers.SchematicManager
import huncho.main.lobby.integration.RadiumIntegration
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.coordinate.Pos
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object LobbyPlugin {
    
    val logger: Logger = LoggerFactory.getLogger(LobbyPlugin::class.java)
    
    @JvmStatic
    fun main(args: Array<String>) {
        initialize()
    }
    
    // Core Managers
    lateinit var configManager: ConfigManager
    lateinit var mongoManager: MongoManager
    var redisManager: RedisManager? = null
    lateinit var eventManager: EventManager
    lateinit var commandManager: CommandManager
    
    // Feature Managers
    lateinit var queueManager: QueueManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var visibilityManager: VisibilityManager
    lateinit var spawnManager: SpawnManager
    lateinit var protectionManager: ProtectionManager
    lateinit var worldLightingManager: WorldLightingManager
    lateinit var tabListManager: TabListManager
    lateinit var schematicManager: SchematicManager
    
    // Integration
    lateinit var radiumIntegration: RadiumIntegration
    
    // Lobby Instance
    lateinit var lobbyInstance: InstanceContainer
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun initialize() {
        logger.info("Initializing Lobby Plugin...")
        
        try {
            // Initialize core managers
            initializeCore()
            
            // Setup lobby world first
            setupLobbyWorld()
            
            // Initialize features (now that lobbyInstance is ready)
            initializeFeatures()
            
            // Initialize schematics and paste startup schematics
            coroutineScope.launch {
                try {
                    schematicManager.initialize()
                    schematicManager.pasteStartupSchematics(lobbyInstance)
                } catch (e: Exception) {
                    logger.error("Failed to initialize or paste startup schematics", e)
                }
            }
            
            logger.info("Lobby Plugin successfully initialized!")
        } catch (e: Exception) {
            logger.error("Failed to initialize Lobby Plugin", e)
            throw e
        }
    }
    
    fun terminate() {
        logger.info("Terminating Lobby Plugin...")
        
        try {
            // Save all data
            queueManager.saveAllQueues()
            
            // Shutdown schematic manager
            schematicManager.shutdown()
            
            // Shutdown integrations
            radiumIntegration.shutdown()
            
            // Close database connections
            mongoManager.close()
            redisManager?.close()
            
            // Cancel coroutine scope
            coroutineScope.cancel()
            
            logger.info("Lobby Plugin successfully terminated!")
        } catch (e: Exception) {
            logger.error("Error during plugin termination", e)
        }
    }
    
    private fun initializeCore() {
        logger.info("Initializing core managers...")
        
        // Configuration
        configManager = ConfigManager(this)
        configManager.loadAllConfigs()
        
        // Database connections
        mongoManager = MongoManager(configManager)
        
        // Redis is optional now since we use HTTP API
        val redisEnabled = configManager.getBoolean(configManager.mainConfig, "database.redis.enabled", false)
        if (redisEnabled) {
            redisManager = RedisManager(configManager)
            logger.info("Redis integration enabled")
        } else {
            logger.info("Redis integration disabled - using HTTP API instead")
        }
        
        // Radium integration
        radiumIntegration = RadiumIntegration(configManager)
        radiumIntegration.initialize()
        
        // Event and command management
        eventManager = EventManager(this)
        commandManager = CommandManager(this)
    }
    
    private fun initializeFeatures() {
        logger.info("Initializing feature managers...")
        
        // Core features
        spawnManager = SpawnManager(this)
        protectionManager = ProtectionManager(this)
        
        // Schematic manager (requires data folder)
        val dataFolder = File("config/lobby") // Same directory as config
        schematicManager = SchematicManager(configManager, dataFolder)
        
        // Advanced features
        queueManager = QueueManager(this)
        scoreboardManager = ScoreboardManager(this)
        visibilityManager = VisibilityManager(this)
        
        // MythicHub style features
        worldLightingManager = WorldLightingManager(this)
        tabListManager = TabListManager(this)
        
        // Initialize the new managers
        worldLightingManager.initialize()
        tabListManager.initialize()
        
        // Register all event listeners
        eventManager.registerAllListeners()
        
        // Register all commands
        commandManager.registerAllCommands()
    }
    
    private fun setupLobbyWorld() {
        logger.info("Setting up lobby world...")
        
        val instanceManager = MinecraftServer.getInstanceManager()
        lobbyInstance = instanceManager.createInstanceContainer()
        
        // Set the chunk generator
        lobbyInstance.setGenerator { unit ->
            unit.modifier().fillHeight(0, 60, Block.STONE)
            unit.modifier().fillHeight(60, 61, Block.GRASS_BLOCK)
        }
        
        // Set as the default spawn instance
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            if (instance != lobbyInstance) {
                instanceManager.unregisterInstance(instance)
            }
        }
    }
    
    fun reload() {
        logger.info("Reloading Lobby Plugin...")
        
        try {
            // Reload configurations
            configManager.loadAllConfigs()
            
            // Reload managers
            queueManager.reload()
            scoreboardManager.reload()
            
            // Reload schematics
            coroutineScope.launch {
                try {
                    schematicManager.reload()
                } catch (e: Exception) {
                    logger.error("Failed to reload schematic manager", e)
                }
            }
            
            logger.info("Lobby Plugin successfully reloaded!")
        } catch (e: Exception) {
            logger.error("Error during plugin reload", e)
            throw e
        }
    }
}
