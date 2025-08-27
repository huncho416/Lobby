package huncho.main.lobby.config

import huncho.main.lobby.LobbyPlugin
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStream

class ConfigManager(private val plugin: LobbyPlugin) {
    
    private val configFolder = File("config/lobby")
    private val yaml = Yaml()
    
    // Configuration data
    var mainConfig: Map<String, Any> = emptyMap()
        private set
    var messagesConfig: Map<String, Any> = emptyMap()
        private set
    var scoreboardConfig: Map<String, Any> = emptyMap()
        private set
    var serversConfig: Map<String, Any> = emptyMap()
        private set
    var queuesConfig: Map<String, Any> = emptyMap()
        private set
    
    fun loadAllConfigs() {
        createConfigFolder()
        
        mainConfig = loadConfig("config.yml", getDefaultMainConfig())
        messagesConfig = loadConfig("messages.yml", getDefaultMessagesConfig())
        scoreboardConfig = loadConfig("scoreboard.yml", getDefaultScoreboardConfig())
        serversConfig = loadConfig("servers.yml", getDefaultServersConfig())
        queuesConfig = loadConfig("queues.yml", getDefaultQueuesConfig())
        
        LobbyPlugin.logger.info("All configuration files loaded successfully!")
    }
    
    private fun createConfigFolder() {
        if (!configFolder.exists()) {
            configFolder.mkdirs()
        }
    }
    
    private fun loadConfig(filename: String, defaultContent: String): Map<String, Any> {
        val file = File(configFolder, filename)
        
        if (!file.exists()) {
            file.writeText(defaultContent)
            LobbyPlugin.logger.info("Created default $filename")
        }
        
        return try {
            FileInputStream(file).use { input ->
                val result = yaml.load<Map<String, Any>>(input) ?: emptyMap()
                LobbyPlugin.logger.info("Successfully loaded $filename with ${result.keys.size} root keys: ${result.keys}")
                result
            }
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to load $filename", e)
            emptyMap()
        }
    }
    
    // Utility methods for accessing config values
    fun getString(config: Map<String, Any>, path: String, default: String = ""): String {
        return getNestedValue(config, path) as? String ?: default
    }
    
    fun getInt(config: Map<String, Any>, path: String, default: Int = 0): Int {
        return getNestedValue(config, path) as? Int ?: default
    }
    
    fun getBoolean(config: Map<String, Any>, path: String, default: Boolean = false): Boolean {
        return getNestedValue(config, path) as? Boolean ?: default
    }
    
    fun getList(config: Map<String, Any>, path: String): List<*> {
        return getNestedValue(config, path) as? List<*> ?: emptyList<Any>()
    }
    
    fun getMap(config: Map<String, Any>, path: String): Map<String, Any> {
        return getNestedValue(config, path) as? Map<String, Any> ?: emptyMap()
    }
    
    fun getDouble(config: Map<String, Any>, path: String, default: Double = 0.0): Double {
        val value = getNestedValue(config, path)
        return when (value) {
            is Double -> value
            is Number -> value.toDouble()
            else -> default
        }
    }
    
