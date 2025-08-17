package huncho.main.lobby

import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.coordinate.Pos
import net.minestom.server.extras.velocity.VelocityProxy

fun main() {
    // Initialize Minestom server
    val minecraftServer = MinecraftServer.init()
    
    // Initialize our lobby plugin first to load config
    LobbyPlugin.initialize()
    
    // Configure Velocity support  
    val velocityEnabled = LobbyPlugin.configManager.getBoolean(LobbyPlugin.configManager.mainConfig, "server.velocity.enabled", true)
    if (velocityEnabled) {
        val velocitySecret = LobbyPlugin.configManager.getString(LobbyPlugin.configManager.mainConfig, "server.velocity.secret", "")
        if (velocitySecret.isNotEmpty() && velocitySecret != "your-velocity-secret-here" && velocitySecret.length >= 8) {
            VelocityProxy.enable(velocitySecret)
            println("✅ Velocity proxy support enabled")
        } else {
            println("⚠️ Velocity secret not configured properly")
        }
    }
    
    // Handle player login - set them to spawn in the lobby instance
    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        val player = event.player
        
        // Set the spawning instance to our lobby
        event.spawningInstance = LobbyPlugin.lobbyInstance
        
        // Set spawn position from config
        val spawnX = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.x", 0.5)
        val spawnY = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.y", 100.0)
        val spawnZ = LobbyPlugin.configManager.getDouble(LobbyPlugin.configManager.mainConfig, "spawn.z", 0.5)
        player.respawnPoint = Pos(spawnX, spawnY, spawnZ)
    }
    
    // Get server configuration
    val port = LobbyPlugin.configManager.getInt(LobbyPlugin.configManager.mainConfig, "server.port", 25566)
    val bindAddress = LobbyPlugin.configManager.getString(LobbyPlugin.configManager.mainConfig, "server.bind_address", "0.0.0.0")
    
    // Start the server
    minecraftServer.start(bindAddress, port)
    
    println("✅ Lobby server started successfully!")
    println("🌐 Server running on $bindAddress:$port")
    println("🔌 Velocity proxy support: ${if (velocityEnabled) "enabled" else "disabled"}")
    println("🎮 Players can now connect through the Radium proxy")
}
