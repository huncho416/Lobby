package huncho.main.lobby.redis

import huncho.main.lobby.LobbyPlugin
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Redis connections for the Lobby plugin
 */
class RedisManager(private val plugin: LobbyPlugin) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RedisManager::class.java)
    }
    
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
    private val isConnected = AtomicBoolean(false)
    
    /**
     * Initialize Redis connection
     */
    fun initialize(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val redisConfig = plugin.configManager.getMap(plugin.configManager.mainConfig, "redis")
                if (redisConfig.isEmpty()) {
                    throw IllegalStateException("Redis configuration not found")
                }
                
                if (!plugin.configManager.getBoolean(plugin.configManager.mainConfig, "redis.enabled", false)) {
                    logger.info("Redis is disabled in configuration")
                    return@supplyAsync false
                }
                
                val host = plugin.configManager.getString(plugin.configManager.mainConfig, "redis.host", "localhost")
                val port = plugin.configManager.getInt(plugin.configManager.mainConfig, "redis.port", 6379)
                val password = plugin.configManager.getString(plugin.configManager.mainConfig, "redis.password", "")
                val database = plugin.configManager.getInt(plugin.configManager.mainConfig, "redis.database", 0)
                val timeout = plugin.configManager.getLong(plugin.configManager.mainConfig, "redis.timeout", 5000)
                
                logger.info("Connecting to Redis at $host:$port (database: $database)...")
                
                // Build Redis URI
                val uriBuilder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabase(database)
                    .withTimeout(Duration.ofMillis(timeout))
                
                if (password.isNotEmpty()) {
                    uriBuilder.withPassword(password.toCharArray())
                }
                
                val redisUri = uriBuilder.build()
                
                // Create client and connections
                client = RedisClient.create(redisUri)
                connection = client!!.connect()
                pubSubConnection = client!!.connectPubSub()
                
                // Test connection
                val commands = connection!!.sync()
                commands.ping()
                
                isConnected.set(true)
                logger.info("Successfully connected to Redis")
                true
                
            } catch (e: Exception) {
                logger.error("Failed to connect to Redis", e)
                cleanup()
                false
            }
        }
    }
    
    /**
     * Get sync Redis commands
     */
    fun getCommands(): RedisCommands<String, String>? {
        return if (isConnected.get()) connection?.sync() else null
    }
    
    /**
     * Get async Redis commands
     */
    fun getAsyncCommands(): RedisAsyncCommands<String, String>? {
        return if (isConnected.get()) connection?.async() else null
    }
    
    /**
     * Get pub/sub async commands
     */
    fun getPubSubCommands(): RedisPubSubAsyncCommands<String, String>? {
        return if (isConnected.get()) pubSubConnection?.async() else null
    }
    
    /**
     * Get pub/sub connection for listeners
     */
    fun getPubSubConnection(): StatefulRedisPubSubConnection<String, String>? {
        return if (isConnected.get()) pubSubConnection else null
    }
    
    /**
     * Check if Redis is connected
     */
    fun isConnected(): Boolean {
        return isConnected.get() && connection?.isOpen == true
    }
    
    /**
     * Shutdown Redis connections
     */
    fun shutdown() {
        logger.info("Shutting down Redis connections...")
        cleanup()
    }
    
    private fun cleanup() {
        isConnected.set(false)
        
        try {
            pubSubConnection?.close()
        } catch (e: Exception) {
            logger.warn("Error closing pub/sub connection", e)
        }
        
        try {
            connection?.close()
        } catch (e: Exception) {
            logger.warn("Error closing Redis connection", e)
        }
        
        try {
            client?.shutdown()
        } catch (e: Exception) {
            logger.warn("Error shutting down Redis client", e)
        }
        
        pubSubConnection = null
        connection = null
        client = null
    }
}