    fun getLong(config: Map<String, Any>, path: String, default: Long = 0L): Long {
        val value = getNestedValue(config, path)
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            else -> default
        }
    }
    
    private fun getNestedValue(config: Map<String, Any>, path: String): Any? {
        val keys = path.split(".")
        var current: Any? = config
        
        for (key in keys) {
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> null
            }
            if (current == null) break
        }
        
        return current
    }
    
    // Default configuration content methods
    private fun getDefaultMainConfig(): String {
        return """
# Lobby Plugin Main Configuration
# ================================

# Database Configuration
database:
  mongodb:
    uri: "mongodb://localhost:27017"
    database: "radium"
    collection: "players"
  redis:
    host: "localhost"
    port: 6379
    password: ""
    database: 0

# Lobby Settings
lobby:
  world: "lobby"
  spawn:
    x: 0.5
    y: 65.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
  
  # Auto-features
  auto-fly: true
  auto-speed: 2
  teleport-on-join: true
  
  # Join Items (slot: item)
  join-items:
    0: "COMPASS:Server Selector"
    4: "REDSTONE:Player Visibility"
    8: "BARRIER:Leave Server"

# Protection Settings
protection:
  block-break: true
  block-place: true
  item-drop: true
  item-pickup: true
  damage: true
  hunger: true
  chat: true
  portal: true
  inventory-move: true
  interaction: true

# Features
features:
  double-jump: true
  launch-pads: true
  player-visibility: true
  scoreboard: true
  join-messages: true
  leave-messages: true

# Update Notifications
updates:
  check-for-updates: true
  notify-staff: true
        """.trimIndent()
    }
    
    private fun getDefaultMessagesConfig(): String {
        return """
# Lobby Plugin Messages Configuration
# ===================================

# Prefixes
prefix: "&8[&bLobby&8] &7"
error-prefix: "&8[&cLobby&8] &c"
success-prefix: "&8[&aLobby&8] &a"

# Join/Leave Messages
join-message: "&8[&a+&8] &7{player} &ajoined the lobby"
leave-message: "&8[&c-&8] &7{player} &cleft the lobby"

# Commands
commands:
  no-permission: "&cYou don't have permission to use this command!"
  player-only: "&cThis command can only be used by players!"
  usage: "&cUsage: {usage}"
  reload-success: "&aConfiguration reloaded successfully!"
  
  # Spawn
  spawn-set: "&aSpawn location set successfully!"
  spawn-teleport: "&aTeleported to spawn!"
  
  # Fly
  fly-enabled: "&aFly mode enabled!"
  fly-disabled: "&cFly mode disabled!"
  
  # Queue
  queue-joined: "&aYou joined the {queue} queue! &7(Position: #{position})"
  queue-left: "&cYou left the {queue} queue!"
  queue-not-in: "&cYou are not in any queue!"
  queue-not-found: "&cQueue '{queue}' not found!"
  queue-paused: "&eQueue '{queue}' has been paused!"
  queue-unpaused: "&aQueue '{queue}' has been unpaused!"
  queue-already-paused: "&cQueue '{queue}' is already paused!"
  queue-already-unpaused: "&cQueue '{queue}' is already running!"
  
  # Build Mode
  build-enabled: "&aBuild mode enabled!"
  build-disabled: "&cBuild mode disabled!"
  
  # Player Visibility
  visibility-all: "&aShowing all players!"
  visibility-staff: "&eShowing staff only!"
  visibility-none: "&cHiding all players!"
  
  # Scoreboard
  scoreboard-enabled: "&aScoreboard enabled!"
  scoreboard-disabled: "&cScoreboard disabled!"

# Menu Titles
menus:
  servers: "&8Server Selector"
  visibility: "&8Player Visibility"

# Items
items:
  server-selector: "&bServer Selector"
  player-visibility: "&ePlayer Visibility"
  leave-server: "&cLeave Server"

# Queue System
queue:
  priority:
    1: "&c[VIP+]"
    2: "&6[VIP]"
    3: "&a[Member]"
  
  full: "&cThe {server} queue is currently full!"
  connecting: "&aConnecting you to {server}..."
  failed: "&cFailed to connect to {server}. Please try again!"

# Protection Messages
protection:
  block-break: "&cYou cannot break blocks in the lobby!"
  block-place: "&cYou cannot place blocks in the lobby!"
  item-drop: "&cYou cannot drop items in the lobby!"
  item-pickup: "&cYou cannot pick up items in the lobby!"
  damage: "&cYou cannot take damage in the lobby!"
  chat: "&cYou cannot chat in the lobby!"
  portal: "&cPortals are disabled in the lobby!"
  inventory-move: "&cYou cannot move items in menus!"
  interaction: "&cYou cannot interact with that!"
        """.trimIndent()
    }
    
    private fun getDefaultScoreboardConfig(): String {
        return """
# Lobby Plugin Scoreboard Configuration
# =====================================

# Scoreboard Settings
scoreboard:
  enabled: true
  title: "&b&lLOBBY"
  update-interval: 20 # ticks (1 second)
  
  # Lines (use {placeholder} for dynamic content)
  lines:
    - ""
    - "&7Online: &b{online_players}"
    - "&7Rank: {player_rank}"
    - ""
    - "&7Queue: {player_queue}"
    - "&7Position: &e#{queue_position}"
    - ""
    - "&7Server: &aLobby"
    - ""
    - "&ewww.example.com"

# Placeholders
placeholders:
  online_players: "Online players count"
  player_rank: "Player's rank from Radium"
  player_queue: "Current queue name or 'None'"
  queue_position: "Position in queue or 'N/A'"
  player_name: "Player's display name"
  server_name: "Current server name"
        """.trimIndent()
    }
    
    private fun getDefaultServersConfig(): String {
        return """
# Lobby Plugin Servers Configuration
# ==================================

# Server Menu Settings
servers-menu:
  title: "&8Server Selector"
  size: 27 # 9, 18, 27, 36, 45, 54
  
  # Menu Items
  items:
    # Practice Server
    practice:
      slot: 10
      material: "DIAMOND_SWORD"
      name: "&b&lPractice"
      lore:
        - "&7Fight other players in various"
        - "&7game modes and improve your PvP skills!"
        - ""
        - "&aOnline: &f{practice_online}"
        - "&aStatus: &f{practice_status}"
        - ""
        - "&eClick to join!"
      server: "practice"
      
    # Survival Server  
    survival:
      slot: 12
      material: "GRASS_BLOCK"
      name: "&a&lSurvival"
      lore:
        - "&7Experience vanilla Minecraft"
        - "&7survival with friends!"
        - ""
        - "&aOnline: &f{survival_online}"
        - "&aStatus: &f{survival_status}"
        - ""
        - "&eClick to join!"
      server: "survival"
      
    # Creative Server
    creative:
      slot: 14
      material: "BARRIER"
      name: "&d&lCreative"
      lore:
        - "&7Build amazing structures"
        - "&7with unlimited resources!"
        - ""
        - "&aOnline: &f{creative_online}"
        - "&aStatus: &f{creative_status}"
        - ""
        - "&eClick to join!"
      server: "creative"
      
    # Minigames Server
    minigames:
      slot: 16
      material: "GOLD_INGOT"
      name: "&6&lMinigames"
      lore:
        - "&7Play various fun minigames"
        - "&7with other players!"
        - ""
        - "&aOnline: &f{minigames_online}"
        - "&aStatus: &f{minigames_status}"
        - ""
        - "&eClick to join!"
      server: "minigames"

# Server Definitions
servers:
  practice:
    name: "Practice"
    ip: "practice.example.com"
    port: 25565
    max-players: 100
    queue-enabled: true
    
  survival:
    name: "Survival"
    ip: "survival.example.com"
    port: 25565
    max-players: 50
    queue-enabled: false
    
  creative:
    name: "Creative"
    ip: "creative.example.com"
    port: 25565
    max-players: 30
    queue-enabled: false
    
  minigames:
    name: "Minigames"
    ip: "minigames.example.com"
    port: 25565
    max-players: 80
    queue-enabled: true
        """.trimIndent()
    }
    
    private fun getDefaultQueuesConfig(): String {
        return """
# Lobby Plugin Queues Configuration
# =================================

# Queue System Settings
queue-system:
  enabled: true
  max-position-display: 100
  update-interval: 5 # seconds
  
  # Priority levels (1 = highest priority)
  priority-levels:
    1: "queue.priority.1"
    2: "queue.priority.2"
    3: "queue.priority.3"
    4: "queue.priority.4"
    5: "queue.priority.5"
    # ... continues to 50

# Server Queues
queues:
  practice:
    enabled: true
    max-players: 100
    auto-send: true
    send-delay: 5 # seconds
    priority-slots: 20 # reserved for priority players
    
  minigames:
    enabled: true
    max-players: 80
    auto-send: true
    send-delay: 3
    priority-slots: 15
    
  survival:
    enabled: false
    
  creative:
    enabled: false

# Queue Messages
messages:
  position-update: "&7Queue position: &e#{position} &8| &7ETA: &e{eta}"
  server-full: "&cThe {server} server is currently full. You have been added to the queue."
  connecting: "&aConnecting you to {server}..."
  failed-connect: "&cFailed to connect to {server}. You have been moved back to position #{position}."
  queue-paused: "&eThe {server} queue is currently paused."
  queue-disabled: "&cThe {server} queue is currently disabled."
        """.trimIndent()
    }
}
