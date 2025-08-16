package huncho.main.lobby.schematics

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Service for loading, caching, and pasting schematics in the lobby
 */
interface SchematicService {
    
    /**
     * Load and cache a schematic from file
     */
    suspend fun loadSchematic(file: File): SchematicHandle?
    
    /**
     * Load a schematic from config name
     */
    suspend fun loadSchematic(configName: String): SchematicHandle?
    
    /**
     * Paste a schematic with the given options
     */
    suspend fun pasteSchematic(
        instance: Instance,
        handle: SchematicHandle,
        options: PasteOptions = PasteOptions()
    ): PasteResult
    
    /**
     * Paste a schematic by config name
     */
    suspend fun pasteSchematic(
        instance: Instance,
        configName: String,
        options: PasteOptions = PasteOptions()
    ): PasteResult
    
    /**
     * Get all loaded schematics
     */
    fun getLoadedSchematics(): Map<String, SchematicHandle>
    
    /**
     * Check if a schematic is cached
     */
    fun isCached(configName: String): Boolean
    
    /**
     * Clear the cache
     */
    fun clearCache()
    
    /**
     * Clear a specific schematic from cache
     */
    fun clearCache(configName: String)
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats
    
    /**
     * Reload schematics from config
     */
    suspend fun reload()
}

/**
 * Handle to a loaded schematic with metadata
 */
data class SchematicHandle(
    val name: String,
    val file: File,
    val schematic: net.hollowcube.schem.Schematic,
    val loadTime: Long = System.currentTimeMillis(),
    val source: SchematicSource
)

/**
 * Source of a schematic
 */
sealed class SchematicSource {
    object Config : SchematicSource()
    data class File(val path: String) : SchematicSource()
    data class Manual(val loader: String) : SchematicSource()
}

/**
 * Options for pasting a schematic
 */
data class PasteOptions(
    val origin: Pos? = null,
    val rotation: Int = 0, // 0, 90, 180, 270 degrees
    val mirror: Boolean = false,
    val pasteAir: Boolean = false,
    val async: Boolean = true
) {
    companion object {
        fun fromConfig(config: Map<String, Any>): PasteOptions {
            val origin = config["origin"]?.let { originMap ->
                if (originMap is Map<*, *>) {
                    val x = (originMap["x"] as? Number)?.toDouble() ?: 0.0
                    val y = (originMap["y"] as? Number)?.toDouble() ?: 64.0
                    val z = (originMap["z"] as? Number)?.toDouble() ?: 0.0
                    Pos(x, y, z)
                } else null
            }
            
            return PasteOptions(
                origin = origin,
                rotation = (config["rotation"] as? Number)?.toInt() ?: 0,
                mirror = config["mirror"] as? Boolean ?: false,
                pasteAir = config["paste_air"] as? Boolean ?: false,
                async = true
            )
        }
    }
}

/**
 * Result of a schematic paste operation
 */
data class PasteResult(
    val success: Boolean,
    val blocksPlaced: Int = 0,
    val timeTaken: Long = 0,
    val error: String? = null,
    val handle: SchematicHandle? = null
) {
    companion object {
        fun success(blocksPlaced: Int, timeTaken: Long, handle: SchematicHandle) = 
            PasteResult(true, blocksPlaced, timeTaken, null, handle)
            
        fun failure(error: String) = 
            PasteResult(false, 0, 0, error, null)
    }
}

/**
 * Cache statistics
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hitCount: Long = 0,
    val missCount: Long = 0,
    val loadCount: Long = 0,
    val totalMemoryUsage: Long = 0
) {
    val hitRate: Double get() = if (hitCount + missCount == 0L) 0.0 else hitCount.toDouble() / (hitCount + missCount)
}
