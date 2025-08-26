package huncho.main.lobby

import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.api.GamemodeAPI
import huncho.main.lobby.api.RadiumCommandForwarder
import huncho.main.lobby.listeners.EventManager
import huncho.main.lobby.commands.CommandManager
import huncho.main.lobby.features.queue.QueueManager
import huncho.main.lobby.features.scoreboard.ScoreboardManager
import huncho.main.lobby.features.visibility.VisibilityManager
import huncho.main.lobby.features.spawn.SpawnManager
import huncho.main.lobby.features.protection.ProtectionManager
import huncho.main.lobby.features.world.WorldLightingManager
import huncho.main.lobby.features.tablist.TabListManager
import huncho.main.lobby.features.vanish.VanishStatusMonitor
import huncho.main.lobby.managers.SchematicManager
import huncho.main.lobby.integration.RadiumIntegration
import kotlinx.coroutines.*
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
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
    lateinit var eventManager: EventManager
    lateinit var commandManager: CommandManager
    
    // HTTP API
    lateinit var gamemodeAPI: GamemodeAPI
    
    // Radium Command Forwarding
    lateinit var radiumCommandForwarder: RadiumCommandForwarder
    
    // Feature Managers
    lateinit var queueManager: QueueManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var visibilityManager: VisibilityManager
    lateinit var spawnManager: SpawnManager
    lateinit var protectionManager: ProtectionManager
    lateinit var worldLightingManager: WorldLightingManager
    lateinit var tabListManager: TabListManager
    lateinit var vanishStatusMonitor: VanishStatusMonitor
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
            
            // Shutdown vanish status monitor
            if (::vanishStatusMonitor.isInitialized) {
                vanishStatusMonitor.shutdown()
            }
            
            // Shutdown integrations
            radiumIntegration.shutdown()
            
            // Shutdown HTTP API
            gamemodeAPI.shutdown()
            
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
        
        // HTTP API for gamemode synchronization
        gamemodeAPI = GamemodeAPI(configManager)
        gamemodeAPI.initialize()
        
        // Radium integration
        radiumIntegration = RadiumIntegration(configManager)
        radiumIntegration.initialize()
        
        // Radium command forwarding
        val radiumApiUrl = configManager.getString(
            configManager.mainConfig,
            "radium.api.base_url",
            "http://localhost:8080"
        )
        radiumCommandForwarder = RadiumCommandForwarder(radiumApiUrl)
        
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
        vanishStatusMonitor = VanishStatusMonitor(this)
        
        // Initialize the new managers
        worldLightingManager.initialize()
        tabListManager.initialize()
        vanishStatusMonitor.initialize()
        
        // Register all event listeners
        eventManager.registerAllListeners()
        
        // Register all commands
        commandManager.registerAllCommands()
    }
    
    private fun setupLobbyWorld() {
        logger.info("Setting up lobby world...")
        
        val instanceManager = MinecraftServer.getInstanceManager()
        lobbyInstance = instanceManager.createInstanceContainer()
        
        // Enable automatic lighting updates
        lobbyInstance.setChunkSupplier { instance, x, z -> LightingChunk(instance, x, z) }
        
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
    
    fun shutdown() {
        logger.info("Shutting down Lobby Plugin...")
        
        try {
            // Shutdown event manager and join item monitor
            if (::eventManager.isInitialized) {
                eventManager.shutdown()
            }
            
            // Shutdown vanish status monitor
            if (::vanishStatusMonitor.isInitialized) {
                vanishStatusMonitor.shutdown()
            }
            
            // Cancel all coroutines
            coroutineScope.cancel()
            
            logger.info("Lobby Plugin shutdown complete!")
        } catch (e: Exception) {
            logger.error("Error during plugin shutdown", e)
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
