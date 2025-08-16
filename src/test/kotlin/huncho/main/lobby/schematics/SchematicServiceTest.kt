package huncho.main.lobby.schematics

import kotlinx.coroutines.runBlocking
import net.minestom.server.coordinate.Pos
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class SchematicServiceTest {
    
    @Test
    fun testPasteOptionsFromConfig() {
        val config = mapOf(
            "origin" to mapOf(
                "x" to 10.0,
                "y" to 64.0,
                "z" to 20.0
            ),
            "rotation" to 90,
            "mirror" to true,
            "paste_air" to false
        )
        
        val options = PasteOptions.fromConfig(config)
        
        assertEquals(Pos(10.0, 64.0, 20.0), options.origin)
        assertEquals(90, options.rotation)
        assertTrue(options.mirror)
        assertFalse(options.pasteAir)
    }
    
    @Test
    fun testPasteOptionsDefaults() {
        val config = emptyMap<String, Any>()
        val options = PasteOptions.fromConfig(config)
        
        assertNull(options.origin)
        assertEquals(0, options.rotation)
        assertFalse(options.mirror)
        assertFalse(options.pasteAir)
        assertTrue(options.async)
    }
    
    @Test
    fun testSchematicHandleCreation() {
        // This test would require a mock schematic, so we'll keep it simple
        val source = SchematicSource.Config
        assertNotNull(source)
        
        val fileSource = SchematicSource.File("test.schem")
        assertTrue(fileSource is SchematicSource.File)
        assertEquals("test.schem", (fileSource as SchematicSource.File).path)
    }
    
    @Test
    fun testPasteResultFactory() {
        // Test the failure case which doesn't need a handle
        val failureResult = PasteResult.failure("File not found")
        assertFalse(failureResult.success)
        assertEquals(0, failureResult.blocksPlaced)
        assertEquals("File not found", failureResult.error)
        assertNull(failureResult.handle)
        
        // Test that we can create a basic PasteResult
        val basicResult = PasteResult(true, 100, 500, null, null)
        assertTrue(basicResult.success)
        assertEquals(100, basicResult.blocksPlaced)
        assertEquals(500, basicResult.timeTaken)
    }
    
    @Test
    fun testCacheStats() {
        val stats = CacheStats(
            size = 5,
            maxSize = 10,
            hitCount = 20,
            missCount = 5,
            loadCount = 25
        )
        
        assertEquals(5, stats.size)
        assertEquals(10, stats.maxSize)
        assertEquals(0.8, stats.hitRate, 0.01) // 20/(20+5) = 0.8
    }
}
