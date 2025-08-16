package huncho.main.lobby.features.queue

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import kotlinx.coroutines.*
import org.bson.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.UUID

data class QueuePlayer(
    val uuid: UUID,
    val name: String,
    val priority: Int,
    val joinTime: Long
)

data class QueueInfo(
    val name: String,
    val maxPlayers: Int,
    val autoSend: Boolean,
    val sendDelay: Long,
    val prioritySlots: Int,
    var isPaused: Boolean = false,
    val players: ConcurrentLinkedQueue<QueuePlayer> = ConcurrentLinkedQueue()
)

class QueueManager(private val plugin: LobbyPlugin) {
    
    private val queues = ConcurrentHashMap<String, QueueInfo>()
    private val playerQueues = ConcurrentHashMap<UUID, String>() // UUID to queue name
    private val updateJob = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        loadQueues()
        startUpdateTask()
    }
    
    private fun loadQueues() {
        val queuesConfig = plugin.configManager.getMap(plugin.configManager.queuesConfig, "queues")
        
        queuesConfig.forEach { (queueName, queueData) ->
            if (queueData is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val data = queueData as Map<String, Any>
                val enabled = data["enabled"] as? Boolean ?: false
                
                if (enabled) {
                    val maxPlayers = data["max-players"] as? Int ?: 100
                    val autoSend = data["auto-send"] as? Boolean ?: true
                    val sendDelay = data["send-delay"] as? Int ?: 5000
                    val prioritySlots = data["priority-slots"] as? Int ?: 0
                    
                    val queueInfo = QueueInfo(
                        name = queueName,
                        maxPlayers = maxPlayers,
                        autoSend = autoSend,
                        sendDelay = sendDelay.toLong(),
                        prioritySlots = prioritySlots
                    )
                    
                    queues[queueName] = queueInfo
                    LobbyPlugin.logger.info("Loaded queue: $queueName")
                }
            }
        }
    }
    
    fun joinQueue(player: Player, queueName: String) {
        val queue = queues[queueName]
        if (queue == null) {
            val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-not-found")
                .replace("{queue}", queueName)
            MessageUtils.sendMessage(player, message)
            return
        }
        
        if (playerQueues.containsKey(player.uuid)) {
            val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.already-in-queue")
            MessageUtils.sendMessage(player, message)
            return
        }
        
        if (queue.isPaused) {
            val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-paused")
                .replace("{queue}", queueName)
            MessageUtils.sendMessage(player, message)
            return
        }
        
        // Check queue bypass permission
        plugin.radiumIntegration.hasPermission(player.uuid, "queue.bypass").thenAccept { hasBypass ->
            if (hasBypass) {
                // Bypass queue, send directly
                sendPlayerToServer(player, queueName)
                return@thenAccept
            }
            
            plugin.radiumIntegration.hasPermission(player.uuid, "queue.bypass.$queueName").thenAccept { hasSpecificBypass ->
                if (hasSpecificBypass) {
                    // Bypass queue, send directly
                    sendPlayerToServer(player, queueName)
                    return@thenAccept
                }
                
                // Continue with normal queue logic
                continueQueueJoin(player, queueName)
            }
        }
    }
    
    private fun continueQueueJoin(player: Player, queueName: String) {
        val queue = queues[queueName] ?: return
        
        // Get player priority
        val priority = getPlayerPriority(player)
        
        val queuePlayer = QueuePlayer(
            uuid = player.uuid,
            name = player.username,
            priority = priority,
            joinTime = System.currentTimeMillis()
        )
        
        // Add to queue in priority order
        addToQueueWithPriority(queue, queuePlayer)
        playerQueues[player.uuid] = queueName
        
        val position = getPlayerPosition(queue, player.uuid)
        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-joined")
            .replace("{queue}", queueName)
            .replace("{position}", position.toString())
        MessageUtils.sendMessage(player, message)
    }
    
    private fun addToQueueWithPriority(queue: QueueInfo, newPlayer: QueuePlayer) {
        val playersList = queue.players.toMutableList()
        
        // Find insertion point based on priority and join time
        var insertIndex = 0
        for (i in playersList.indices) {
            val existingPlayer = playersList[i]
            if (newPlayer.priority < existingPlayer.priority ||
                (newPlayer.priority == existingPlayer.priority && newPlayer.joinTime < existingPlayer.joinTime)) {
                insertIndex = i
                break
            }
            insertIndex = i + 1
        }
        
        playersList.add(insertIndex, newPlayer)
        
        // Rebuild queue
        queue.players.clear()
        queue.players.addAll(playersList)
    }
    
    private fun getPlayerPriority(player: Player): Int {
        // TODO: Implement async priority checking
        // For now, return default priority
        return 100 // Default priority (lowest)
    }
    
    private fun getPlayerPosition(queue: QueueInfo, uuid: UUID): Int {
        return queue.players.indexOfFirst { it.uuid == uuid } + 1
    }
    
    fun leaveQueue(player: Player): Boolean {
        val uuid = player.uuid
        val queueName = playerQueues[uuid]
        
        if (queueName == null) {
            val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-not-in")
            MessageUtils.sendMessage(player, message)
            return false
        }
        
        removePlayerFromQueue(uuid)
        
        val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.queue-left")
            .replace("{queue}", queueName)
        MessageUtils.sendMessage(player, message)
        
        return true
    }
    
    fun removePlayerFromQueue(uuid: UUID) {
        val queueName = playerQueues.remove(uuid) ?: return
        val queue = queues[queueName] ?: return
        
        queue.players.removeIf { it.uuid == uuid }
        // Queue position is now managed locally in memory
        // No need for Redis cache as positions are calculated dynamically
    }
    
    fun pauseQueue(queueName: String): Boolean {
        val queue = queues[queueName] ?: return false
        
        if (queue.isPaused) return false
        
        queue.isPaused = true
        return true
    }
    
    fun unpauseQueue(queueName: String): Boolean {
        val queue = queues[queueName] ?: return false
        
        if (!queue.isPaused) return false
        
        queue.isPaused = false
        return true
    }
    
    fun isQueuePaused(queueName: String): Boolean {
        return queues[queueName]?.isPaused ?: false
    }
    
    private fun sendPlayerToServer(player: Player, serverName: String) {
        val message = plugin.configManager.getString(plugin.configManager.queuesConfig, "messages.connecting")
            .replace("{server}", serverName)
        MessageUtils.sendMessage(player, message)
        
        // Use Radium integration to send player to server
        plugin.radiumIntegration.transferPlayer(player.username, serverName)
    }
    
    fun getQueueInfo(queueName: String): QueueInfo? {
        return queues[queueName]
    }
    
    fun getAllQueues(): Map<String, QueueInfo> {
        return queues.toMap()
    }
    
    fun getPlayerCurrentQueue(uuid: UUID): String? {
        return playerQueues[uuid]
    }
    
    fun getPlayerPosition(uuid: UUID): Int? {
        val queueName = playerQueues[uuid] ?: return null
        val queue = queues[queueName] ?: return null
        return getPlayerPosition(queue, uuid)
    }
    
    private fun startUpdateTask() {
        updateJob.launch {
            while (isActive) {
                processQueues()
                delay(1000) // Process every second
            }
        }
    }
    
    private suspend fun processQueues() {
        // TODO: Implement queue processing logic
        // Process each queue and send players to servers when ready
    }
    
    fun saveAllQueues() {
        // Save queue states to database if needed
        // TODO: Implement queue state persistence
    }
    
    fun reload() {
        queues.clear()
        playerQueues.clear()
        loadQueues()
    }
    
    fun cleanup() {
        updateJob.cancel()
    }
}
