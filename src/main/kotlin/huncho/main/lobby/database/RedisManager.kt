package huncho.main.lobby.database

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.config.ConfigManager
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Jedis
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class RedisManager(private val configManager: ConfigManager) {
    
    private lateinit var jedisPool: JedisPool
    private val objectMapper = jacksonObjectMapper()
    
    init {
        connect()
    }
    
    private fun connect() {
        try {
            val host = configManager.getString(configManager.mainConfig, "database.redis.host")
            val port = configManager.getInt(configManager.mainConfig, "database.redis.port")
            val password = configManager.getString(configManager.mainConfig, "database.redis.password")
            val database = configManager.getInt(configManager.mainConfig, "database.redis.database")
            
            val poolConfig = JedisPoolConfig().apply {
                maxTotal = 20
                maxIdle = 10
                minIdle = 2
                testOnBorrow = true
                testOnReturn = true
                testWhileIdle = true
                maxWaitMillis = 5000
            }
            
            jedisPool = if (password.isNotEmpty()) {
                JedisPool(poolConfig, host, port, 5000, password, database)
            } else {
                JedisPool(poolConfig, host, port, 5000, null, database)
            }
            
            // Test connection
            jedisPool.resource.use { jedis ->
                jedis.ping()
            }
            
            LobbyPlugin.logger.info("Successfully connected to Redis")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to connect to Redis", e)
            throw e
        }
    }
    
    fun <T> withJedis(action: (Jedis) -> T): T {
        return jedisPool.resource.use { jedis ->
            action(jedis)
        }
    }
    
    // Player cache methods
    fun cachePlayerData(uuid: String, data: Map<String, Any>, expireSeconds: Int = 3600) {
        withJedis { jedis ->
            val json = objectMapper.writeValueAsString(data)
            jedis.setex("player:$uuid", expireSeconds.toLong(), json)
        }
    }
    
    fun getCachedPlayerData(uuid: String): Map<String, Any>? {
        return withJedis { jedis ->
            val json = jedis.get("player:$uuid")
            json?.let { objectMapper.readValue<Map<String, Any>>(it) }
        }
    }
    
    fun removeCachedPlayerData(uuid: String) {
        withJedis { jedis ->
            jedis.del("player:$uuid")
        }
    }
    
    // Queue cache methods
    fun cacheQueuePosition(uuid: String, queueName: String, position: Int) {
        withJedis { jedis ->
            jedis.setex("queue:$queueName:$uuid", 300, position.toString()) // 5 minute expiry
        }
    }
    
    fun getCachedQueuePosition(uuid: String, queueName: String): Int? {
        return withJedis { jedis ->
            jedis.get("queue:$queueName:$uuid")?.toIntOrNull()
        }
    }
    
    fun removeQueuePosition(uuid: String, queueName: String) {
        withJedis { jedis ->
            jedis.del("queue:$queueName:$uuid")
        }
    }
    
    // Server status cache
    fun cacheServerStatus(serverName: String, online: Int, maxPlayers: Int, status: String) {
        withJedis { jedis ->
            val data = mapOf(
                "online" to online,
                "maxPlayers" to maxPlayers,
                "status" to status,
                "lastUpdate" to System.currentTimeMillis()
            )
            val json = objectMapper.writeValueAsString(data)
            jedis.setex("server:$serverName", 60, json) // 1 minute expiry
        }
    }
    
    fun getCachedServerStatus(serverName: String): Map<String, Any>? {
        return withJedis { jedis ->
            val json = jedis.get("server:$serverName")
            json?.let { objectMapper.readValue<Map<String, Any>>(it) }
        }
    }
    
    // Player settings cache
    fun cachePlayerSettings(uuid: String, settings: Map<String, Any>) {
        withJedis { jedis ->
            val json = objectMapper.writeValueAsString(settings)
            jedis.setex("settings:$uuid", 1800, json) // 30 minute expiry
        }
    }
    
    fun getCachedPlayerSettings(uuid: String): Map<String, Any>? {
        return withJedis { jedis ->
            val json = jedis.get("settings:$uuid")
            json?.let { objectMapper.readValue<Map<String, Any>>(it) }
        }
    }
    
    // Session tracking
    fun setPlayerOnline(uuid: String, serverName: String) {
        withJedis { jedis ->
            jedis.setex("online:$uuid", 300, serverName) // 5 minute expiry, should be refreshed
        }
    }
    
    fun setPlayerOffline(uuid: String) {
        withJedis { jedis ->
            jedis.del("online:$uuid")
        }
    }
    
    fun isPlayerOnline(uuid: String): Boolean {
        return withJedis { jedis ->
            jedis.exists("online:$uuid")
        }
    }
    
    fun getPlayerServer(uuid: String): String? {
        return withJedis { jedis ->
            jedis.get("online:$uuid")
        }
    }
    
    // Radium integration methods
    fun getCachedRank(uuid: String): String? {
        val playerData = getCachedPlayerData(uuid)
        return playerData?.get("rank") as? String
    }
    
    fun getCachedPermissions(uuid: String): List<String> {
        val playerData = getCachedPlayerData(uuid)
        return (playerData?.get("permissions") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }
    
    fun hasPermissionCached(uuid: String, permission: String): Boolean? {
        val permissions = getCachedPermissions(uuid)
        if (permissions.isEmpty()) return null // Not cached
        
        return permissions.contains(permission) || 
               permissions.any { it.endsWith("*") && permission.startsWith(it.dropLast(1)) }
    }
    
    // Publish/Subscribe for cross-server communication
    fun publishMessage(channel: String, message: String) {
        withJedis { jedis ->
            jedis.publish(channel, message)
        }
    }
    
    fun subscribeToChannel(channel: String, callback: (String) -> Unit) {
        Thread {
            withJedis { jedis ->
                jedis.subscribe(object : redis.clients.jedis.JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        callback(message)
                    }
                }, channel)
            }
        }.start()
    }
    
    // General cache methods
    fun set(key: String, value: String, expireSeconds: Int = -1) {
        withJedis { jedis ->
            if (expireSeconds > 0) {
                jedis.setex(key, expireSeconds.toLong(), value)
            } else {
                jedis.set(key, value)
            }
        }
    }
    
    fun get(key: String): String? {
        return withJedis { jedis ->
            jedis.get(key)
        }
    }
    
    fun delete(key: String) {
        withJedis { jedis ->
            jedis.del(key)
        }
    }
    
    fun exists(key: String): Boolean {
        return withJedis { jedis ->
            jedis.exists(key)
        }
    }
    
    fun close() {
        try {
            jedisPool.close()
            LobbyPlugin.logger.info("Redis connection closed")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to close Redis connection", e)
        }
    }
}
