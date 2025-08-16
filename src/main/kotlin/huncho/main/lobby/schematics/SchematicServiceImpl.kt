package huncho.main.lobby.schematics

import kotlinx.coroutines.*
import net.hollowcube.schem.Schematic
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of SchematicService with caching and async support
 */
class SchematicServiceImpl(
    private val schematicsConfig: Map<String, Any>,
    private val dataFolder: File,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SchematicService {
    
    private val logger = LoggerFactory.getLogger(SchematicServiceImpl::class.java)
    
    // Cache configuration
    private val cacheEnabled = schematicsConfig["cache_enabled"] as? Boolean ?: true
    private val maxCacheSize = (schematicsConfig["cache_max_size"] as? Number)?.toInt() ?: 10
    private val asyncOperations = schematicsConfig["async_operations"] as? Boolean ?: true
    
    // Cache storage
    private val cache = ConcurrentHashMap<String, SchematicHandle>()
    private val cacheOrder = mutableListOf<String>() // For LRU eviction
    
    // Cache statistics
    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val loadCount = AtomicLong(0)
    
    override suspend fun loadSchematic(file: File): SchematicHandle? {
        return withContext(if (asyncOperations) Dispatchers.IO else Dispatchers.Unconfined) {
            try {
                loadCount.incrementAndGet()
                
                if (!file.exists()) {
                    logger.error("Schematic file not found: ${file.absolutePath}")
                    return@withContext null
                }
                
                logger.info("Loading schematic from file: ${file.name}")
                val startTime = System.currentTimeMillis()
                
                FileInputStream(file).use { inputStream ->
                    val schematic = net.hollowcube.schem.SchematicReader().read(inputStream)
                    val loadTime = System.currentTimeMillis() - startTime
                    
                    logger.info("Successfully loaded schematic '${file.name}' in ${loadTime}ms " +
                        "(${schematic.size().x()}x${schematic.size().y()}x${schematic.size().z()})")
                    
                    SchematicHandle(
                        name = file.nameWithoutExtension,
                        file = file,
                        schematic = schematic,
                        loadTime = System.currentTimeMillis(),
                        source = SchematicSource.File(file.absolutePath)
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to load schematic from file: ${file.absolutePath}", e)
                null
            }
        }
    }
    
    override suspend fun loadSchematic(configName: String): SchematicHandle? {
        // Check cache first
        if (cacheEnabled) {
            cache[configName]?.let { cached ->
                hitCount.incrementAndGet()
                updateCacheOrder(configName)
                logger.debug("Loaded schematic '$configName' from cache")
                return cached
            }
            missCount.incrementAndGet()
        }
        
        // Load from config
        val filesConfig = schematicsConfig["files"] as? Map<String, Any>
        val schematicConfig = filesConfig?.get(configName) as? Map<String, Any>
        
        if (schematicConfig == null) {
            logger.error("Schematic configuration not found: $configName")
            return null
        }
        
        val enabled = schematicConfig["enabled"] as? Boolean ?: true
        if (!enabled) {
            logger.debug("Schematic '$configName' is disabled in config")
            return null
        }
        
        val filePath = schematicConfig["file"] as? String
        if (filePath == null) {
            logger.error("No file path specified for schematic: $configName")
            return null
        }
        
        val file = if (File(filePath).isAbsolute) {
            File(filePath)
        } else {
            File(dataFolder, filePath)
        }
        
        val handle = loadSchematic(file)?.copy(
            name = configName,
            source = SchematicSource.Config
        )
        
        // Cache the result
        if (handle != null && cacheEnabled) {
            addToCache(configName, handle)
        }
        
        return handle
    }
    
    override suspend fun pasteSchematic(
        instance: Instance,
        handle: SchematicHandle,
        options: PasteOptions
    ): PasteResult {
        return withContext(if (options.async) Dispatchers.IO else Dispatchers.Unconfined) {
            try {
                logger.info("Pasting schematic '${handle.name}' with options: $options")
                val startTime = System.currentTimeMillis()
                
                val origin = options.origin ?: Pos(0.0, 64.0, 0.0)
                var blocksPlaced = 0
                
                // Create rotation from options
                val rotation = when (options.rotation) {
                    90 -> net.hollowcube.schem.Rotation.CLOCKWISE_90
                    180 -> net.hollowcube.schem.Rotation.CLOCKWISE_180
                    270 -> net.hollowcube.schem.Rotation.CLOCKWISE_270
                    else -> net.hollowcube.schem.Rotation.NONE
                }
                
                // Apply schematic using the apply method
                handle.schematic.apply(rotation) { pos, block ->
                    // Skip air blocks if not pasting air
                    if (!options.pasteAir && block.isAir()) {
                        return@apply
                    }
                    
                    val worldPos = origin.add(pos.x(), pos.y(), pos.z())
                    instance.setBlock(worldPos, block)
                    blocksPlaced++
                }
                
                val timeTaken = System.currentTimeMillis() - startTime
                logger.info("Successfully pasted schematic '${handle.name}' - " +
                    "$blocksPlaced blocks placed in ${timeTaken}ms")
                
                PasteResult.success(blocksPlaced, timeTaken, handle)
                
            } catch (e: Exception) {
                logger.error("Failed to paste schematic '${handle.name}'", e)
                PasteResult.failure("Failed to paste schematic: ${e.message}")
            }
        }
    }
    
    override suspend fun pasteSchematic(
        instance: Instance,
        configName: String,
        options: PasteOptions
    ): PasteResult {
        val handle = loadSchematic(configName)
            ?: return PasteResult.failure("Failed to load schematic: $configName")
            
        // Merge config options with provided options
        val filesConfig = schematicsConfig["files"] as? Map<String, Any>
        val schematicConfig = filesConfig?.get(configName) as? Map<String, Any>
        val configOptions = schematicConfig?.let { PasteOptions.fromConfig(it) } ?: PasteOptions()
        
        val mergedOptions = options.copy(
            origin = options.origin ?: configOptions.origin,
            rotation = if (options.rotation != 0) options.rotation else configOptions.rotation,
            mirror = if (options.mirror) options.mirror else configOptions.mirror,
            pasteAir = if (options.pasteAir) options.pasteAir else configOptions.pasteAir
        )
        
        return pasteSchematic(instance, handle, mergedOptions)
    }
    
    override fun getLoadedSchematics(): Map<String, SchematicHandle> {
        return cache.toMap()
    }
    
    override fun isCached(configName: String): Boolean {
        return cache.containsKey(configName)
    }
    
    override fun clearCache() {
        cache.clear()
        synchronized(cacheOrder) {
            cacheOrder.clear()
        }
        logger.info("Schematic cache cleared")
    }
    
    override fun clearCache(configName: String) {
        cache.remove(configName)
        synchronized(cacheOrder) {
            cacheOrder.remove(configName)
        }
        logger.debug("Cleared schematic '$configName' from cache")
    }
    
    override fun getCacheStats(): CacheStats {
        val memoryUsage = cache.values.sumOf { estimateMemoryUsage(it) }
        
        return CacheStats(
            size = cache.size,
            maxSize = maxCacheSize,
            hitCount = hitCount.get(),
            missCount = missCount.get(),
            loadCount = loadCount.get(),
            totalMemoryUsage = memoryUsage
        )
    }
    
    override suspend fun reload() {
        logger.info("Reloading schematics...")
        clearCache()
        
        // Pre-load configured schematics if enabled
        val filesConfig = schematicsConfig["files"] as? Map<String, Any> ?: return
        
        for ((name, config) in filesConfig) {
            if (config is Map<*, *>) {
                val enabled = config["enabled"] as? Boolean ?: true
                if (enabled) {
                    try {
                        loadSchematic(name)
                        logger.debug("Pre-loaded schematic: $name")
                    } catch (e: Exception) {
                        logger.warn("Failed to pre-load schematic '$name'", e)
                    }
                }
            }
        }
        
        logger.info("Schematics reloaded - ${cache.size} schematics cached")
    }
    
    private fun addToCache(name: String, handle: SchematicHandle) {
        if (!cacheEnabled) return
        
        synchronized(cacheOrder) {
            // Remove if already exists
            cache.remove(name)
            cacheOrder.remove(name)
            
            // Add to cache
            cache[name] = handle
            cacheOrder.add(name)
            
            // Evict oldest if over limit
            while (cache.size > maxCacheSize && cacheOrder.isNotEmpty()) {
                val oldest = cacheOrder.removeAt(0)
                cache.remove(oldest)
                logger.debug("Evicted schematic '$oldest' from cache (LRU)")
            }
        }
    }
    
    private fun updateCacheOrder(name: String) {
        synchronized(cacheOrder) {
            cacheOrder.remove(name)
            cacheOrder.add(name) // Move to end (most recently used)
        }
    }
    
    private fun estimateMemoryUsage(handle: SchematicHandle): Long {
        // Rough estimation: each block = ~20 bytes
        val schematic = handle.schematic
        return (schematic.size().x().toLong() * schematic.size().y().toLong() * schematic.size().z().toLong() * 20L)
    }
    
    fun shutdown() {
        coroutineScope.cancel()
        clearCache()
        logger.info("SchematicService shutdown complete")
    }
}
