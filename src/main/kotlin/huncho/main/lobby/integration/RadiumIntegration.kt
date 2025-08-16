package huncho.main.lobby.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import huncho.main.lobby.config.ConfigManager
import huncho.main.lobby.utils.MessageUtils
import net.minestom.server.entity.Player
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Integration with Radium using HTTP API for permissions, ranks, and player data
 */
class RadiumIntegration(
    private val configManager: ConfigManager
) {
    private val logger = LoggerFactory.getLogger(RadiumIntegration::class.java)
    private val playerCache = ConcurrentHashMap<UUID, PlayerData>()
    private val httpClient: OkHttpClient
    private val objectMapper = ObjectMapper()
    private var baseUrl: String = ""
    private var apiKey: String? = null
    
    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    fun initialize() {
        try {
            // Load Radium API configuration
            baseUrl = configManager.getString(configManager.mainConfig, "radium.api.base_url", "http://localhost:8080")
            apiKey = configManager.getString(configManager.mainConfig, "radium.api.key", "").takeIf { it.isNotEmpty() && it != "null" }
            
            // Ensure baseUrl ends with /api (removing the /v1 part as per new API structure)
            baseUrl = baseUrl.trimEnd('/')
            if (!baseUrl.endsWith("/api")) {
                baseUrl = "$baseUrl/api"
            }
            
            println("Radium HTTP API integration initialized - URL: $baseUrl")
        } catch (e: Exception) {
            println("Failed to initialize Radium integration: ${e.message}")
        }
    }
    
    fun shutdown() {
        playerCache.clear()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        println("Radium integration shut down")
    }
    
    /**
     * Get player data from Radium API with caching
     */
    fun getPlayerData(uuid: UUID): CompletableFuture<PlayerData?> {
        return CompletableFuture.supplyAsync {
            // Check cache first (5 minute TTL)
            val cached = playerCache[uuid]
            if (cached != null && cached.cachedAt.isAfter(Instant.now().minusSeconds(300))) {
                return@supplyAsync cached
            }
            
            try {
                // Get profile by UUID from Radium API
                val request = buildGetRequest("/players/$uuid")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        val jsonNode = objectMapper.readTree(body)
                        val playerData = parsePlayerDataFromProfile(jsonNode)
                        playerCache[uuid] = playerData
                        return@supplyAsync playerData
                    } else if (response.code == 404) {
                        // Player not found
                        return@supplyAsync null
                    } else {
                        println("Error fetching player profile for $uuid: HTTP ${response.code}")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                println("Error fetching player data for $uuid: ${e.message}")
                return@supplyAsync null
            }
        }
    }
    
    /**
     * Get player data by username
     */
    fun getPlayerDataByName(username: String): CompletableFuture<PlayerData?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/players/$username")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        val jsonNode = objectMapper.readTree(body)
                        val playerData = parsePlayerDataFromProfile(jsonNode)
                        
                        // Cache by UUID if available
                        val uuidString = jsonNode.get("uuid")?.asText()
                        if (uuidString != null) {
                            try {
                                val uuid = UUID.fromString(uuidString)
                                playerCache[uuid] = playerData
                            } catch (e: IllegalArgumentException) {
                                // Invalid UUID format
                            }
                        }
                        
                        return@supplyAsync playerData
                    } else if (response.code == 404) {
                        return@supplyAsync null
                    } else {
                        println("Error fetching player profile for $username: HTTP ${response.code}")
                        return@supplyAsync null
                    }
                }
            } catch (e: Exception) {
                println("Error fetching player data for $username: ${e.message}")
                return@supplyAsync null
            }
        }
    }
    
    /**
     * Check if player has permission via API
     */
    fun hasPermission(uuid: UUID, permission: String): CompletableFuture<Boolean> {
        return getPlayerData(uuid).thenCompose { playerData ->
            if (playerData == null) {
                return@thenCompose CompletableFuture.completedFuture(false)
            }
            
            // Check cached permissions first
            if (playerData.hasPermission(permission)) {
                return@thenCompose CompletableFuture.completedFuture(true)
            }
            
            // Make API call to get all permissions and check locally
            CompletableFuture.supplyAsync {
                try {
                    val request = buildGetRequest("/permissions/$uuid")
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: return@supplyAsync false
                            val jsonNode = objectMapper.readTree(body)
                            
                            // Parse permissions array and check if permission exists
                            val permissions = mutableListOf<String>()
                            jsonNode.forEach { permNode ->
                                permissions.add(permNode.asText())
                            }
                            
                            // Check if the specific permission exists
                            return@supplyAsync checkPermissionInList(permissions, permission)
                        }
                        return@supplyAsync false
                    }
                } catch (e: Exception) {
                    println("Error checking permission $permission for $uuid: ${e.message}")
                    return@supplyAsync false
                }
            }
        }
    }
    
    /**
     * Check if player has permission by username (converts to UUID first)
     */
    fun hasPermissionByName(username: String, permission: String): CompletableFuture<Boolean> {
        return getPlayerDataByName(username).thenCompose { playerData ->
            if (playerData?.uuid != null) {
                hasPermission(playerData.uuid, permission)
            } else {
                CompletableFuture.completedFuture(false)
            }
        }
    }
    
    /**
     * Helper method to check if a permission exists in a list of permissions
     */
    private fun checkPermissionInList(permissions: List<String>, permission: String): Boolean {
        // Check direct permissions
        if (permissions.contains(permission)) return true
        
        // Check wildcard permissions
        val parts = permission.split(".")
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (permissions.contains(wildcard)) return true
        }
        
        // Check for global permission
        return permissions.contains("*")
    }
    
    /**
     * Get player's highest rank
     */
    fun getPlayerRank(uuid: UUID): CompletableFuture<String?> {
        return getPlayerData(uuid).thenApply { playerData ->
            playerData?.highestRank
        }
    }
    
    /**
     * Get player's display name with rank prefix
     */
    fun getDisplayName(uuid: UUID): CompletableFuture<String> {
        return getPlayerData(uuid).thenApply { playerData ->
            playerData?.displayName ?: "Player"
        }
    }
    
    /**
     * Get all ranks from API
     */
    fun getAllRanks(): CompletableFuture<List<RankData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/ranks")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        val ranksArray = jsonNode.get("ranks")
                        
                        val ranks = mutableListOf<RankData>()
                        ranksArray?.forEach { rankNode ->
                            ranks.add(parseRankData(rankNode))
                        }
                        
                        return@supplyAsync ranks.sortedByDescending { it.weight }
                    }
                    return@supplyAsync emptyList()
                }
            } catch (e: Exception) {
                println("Error fetching ranks: ${e.message}")
                return@supplyAsync emptyList()
            }
        }
    }
    
    /**
     * Get specific rank data
     */
    fun getRank(rankName: String): CompletableFuture<RankData?> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/ranks/$rankName")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync null
                        val jsonNode = objectMapper.readTree(body)
                        return@supplyAsync parseRankData(jsonNode)
                    } else if (response.code == 404) {
                        return@supplyAsync null
                    }
                    return@supplyAsync null
                }
            } catch (e: Exception) {
                println("Error fetching rank $rankName: ${e.message}")
                return@supplyAsync null
            }
        }
    }
    
    /**
     * Send a message to a player via API
     */
    fun sendMessage(playerName: String, message: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val json = objectMapper.writeValueAsString(mapOf(
                    "from" to "lobby",
                    "to" to playerName,
                    "message" to message
                ))
                
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/players/message")
                    .post(requestBody)
                    .let { builder ->
                        apiKey?.let { key ->
                            builder.header("Authorization", "Bearer $key")
                        } ?: builder
                    }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    return@supplyAsync response.isSuccessful
                }
            } catch (e: Exception) {
                println("Error sending message to $playerName: ${e.message}")
                return@supplyAsync false
            }
        }
    }
    
    /**
     * Transfer a player to another server via API
     */
    fun transferPlayer(playerName: String, serverName: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val json = objectMapper.writeValueAsString(mapOf(
                    "player" to playerName,
                    "server" to serverName
                ))
                
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/players/transfer")
                    .post(requestBody)
                    .let { builder ->
                        apiKey?.let { key ->
                            builder.header("Authorization", "Bearer $key")
                        } ?: builder
                    }
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    return@supplyAsync response.isSuccessful
                }
            } catch (e: Exception) {
                println("Error transferring player $playerName to $serverName: ${e.message}")
                return@supplyAsync false
            }
        }
    }
    
    /**
     * Get server list and player counts
     */
    fun getServerList(): CompletableFuture<List<ServerData>> {
        return CompletableFuture.supplyAsync {
            try {
                val request = buildGetRequest("/servers")
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@supplyAsync emptyList()
                        val jsonNode = objectMapper.readTree(body)
                        val serversArray = jsonNode.get("servers")
                        
                        val servers = mutableListOf<ServerData>()
                        serversArray?.forEach { serverNode ->
                            val name = serverNode.get("name")?.asText() ?: ""
                            val playerCount = serverNode.get("playerCount")?.asInt() ?: 0
                            val isOnline = serverNode.get("isOnline")?.asBoolean() ?: false
                            servers.add(ServerData(name, playerCount, isOnline))
                        }
                        
                        return@supplyAsync servers
                    }
                    return@supplyAsync emptyList()
                }
            } catch (e: Exception) {
                println("Error fetching server list: ${e.message}")
                return@supplyAsync emptyList()
            }
        }
    }
    
    /**
     * Invalidate player cache
     */
    fun invalidatePlayer(uuid: UUID) {
        playerCache.remove(uuid)
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        playerCache.clear()
    }
    
    // Player sync methods for join/leave events
    fun syncPlayerOnJoin(player: Player) {
        // TODO: Implement player sync on join
        logger.info("Syncing player ${player.username} on join")
    }
    
    fun syncPlayerOnLeave(player: Player) {
        // TODO: Implement player sync on leave  
        logger.info("Syncing player ${player.username} on leave")
    }
    
    // Chat formatting method
    fun formatChatMessage(player: Player, message: String): String {
        val cachedData = playerCache[player.uuid]
        
        return if (cachedData != null && cachedData.rank != null) {
            // Use rank prefix and color
            val prefix = cachedData.rank.prefix.ifEmpty { "" }
            val color = cachedData.rank.color.ifEmpty { "&7" }
            "$prefix$color${cachedData.username}&7: &f$message"
        } else {
            // Try to fetch fresh data if not cached
            try {
                val playerData = getPlayerData(player.uuid).join()
                if (playerData != null && playerData.rank != null) {
                    val prefix = playerData.rank.prefix.ifEmpty { "" }
                    val color = playerData.rank.color.ifEmpty { "&7" }
                    "$prefix$color${playerData.username}&7: &f$message"
                } else {
                    // Use fallback from config
                    val fallbackPrefix = configManager.getString(configManager.mainConfig, "radium.fallback.default_prefix", "&7")
                    "$fallbackPrefix${player.username}&7: &f$message"
                }
            } catch (e: Exception) {
                logger.warn("Failed to format chat for ${player.username}, using fallback", e)
                // Ultimate fallback
                "&7${player.username}&7: &f$message"
            }
        }
    }
    
    // Tab list method
    fun getTabListName(player: Player): String {
        // TODO: Implement tab list name formatting
        return player.username
    }
    
    private fun buildGetRequest(endpoint: String): Request {
        val requestBuilder = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
        
        // Add API key if configured
        apiKey?.let { key ->
            requestBuilder.header("Authorization", "Bearer $key")
        }
        
        return requestBuilder.build()
    }
    
    private fun parsePlayerDataFromProfile(jsonNode: JsonNode): PlayerData {
        val username = jsonNode.get("username")?.asText() ?: "Unknown"
        val uuid = jsonNode.get("uuid")?.asText()?.let { 
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }
        
        val rankNode = jsonNode.get("rank")
        val rankData = if (rankNode != null && !rankNode.isNull) {
            parseRankData(rankNode)
        } else null
        
        val permissions = mutableListOf<String>()
        jsonNode.get("permissions")?.forEach { permNode ->
            permissions.add(permNode.asText())
        }
        
        val displayName = if (rankData != null) {
            "${rankData.prefix}$username"
        } else {
            username
        }
        
        val highestRankName: String = (rankData?.name as? String) ?: "Default"
        
        return PlayerData(
            uuid = uuid,
            username = username,
            rank = rankData,
            permissions = permissions,
            highestRank = highestRankName,
            displayName = displayName,
            cachedAt = Instant.now()
        )
    }
    
    private fun parseRankData(jsonNode: JsonNode): RankData {
        val name = jsonNode.get("name")?.asText() ?: ""
        val weight = jsonNode.get("weight")?.asInt() ?: 0
        val prefix = jsonNode.get("prefix")?.asText() ?: ""
        val color = jsonNode.get("color")?.asText() ?: "&f"
        
        val permissions = mutableListOf<String>()
        jsonNode.get("permissions")?.forEach { permNode ->
            permissions.add(permNode.asText())
        }
        
        return RankData(
            name = name,
            weight = weight,
            prefix = prefix,
            color = color,
            permissions = permissions
        )
    }
    
    data class PlayerData(
        val uuid: UUID?,
        val username: String,
        val rank: RankData?,
        val permissions: List<String>,
        val highestRank: String,
        val displayName: String,
        val cachedAt: Instant = Instant.now()
    ) {
        fun hasPermission(permission: String): Boolean {
            // Check rank permissions first
            rank?.let { rankData ->
                if (rankData.hasPermission(permission)) return true
            }
            
            // Check direct permissions
            if (permissions.contains(permission)) return true
            
            // Check wildcard permissions
            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".") + ".*"
                if (permissions.contains(wildcard)) return true
            }
            
            // Check for admin wildcard
            return permissions.contains("*")
        }
    }
    
    data class RankData(
        val name: String,
        val weight: Int,
        val prefix: String,
        val color: String,
        val permissions: List<String>
    ) {
        fun hasPermission(permission: String): Boolean {
            // Check direct permissions
            if (permissions.contains(permission)) return true
            
            // Check wildcard permissions
            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".") + ".*"
                if (permissions.contains(wildcard)) return true
            }
            
            // Check for admin wildcard
            return permissions.contains("*")
        }
    }
    
    data class ServerData(
        val name: String,
        val playerCount: Int,
        val isOnline: Boolean
    )
}
