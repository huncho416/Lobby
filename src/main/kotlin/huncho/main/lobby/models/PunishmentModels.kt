package huncho.main.lobby.models

import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * Represents a punishment (ban/tempban) from Radium
 */
data class Punishment(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("player_uuid")
    val playerUuid: UUID,
    
    @SerializedName("player_name")
    val playerName: String,
    
    @SerializedName("type")
    val type: PunishmentType,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("punisher_uuid")
    val punisherUuid: UUID?,
    
    @SerializedName("punisher_name")
    val punisherName: String?,
    
    @SerializedName("created_at")
    val createdAt: Long,
    
    @SerializedName("expires_at")
    val expiresAt: Long?, // null for permanent bans
    
    @SerializedName("active")
    val active: Boolean,
    
    @SerializedName("server")
    val server: String?,
    
    @SerializedName("ip")
    val ip: String?
) {
    /**
     * Check if this punishment is currently active
     */
    fun isCurrentlyActive(): Boolean {
        if (!active) return false
        
        // If no expiration, it's permanent and active
        val expiry = expiresAt ?: return true
        
        // Check if not yet expired
        return System.currentTimeMillis() < expiry
    }
    
    /**
     * Get the remaining time in milliseconds
     */
    fun getRemainingTime(): Long? {
        val expiry = expiresAt ?: return null
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }
    
    /**
     * Get a formatted reason for display
     */
    fun getDisplayReason(): String {
        return reason.ifEmpty { "No reason provided" }
    }
}

/**
 * Punishment types supported by Radium (matches Radium's exact enum)
 */
enum class PunishmentType {
    @SerializedName("BAN")
    BAN,
    
    @SerializedName("IP_BAN")
    IP_BAN,
    
    @SerializedName("MUTE")
    MUTE,
    
    @SerializedName("KICK")
    KICK,
    
    @SerializedName("WARN")
    WARN,
    
    @SerializedName("BLACKLIST")
    BLACKLIST;
    
    /**
     * Check if this punishment type should prevent joining
     */
    fun preventsJoin(): Boolean {
        return when (this) {
            BAN, IP_BAN, BLACKLIST -> true
            MUTE, KICK, WARN -> false
        }
    }
    
    /**
     * Check if this punishment type can have a duration
     */
    fun canHaveDuration(): Boolean {
        return when (this) {
            BAN, MUTE, WARN -> true
            IP_BAN, KICK, BLACKLIST -> false
        }
    }
}

/**
 * Represents a mute from Radium
 */
data class Mute(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("player_uuid")
    val playerUuid: UUID,
    
    @SerializedName("player_name")
    val playerName: String,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("muter_uuid")
    val muterUuid: UUID?,
    
    @SerializedName("muter_name")
    val muterName: String?,
    
    @SerializedName("created_at")
    val createdAt: Long,
    
    @SerializedName("expires_at")
    val expiresAt: Long?, // null for permanent mutes
    
    @SerializedName("active")
    val active: Boolean,
    
    @SerializedName("server")
    val server: String?
) {
    /**
     * Check if this mute is currently active
     */
    fun isCurrentlyActive(): Boolean {
        if (!active) return false
        
        // If no expiration, it's permanent and active
        val expiry = expiresAt ?: return true
        
        // Check if not yet expired
        return System.currentTimeMillis() < expiry
    }
    
    /**
     * Get the remaining time in milliseconds
     */
    fun getRemainingTime(): Long? {
        val expiry = expiresAt ?: return null
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }
    
    /**
     * Get a formatted reason for display
     */
    fun getDisplayReason(): String {
        return reason.ifEmpty { "No reason provided" }
    }
}

/**
 * Represents a real-time punishment update from Redis
 */
data class PunishmentUpdate(
    @SerializedName("action")
    val action: UpdateAction,
    
    @SerializedName("punishment")
    val punishment: Punishment,
    
    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Represents a real-time mute update from Redis
 */
data class MuteUpdate(
    @SerializedName("action")
    val action: UpdateAction,
    
    @SerializedName("mute")
    val mute: Mute,
    
    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Types of updates that can occur
 */
enum class UpdateAction {
    @SerializedName("CREATE")
    CREATE,
    
    @SerializedName("UPDATE")
    UPDATE,
    
    @SerializedName("DELETE")
    DELETE,
    
    @SerializedName("EXPIRE")
    EXPIRE
}

/**
 * Request DTO for issuing punishments via Radium's API
 * Matches the PunishmentRequest in Radium
 */
data class PunishmentRequest(
    @SerializedName("target")
    val target: String, // Player name or UUID
    
    @SerializedName("type")
    val type: String, // Punishment type (BAN, MUTE, etc.)
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("staffId")
    val staffId: String, // Staff UUID who is issuing the punishment
    
    @SerializedName("duration")
    val duration: String? = null, // Optional duration (e.g., "1d", "2h", "perm")
    
    @SerializedName("silent")
    val silent: Boolean = false,
    
    @SerializedName("clearInventory")
    val clearInventory: Boolean = false,
    
    @SerializedName("priority")
    val priority: Priority = Priority.NORMAL
) {
    enum class Priority {
        @SerializedName("LOW")
        LOW,
        
        @SerializedName("NORMAL")
        NORMAL,
        
        @SerializedName("HIGH")
        HIGH
    }
}

/**
 * Request DTO for revoking punishments via Radium's API
 * Matches the PunishmentRevokeRequest in Radium
 */
data class PunishmentRevokeRequest(
    @SerializedName("target")
    val target: String, // Player name or UUID
    
    @SerializedName("type")
    val type: String, // Punishment type to revoke (BAN, MUTE, etc.)
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("staffId")
    val staffId: String, // Staff UUID who is revoking the punishment
    
    @SerializedName("silent")
    val silent: Boolean = false
)

/**
 * Response DTO from Radium's punishment API
 * Matches the PunishmentResponse in Radium
 */
data class PunishmentResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("target")
    val target: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("staff")
    val staff: String,
    
    @SerializedName("message")
    val message: String
)

/**
 * Error response from Radium's API
 */
data class ErrorResponse(
    @SerializedName("error")
    val error: String,
    
    @SerializedName("message")
    val message: String
)
