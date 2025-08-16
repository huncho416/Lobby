package huncho.main.lobby.commands.impl

import huncho.main.lobby.schematics.SchematicService
import huncho.main.lobby.schematics.PasteOptions
import huncho.main.lobby.integration.RadiumIntegration
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory

class SchematicCommand(
    private val schematicService: SchematicService,
    private val radiumIntegration: RadiumIntegration
) : Command("schem", "schematic") {
    
    private val logger = LoggerFactory.getLogger(SchematicCommand::class.java)
    
    private val subcommandArg = ArgumentType.Word("subcommand").apply {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.addEntry(SuggestionEntry("paste"))
            suggestion.addEntry(SuggestionEntry("reload"))
            suggestion.addEntry(SuggestionEntry("info"))
            suggestion.addEntry(SuggestionEntry("list"))
            suggestion.addEntry(SuggestionEntry("cache"))
        }
    }
    
    private val schematicNameArg = ArgumentType.Word("schematic")
    private val xArg = ArgumentType.Double("x")
    private val yArg = ArgumentType.Double("y") 
    private val zArg = ArgumentType.Double("z")
    private val rotationArg = ArgumentType.Integer("rotation").apply {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.addEntry(SuggestionEntry("0"))
            suggestion.addEntry(SuggestionEntry("90"))
            suggestion.addEntry(SuggestionEntry("180"))
            suggestion.addEntry(SuggestionEntry("270"))
        }
    }
    
    init {
        // Set up argument suggestions
        schematicNameArg.setSuggestionCallback { _, _, suggestion ->
            val schematics = schematicService.getLoadedSchematics().keys
            schematics.forEach { name ->
                suggestion.addEntry(SuggestionEntry(name))
            }
        }
        
        // /schem paste <schematic> [x] [y] [z] [rotation] [--mirror] [--air]
        addSyntax(this::executePaste, subcommandArg, schematicNameArg)
        addSyntax(this::executePasteWithPos, subcommandArg, schematicNameArg, xArg, yArg, zArg)
        addSyntax(this::executePasteWithPosRot, subcommandArg, schematicNameArg, xArg, yArg, zArg, rotationArg)
        
        // /schem reload
        addSyntax(this::executeReload, subcommandArg)
        
        // /schem info [schematic]
        addSyntax(this::executeInfo, subcommandArg)
        addSyntax(this::executeInfoSchematic, subcommandArg, schematicNameArg)
        
        // /schem list
        addSyntax(this::executeList, subcommandArg)
        
        // /schem cache
        addSyntax(this::executeCache, subcommandArg)
        
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get() ||
                    radiumIntegration.hasPermission(sender.uuid, "lobby.schematic").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Console always has permission
            }
        }
    }
    
    private fun executePaste(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "paste") return
        
        val schematicName = context.get(schematicNameArg)
        executePasteInternal(sender, schematicName, null, 0, false, false)
    }
    
    private fun executePasteWithPos(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "paste") return
        
        val schematicName = context.get(schematicNameArg)
        val x = context.get(xArg)
        val y = context.get(yArg)
        val z = context.get(zArg)
        
        executePasteInternal(sender, schematicName, net.minestom.server.coordinate.Pos(x, y, z), 0, false, false)
    }
    
    private fun executePasteWithPosRot(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "paste") return
        
        val schematicName = context.get(schematicNameArg)
        val x = context.get(xArg)
        val y = context.get(yArg)
        val z = context.get(zArg)
        val rotation = context.get(rotationArg)
        
        executePasteInternal(sender, schematicName, net.minestom.server.coordinate.Pos(x, y, z), rotation, false, false)
    }
    
    private fun executePasteInternal(
        sender: CommandSender,
        schematicName: String,
        position: net.minestom.server.coordinate.Pos?,
        rotation: Int,
        mirror: Boolean,
        pasteAir: Boolean
    ) {
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED))
            return
        }
        
        val instance = sender.instance
        if (instance == null) {
            sender.sendMessage(Component.text("You must be in a world to paste schematics!", NamedTextColor.RED))
            return
        }
        
        sender.sendMessage(Component.text("Pasting schematic '$schematicName'...", NamedTextColor.YELLOW))
        
        runBlocking {
            try {
                val options = PasteOptions(
                    origin = position ?: sender.position,
                    rotation = rotation,
                    mirror = mirror,
                    pasteAir = pasteAir,
                    async = true
                )
                
                val result = schematicService.pasteSchematic(instance, schematicName, options)
                
                if (result.success) {
                    sender.sendMessage(
                        Component.text("Successfully pasted schematic '$schematicName'!", NamedTextColor.GREEN)
                            .append(Component.text(" (${result.blocksPlaced} blocks in ${result.timeTaken}ms)", NamedTextColor.GRAY))
                    )
                    logger.info("Player ${sender.username} pasted schematic '$schematicName' at ${options.origin}")
                } else {
                    sender.sendMessage(Component.text("Failed to paste schematic: ${result.error}", NamedTextColor.RED))
                    logger.warn("Failed to paste schematic '$schematicName' for player ${sender.username}: ${result.error}")
                }
            } catch (e: Exception) {
                sender.sendMessage(Component.text("An error occurred while pasting the schematic!", NamedTextColor.RED))
                logger.error("Exception while pasting schematic '$schematicName' for player ${sender.username}", e)
            }
        }
    }
    
    private fun executeReload(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "reload") return
        
        sender.sendMessage(Component.text("Reloading schematics...", NamedTextColor.YELLOW))
        
        runBlocking {
            try {
                schematicService.reload()
                val stats = schematicService.getCacheStats()
                sender.sendMessage(
                    Component.text("Successfully reloaded schematics!", NamedTextColor.GREEN)
                        .append(Component.text(" (${stats.size} cached)", NamedTextColor.GRAY))
                )
                logger.info("Schematics reloaded by ${if (sender is Player) sender.username else "Console"}")
            } catch (e: Exception) {
                sender.sendMessage(Component.text("Failed to reload schematics: ${e.message}", NamedTextColor.RED))
                logger.error("Failed to reload schematics", e)
            }
        }
    }
    
    private fun executeInfo(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "info") return
        
        val stats = schematicService.getCacheStats()
        
        sender.sendMessage(Component.text("=== Schematic Service Info ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Cache Size: ${stats.size}/${stats.maxSize}", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Hit Rate: ${"%.2f".format(stats.hitRate * 100)}%", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Total Loads: ${stats.loadCount}", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Memory Usage: ${"%.2f".format(stats.totalMemoryUsage / 1024.0 / 1024.0)} MB", NamedTextColor.YELLOW))
    }
    
    private fun executeInfoSchematic(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "info") return
        
        val schematicName = context.get(schematicNameArg)
        val schematics = schematicService.getLoadedSchematics()
        val handle = schematics[schematicName]
        
        if (handle == null) {
            sender.sendMessage(Component.text("Schematic '$schematicName' not found or not loaded!", NamedTextColor.RED))
            return
        }
        
        val schematic = handle.schematic
        val timeSinceLoad = System.currentTimeMillis() - handle.loadTime
        
        sender.sendMessage(Component.text("=== Schematic Info: $schematicName ===", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("File: ${handle.file.name}", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Dimensions: ${schematic.size().x()}x${schematic.size().y()}x${schematic.size().z()}", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Source: ${handle.source}", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Loaded: ${timeSinceLoad / 1000}s ago", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("Blocks: ~${schematic.size().x() * schematic.size().y() * schematic.size().z()}", NamedTextColor.YELLOW))
    }
    
    private fun executeList(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "list") return
        
        val schematics = schematicService.getLoadedSchematics()
        
        if (schematics.isEmpty()) {
            sender.sendMessage(Component.text("No schematics are currently loaded.", NamedTextColor.YELLOW))
            return
        }
        
        sender.sendMessage(Component.text("=== Loaded Schematics (${schematics.size}) ===", NamedTextColor.GOLD))
        schematics.forEach { (name, handle) ->
            val schematic = handle.schematic
            val cached = if (schematicService.isCached(name)) " [CACHED]" else ""
            sender.sendMessage(
                Component.text("• $name", NamedTextColor.YELLOW)
                    .append(Component.text(" (${schematic.size().x()}x${schematic.size().y()}x${schematic.size().z()})$cached", NamedTextColor.GRAY))
            )
        }
    }
    
    private fun executeCache(sender: CommandSender, context: net.minestom.server.command.builder.CommandContext) {
        val subcommand = context.get(subcommandArg)
        if (subcommand != "cache") return
        
        sender.sendMessage(Component.text("Clearing schematic cache...", NamedTextColor.YELLOW))
        schematicService.clearCache()
        sender.sendMessage(Component.text("Schematic cache cleared!", NamedTextColor.GREEN))
        logger.info("Schematic cache cleared by ${if (sender is Player) sender.username else "Console"}")
    }
}
