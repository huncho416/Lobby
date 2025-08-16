package huncho.main.lobby.features.queue

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class ServerQueue(
    val name: String,
    val maxPlayers: Int,
    val autoSend: Boolean,
    val sendDelay: Int,
    val prioritySlots: Int
) {
    var isPaused: Boolean = false
    
    // Priority queues: lower number = higher priority
    private val priorityQueues = ConcurrentHashMap<Int, ConcurrentLinkedQueue<String>>()
    private val playerPriorities = ConcurrentHashMap<String, Int>()
    
    fun addPlayer(uuid: String, priority: Int) {
        // Remove player if already in queue
        removePlayer(uuid)
        
        // Add to appropriate priority queue
        val queue = priorityQueues.computeIfAbsent(priority) { ConcurrentLinkedQueue() }
        queue.offer(uuid)
        playerPriorities[uuid] = priority
    }
    
    fun removePlayer(uuid: String): Boolean {
        val priority = playerPriorities.remove(uuid) ?: return false
        val queue = priorityQueues[priority] ?: return false
        return queue.remove(uuid)
    }
    
    fun getNextPlayer(): String? {
        // Check priority queues in order (1 = highest priority)
        for (priority in 1..100) {
            val queue = priorityQueues[priority] ?: continue
            val player = queue.poll()
            if (player != null) {
                playerPriorities.remove(player)
                return player
            }
        }
        return null
    }
    
    fun getPosition(uuid: String): Int {
        val playerPriority = playerPriorities[uuid] ?: return -1
        var position = 1
        
        // Count players with higher priority
        for (priority in 1 until playerPriority) {
            val queue = priorityQueues[priority] ?: continue
            position += queue.size
        }
        
        // Count players with same priority who are ahead
        val sameQueue = priorityQueues[playerPriority] ?: return -1
        for (player in sameQueue) {
            if (player == uuid) break
            position++
        }
        
        return position
    }
    
    fun getSize(): Int {
        return playerPriorities.size
    }
    
    fun contains(uuid: String): Boolean {
        return playerPriorities.containsKey(uuid)
    }
    
    fun getAllPlayers(): List<String> {
        val players = mutableListOf<String>()
        
        for (priority in 1..100) {
            val queue = priorityQueues[priority] ?: continue
            players.addAll(queue)
        }
        
        return players
    }
    
    fun clear() {
        priorityQueues.clear()
        playerPriorities.clear()
    }
}
