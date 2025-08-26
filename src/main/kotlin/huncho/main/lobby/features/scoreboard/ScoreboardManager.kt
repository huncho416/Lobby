package huncho.main.lobby.features.scoreboard

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.timer.TaskSchedule
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ScoreboardManager(private val plugin: LobbyPlugin) {
    
    private val playerScoreboards = ConcurrentHashMap<String, Sidebar>()
    private val enabledPlayers = ConcurrentHashMap<String, Boolean>()
    private val updateJob = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        startUpdateTask()
    }
    
    private fun startUpdateTask() {
        if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "scoreboard.enabled", true)) {
            return
        }
        
        // Use 5 second update interval for scoreboard
        updateJob.launch {
            while (true) {
                try {
                    updateAllScoreboards()
                    delay(5000L) // 5 seconds in milliseconds
                } catch (e: Exception) {
                    LobbyPlugin.logger.error("Error updating scoreboards", e)
                }
            }
        }
    }
    
    /**
     * Add scoreboard for a player
     */
    fun addPlayer(player: Player) {
        val uuid = player.uuid.toString()
        
        runBlocking {
            // Load player preference
            val enabled = getPlayerSetting(player, "scoreboard")
            enabledPlayers[uuid] = enabled
            
            if (enabled) {
                createScoreboard(player)
            }
        }
    }
    
    /**
     * Remove scoreboard for a player
     */
    fun removePlayer(player: Player) {
        val uuid = player.uuid.toString()
        val scoreboard = playerScoreboards.remove(uuid)
        enabledPlayers.remove(uuid)
        
        scoreboard?.let { sidebar ->
            // Remove all viewers individually since viewers collection is unmodifiable
            sidebar.viewers.toList().forEach { viewer ->
                sidebar.removeViewer(viewer)
            }
        }
    }
    
    /**
     * Toggle scoreboard for a player
     */
    fun toggleScoreboard(player: Player): Boolean {
        val uuid = player.uuid.toString()
        val newState = !isScoreboardEnabled(player)
        
        enabledPlayers[uuid] = newState
        
        runBlocking {
            updatePlayerSetting(player, "scoreboard", newState)
        }
        
        if (newState) {
            createScoreboard(player)
        } else {
            removePlayerScoreboard(player)
        }
        
        return newState
    }
    
    /**
     * Check if scoreboard is enabled for a player
     */
    fun isScoreboardEnabled(player: Player): Boolean {
        val uuid = player.uuid.toString()
        return enabledPlayers.getOrDefault(uuid, true)
    }
    
    /**
     * Create scoreboard for a player
     */
    private fun createScoreboard(player: Player) {
        val uuid = player.uuid.toString()
        
        // Remove existing scoreboard
        removePlayerScoreboard(player)
        
        val title = plugin.configManager.getString(plugin.configManager.mainConfig, "scoreboard.title", "&dMythic&fPvP")
        val sidebar = Sidebar(MessageUtils.colorize(title))
        
        // Add to viewer
        sidebar.addViewer(player)
        
        playerScoreboards[uuid] = sidebar
        
        // Update content asynchronously
        CoroutineScope(Dispatchers.Default).launch {
            updateScoreboardContent(player, sidebar)
        }
    }
    
    /**
     * Remove scoreboard for a player
     */
    private fun removePlayerScoreboard(player: Player) {
        val uuid = player.uuid.toString()
        val existing = playerScoreboards.remove(uuid)
        existing?.viewers?.clear()
    }
    
    /**
     * Update scoreboard content for a player
     */
    private suspend fun updateScoreboardContent(player: Player, sidebar: Sidebar) {
        val lines = plugin.configManager.getList(plugin.configManager.mainConfig, "scoreboard.lines")
        
        // Remove existing lines first
        val existingLines = sidebar.lines.toList() // Create a copy to avoid concurrent modification
        existingLines.forEach { line ->
            sidebar.removeLine(line.id)
        }
        
        // Add new lines with placeholders replaced (MythicHub style - bottom to top)
        lines.filterIsInstance<String>().forEachIndexed { index, lineStr ->
            val processedLine = replacePlaceholders(player, lineStr)
            
            // Lines are displayed bottom to top, so reverse the score
            val scoreboardLine = Sidebar.ScoreboardLine(
                "line_$index",
                MessageUtils.colorize(processedLine),
                lines.size - index
            )
            sidebar.createLine(scoreboardLine)
        }
    }
    
    /**
     * Replace placeholders in scoreboard lines
     */
    private suspend fun replacePlaceholders(player: Player, line: String): String {
        var result = line
        
        // Basic placeholders - MythicHub style
        result = result.replace("%player_name%", player.username)
        result = result.replace("%online_players%", plugin.lobbyInstance.players.size.toString())
        result = result.replace("%current_server%", "Lobby")
        result = result.replace("%date%", java.time.LocalDate.now().toString())
        
        // Server-specific player counts (placeholders for now)
        result = result.replace("%skyblock_players%", "0")
        result = result.replace("%gens_players%", "0")
        result = result.replace("%prison_players%", "0")
        
        // Radium integration placeholders
        try {
            val playerData = plugin.radiumIntegration.getPlayerData(player.uuid).join()
            if (playerData?.rank != null) {
                val rankName = playerData.rank.name
                result = result.replace("%player_rank%", rankName)
            } else {
                result = result.replace("%player_rank%", "Default")
            }
        } catch (e: Exception) {
            result = result.replace("%player_rank%", "Default")
        }
        
        // Queue placeholders
        val currentQueue = plugin.queueManager.getPlayerCurrentQueue(player.uuid)
        result = result.replace("%player_queue%", currentQueue ?: "None")
        
        val queuePosition = plugin.queueManager.getPlayerPosition(player.uuid)
        result = result.replace("%queue_position%", queuePosition?.toString() ?: "N/A")
        
        return result
    }
    
    /**
     * Update all scoreboards
     */
    private fun updateAllScoreboards() {
        playerScoreboards.entries.removeAll { (uuid, sidebar) ->
            val player = plugin.lobbyInstance.players.find { it.uuid.toString() == uuid }
            if (player == null) {
                // Remove all viewers individually since viewers collection is unmodifiable
                sidebar.viewers.toList().forEach { viewer ->
                    sidebar.removeViewer(viewer)
                }
                true // Remove from map
            } else {
                if (isScoreboardEnabled(player)) {
                    // Update content asynchronously
                    CoroutineScope(Dispatchers.Default).launch {
                        updateScoreboardContent(player, sidebar)
                    }
                }
                false // Keep in map
            }
        }
    }
    
    /**
     * Reload scoreboards
     */
    fun reload() {
        // Clear existing scoreboards
        playerScoreboards.clear()
        LobbyPlugin.logger.info("ScoreboardManager reloaded")
    }
    
    // Stub methods for missing RadiumIntegration calls
    private fun getPlayerSetting(player: Player, setting: String): Boolean {
        // TODO: Implement with RadiumIntegration HTTP API
        return true
    }
    
    private fun updatePlayerSetting(player: Player, setting: String, value: Boolean) {
        // TODO: Implement with RadiumIntegration HTTP API
    }
    
    private fun getRankDisplay(player: Player): String {
        // TODO: Implement with RadiumIntegration HTTP API
        return "&7[Member]"
    }

    /**
     * Get scoreboard statistics
     */
    fun getScoreboardStats(): Map<String, Int> {
        return mapOf(
            "total_players" to plugin.lobbyInstance.players.size,
            "scoreboard_enabled" to enabledPlayers.values.count { it },
            "scoreboard_disabled" to enabledPlayers.values.count { !it },
            "active_scoreboards" to playerScoreboards.size
        )
    }
    
    /**
     * Update scoreboard for a specific player (public method for vanish events)
     */
    fun updateScoreboard(player: Player) {
        val uuid = player.uuid.toString()
        val sidebar = playerScoreboards[uuid]
        
        if (sidebar != null && enabledPlayers[uuid] == true) {
            updateJob.launch {
                try {
                    updateScoreboardContent(player, sidebar)
                } catch (e: Exception) {
                    LobbyPlugin.logger.error("Error updating scoreboard for ${player.username}", e)
                }
            }
        }
    }
}
