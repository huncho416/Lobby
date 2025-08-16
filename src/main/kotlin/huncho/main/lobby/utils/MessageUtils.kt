package huncho.main.lobby.utils

import huncho.main.lobby.LobbyPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minestom.server.entity.Player
import java.util.regex.Pattern

object MessageUtils {
    
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val colorPattern = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE)
    
    /**
     * Extension function to check permission asynchronously and execute code if player has permission
     */
    fun Player.hasPermissionAsync(permission: String, onResult: (Boolean) -> Unit) {
        LobbyPlugin.radiumIntegration.hasPermission(this.uuid, permission).thenAccept { hasPermission ->
            onResult(hasPermission)
        }
    }
    
    /**
     * Extension function to check permission and send no permission message if needed
     * Also checks for admin permission as a bypass
     */
    fun Player.checkPermissionAndExecute(permission: String, noPermissionMessage: String = "&cYou don't have permission to use this command.", action: () -> Unit) {
        hasPermissionAsync(permission) { hasSpecificPermission ->
            if (hasSpecificPermission) {
                action()
            } else {
                // Check for admin permission as fallback
                hasPermissionAsync("lobby.admin") { hasAdminPermission ->
                    if (hasAdminPermission) {
                        action()
                    } else {
                        sendMessage(this, noPermissionMessage)
                    }
                }
            }
        }
    }
    
    /**
     * Colorize a string using legacy color codes (&)
     */
    fun colorize(text: String): Component {
        return legacySerializer.deserialize(text)
    }
    
    /**
     * Colorize a string using MiniMessage format
     */
    fun miniMessage(text: String): Component {
        return miniMessage.deserialize(text)
    }
    
    /**
     * Send a message to a player
     */
    fun sendMessage(player: Player, message: String) {
        if (message.isNotEmpty()) {
            player.sendMessage(colorize(message))
        }
    }
    
    /**
     * Send a message to a player with prefix
     */
    fun sendMessage(player: Player, message: String, prefix: String) {
        if (message.isNotEmpty()) {
            player.sendMessage(colorize("$prefix$message"))
        }
    }
    
    /**
     * Send an action bar message to a player
     */
    fun sendActionBar(player: Player, message: String) {
        if (message.isNotEmpty()) {
            player.sendActionBar(colorize(message))
        }
    }
    
    /**
     * Send a title to a player
     */
    fun sendTitle(player: Player, title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20) {
        player.showTitle(
            net.kyori.adventure.title.Title.title(
                colorize(title),
                colorize(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis((fadeIn * 50).toLong()),
                    java.time.Duration.ofMillis((stay * 50).toLong()),
                    java.time.Duration.ofMillis((fadeOut * 50).toLong())
                )
            )
        )
    }
    
    /**
     * Strip color codes from a string
     */
    fun stripColor(text: String): String {
        return colorPattern.matcher(text).replaceAll("")
    }
    
    /**
     * Center text for a specific line length
     */
    fun centerText(text: String, lineLength: Int = 40): String {
        val stripped = stripColor(text)
        if (stripped.length >= lineLength) return text
        
        val spaces = (lineLength - stripped.length) / 2
        return " ".repeat(spaces) + text
    }
    
    /**
     * Format time duration
     */
    fun formatTime(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s"
        }
    }
    
    /**
     * Format number with commas
     */
    fun formatNumber(number: Int): String {
        return String.format("%,d", number)
    }
    
    /**
     * Create a progress bar
     */
    fun createProgressBar(current: Int, max: Int, length: Int = 20, completedChar: Char = '|', remainingChar: Char = '|'): String {
        val progress = (current.toDouble() / max.toDouble()).coerceIn(0.0, 1.0)
        val completed = (progress * length).toInt()
        val remaining = length - completed
        
        return "&a" + completedChar.toString().repeat(completed) + 
               "&c" + remainingChar.toString().repeat(remaining)
    }
    
    /**
     * Create a list of centered text lines
     */
    fun createCenteredList(lines: List<String>, lineLength: Int = 40): List<String> {
        return lines.map { centerText(it, lineLength) }
    }
    
    /**
     * Replace placeholders in a string
     */
    fun replacePlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (placeholder, value) ->
            result = result.replace("{$placeholder}", value)
        }
        return result
    }
    
    /**
     * Strip color codes from text
     */
    fun stripColors(text: String): String {
        return colorPattern.matcher(text).replaceAll("")
    }
    
    /**
     * Convert Component to plain string
     */
    fun componentToString(component: Component): String {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
    }
}
