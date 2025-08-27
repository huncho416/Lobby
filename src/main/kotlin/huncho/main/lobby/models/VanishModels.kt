package huncho.main.lobby.models

import java.util.UUID

/**
 * Different vanish levels for permission-based visibility
 * Matches Radium's VanishLevel enum exactly
 */
enum class VanishLevel(val level: Int, val displayName: String) {
    HELPER(1, "Helper"),         // Hidden from players
    MODERATOR(2, "Moderator"),   // Hidden from helpers and players  
    ADMIN(3, "Admin"),           // Hidden from all non-admins
    OWNER(4, "Owner");           // Hidden from everyone
    
    companion object {
        /**
         * Determine vanish level from player permissions
         */
        fun fromPermissions(permissions: Set<String>): VanishLevel {
            return when {
                permissions.contains("radium.vanish.owner") -> OWNER
                permissions.contains("radium.vanish.admin") -> ADMIN
                permissions.contains("radium.vanish.moderator") -> MODERATOR
                permissions.contains("radium.vanish.helper") -> HELPER
                else -> HELPER // Default to lowest level if they have vanish permission
            }
        }
        
        /**
         * Check if viewer can see vanished player based on levels
         */
        fun canSeeVanished(viewerLevel: VanishLevel, vanishedLevel: VanishLevel): Boolean {
            return viewerLevel.level >= vanishedLevel.level
        }
        
        /**
         * Check if viewer can see vanished player based on permissions
         */
        fun canSeeVanished(viewerPermissions: Set<String>, vanishedLevel: VanishLevel): Boolean {
            // Special permission to see all vanished players
            if (viewerPermissions.contains("radium.vanish.see")) {
                return true
            }
            
            val viewerLevel = fromPermissions(viewerPermissions)
            return canSeeVanished(viewerLevel, vanishedLevel)
        }
    }
}

/**
 * Represents vanish state data for a player
 * Matches Radium's VanishData class exactly
 */
data class VanishData(
    val playerId: UUID,
    val vanishTime: Long,
    val level: VanishLevel,
    val vanishedBy: UUID? = null, // Who vanished this player (for admin vanish)
    val reason: String? = null    // Reason for vanish (for admin vanish)
) {
    companion object {
        fun create(playerId: UUID, level: VanishLevel, vanishedBy: UUID? = null, reason: String? = null): VanishData {
            return VanishData(
                playerId = playerId,
                vanishTime = System.currentTimeMillis(),
                level = level,
                vanishedBy = vanishedBy,
                reason = reason
            )
        }
    }
    
    /**
     * Get duration vanished in milliseconds
     */
    fun getVanishDuration(): Long {
        return System.currentTimeMillis() - vanishTime
    }
    
    /**
     * Get formatted vanish duration
     */
    fun getFormattedDuration(): String {
        val duration = getVanishDuration()
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
