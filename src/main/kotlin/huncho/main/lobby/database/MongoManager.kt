package huncho.main.lobby.database

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.config.ConfigManager
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.Document
import kotlinx.coroutines.flow.firstOrNull

class MongoManager(private val configManager: ConfigManager) {
    
    private var mongoClient: MongoClient? = null
    private var database: MongoDatabase? = null
    private lateinit var playersCollection: MongoCollection<Document>
    private lateinit var queuesCollection: MongoCollection<Document>
    private lateinit var serversCollection: MongoCollection<Document>
    
    @Volatile
    private var connected = false
    
    suspend fun connect() {
        try {
            val connectionString = configManager.getString(configManager.mainConfig, "database.mongodb.connection_string")
            val databaseName = configManager.getString(configManager.mainConfig, "database.mongodb.database_name")
            
            mongoClient = MongoClient.create(connectionString)
            database = mongoClient!!.getDatabase(databaseName)
            
            // Initialize collections
            playersCollection = database!!.getCollection("players")
            queuesCollection = database!!.getCollection("queues")
            serversCollection = database!!.getCollection("servers")
            
            connected = true
            LobbyPlugin.logger.info("Successfully connected to MongoDB")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to connect to MongoDB", e)
            connected = false
            throw e
        }
    }
    
    suspend fun getPlayerData(uuid: String): Document? {
        return try {
            playersCollection.find(Document("uuid", uuid)).firstOrNull()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get player data for $uuid", e)
            null
        }
    }
    
    suspend fun savePlayerData(uuid: String, data: Document) {
        try {
            playersCollection.replaceOne(
                Document("uuid", uuid),
                data.append("uuid", uuid),
                com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to save player data for $uuid", e)
        }
    }
    
    suspend fun getPlayerProfile(uuid: String): Document? {
        return try {
            playersCollection.find(Document("uuid", uuid)).firstOrNull()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get player profile for $uuid", e)
            null
        }
    }
    
    suspend fun getPlayerRank(uuid: String): String {
        return try {
            val profile = getPlayerProfile(uuid)
            profile?.getString("rank") ?: "member"
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get player rank for $uuid", e)
            "member"
        }
    }
    
    suspend fun getPlayerPermissions(uuid: String): List<String> {
        return try {
            val profile = getPlayerProfile(uuid)
            @Suppress("UNCHECKED_CAST")
            profile?.getList("permissions", String::class.java) ?: emptyList()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get player permissions for $uuid", e)
            emptyList()
        }
    }
    
    suspend fun hasPermission(uuid: String, permission: String): Boolean {
        return try {
            val permissions = getPlayerPermissions(uuid)
            permissions.contains(permission) || permissions.contains("*")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to check permission $permission for $uuid", e)
            false
        }
    }
    
    suspend fun getQueueData(serverName: String): Document? {
        return try {
            queuesCollection.find(Document("server", serverName)).firstOrNull()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get queue data for $serverName", e)
            null
        }
    }
    
    suspend fun saveQueueData(serverName: String, data: Document) {
        try {
            queuesCollection.replaceOne(
                Document("server", serverName),
                data.append("server", serverName),
                com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to save queue data for $serverName", e)
        }
    }
    
    suspend fun getServerData(serverName: String): Document? {
        return try {
            serversCollection.find(Document("name", serverName)).firstOrNull()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get server data for $serverName", e)
            null
        }
    }
    
    suspend fun saveServerData(serverName: String, data: Document) {
        try {
            serversCollection.replaceOne(
                Document("name", serverName),
                data.append("name", serverName),
                com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to save server data for $serverName", e)
        }
    }
    
    suspend fun getLobbyData(key: String): Document? {
        return try {
            val lobbyCollection: MongoCollection<Document> = database!!.getCollection("lobby")
            lobbyCollection.find(Document("key", key)).firstOrNull()
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to get lobby data for key $key", e)
            null
        }
    }
    
    suspend fun saveLobbyData(key: String, data: Document) {
        try {
            val lobbyCollection: MongoCollection<Document> = database!!.getCollection("lobby")
            lobbyCollection.replaceOne(
                Document("key", key),
                data.append("key", key),
                com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Failed to save lobby data for key $key", e)
        }
    }

    fun isConnected(): Boolean = connected
    
    fun getDatabase(databaseName: String): MongoDatabase? {
        return mongoClient?.getDatabase(databaseName)
    }
    
    fun close() {
        try {
            mongoClient?.close()
            connected = false
            LobbyPlugin.logger.info("MongoDB connection closed")
        } catch (e: Exception) {
            LobbyPlugin.logger.error("Error closing MongoDB connection", e)
        }
    }
}
