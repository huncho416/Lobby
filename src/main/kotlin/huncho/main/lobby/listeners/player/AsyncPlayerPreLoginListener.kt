package huncho.main.lobby.listeners.player

import huncho.main.lobby.LobbyPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Handles pre-login checks including ban validation
 */
class AsyncPlayerPreLoginListener(private val plugin: LobbyPlugin) : EventListener<AsyncPlayerPreLoginEvent> {
    
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AsyncPlayerPreLoginListener::class.java)
    }
    
    override fun eventType(): Class<AsyncPlayerPreLoginEvent> = AsyncPlayerPreLoginEvent::class.java
    
    override fun run(event: AsyncPlayerPreLoginEvent): EventListener.Result {
        val playerUuid = event.playerUuid
        val username = event.username
        
        logger.debug("Checking pre-login for player $username ($playerUuid)")
        
        try {
            // Check if player is banned using the punishment service
            val punishmentService = plugin.punishmentService
            if (punishmentService != null) {
                val isBanned = punishmentService.isPlayerBanned(playerUuid).join()
                
                if (isBanned) {
                    // Get the active punishment for details
                    val punishment = punishmentService.getActivePunishment(playerUuid).join()
                    
                    val kickReason = if (punishment != null) {
                        buildBanMessage(punishment.getDisplayReason(), punishment.expiresAt)
                    } else {
                        Component.text("You are banned from this server!", NamedTextColor.RED)
                    }
                    
                    // TODO: Properly deny the login with the ban message
                    // For now, log the ban check - will implement proper denial after verifying Minestom API
                    logger.info("Player $username ($playerUuid) attempted to join but is banned: ${punishment?.getDisplayReason() ?: "Unknown reason"}")
                    // The ban enforcement will happen in the join event as a fallback
                    return EventListener.Result.SUCCESS
                }
            } else {
                logger.warn("PunishmentService not available, skipping ban check for $username")
            }
            
            // Allow login if no ban found
            logger.debug("Pre-login checks passed for player $username ($playerUuid)")
            
        } catch (e: Exception) {
            logger.error("Error during pre-login checks for player $username ($playerUuid)", e)
            // Allow login on error to avoid false positives
        }
        
        return EventListener.Result.SUCCESS
    }
    
    /**
     * Build a ban message with reason and expiration details
     */
    private fun buildBanMessage(reason: String, expiresAt: Long?): Component {
        val message = Component.text()
            .append(Component.text("You are banned from this server!", NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(reason, NamedTextColor.WHITE))
        
        if (expiresAt != null) {
            val remaining = expiresAt - System.currentTimeMillis()
            if (remaining > 0) {
                message.append(Component.newline())
                    .append(Component.text("Expires in: ", NamedTextColor.GRAY))
                    .append(Component.text(formatDuration(remaining), NamedTextColor.YELLOW))
            }
        } else {
            message.append(Component.newline())
                .append(Component.text("This ban is permanent.", NamedTextColor.DARK_RED))
        }
        
        return message.build()
    }
    
    /**
     * Format duration in a human-readable format
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
