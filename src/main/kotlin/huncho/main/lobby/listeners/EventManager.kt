package huncho.main.lobby.listeners

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.listeners.player.*
import huncho.main.lobby.listeners.protection.*
import huncho.main.lobby.listeners.features.*
import net.minestom.server.MinecraftServer

class EventManager(private val plugin: LobbyPlugin) {
    
    fun registerAllListeners() {
        val eventHandler = MinecraftServer.getGlobalEventHandler()
        
        // Player listeners
        eventHandler.addListener(PlayerJoinListener(plugin))
        eventHandler.addListener(PlayerLeaveListener(plugin))
        eventHandler.addListener(PlayerChatListener(plugin))
        eventHandler.addListener(PlayerMoveListener(plugin))
        
        // Protection listeners
        eventHandler.addListener(BlockProtectionListener(plugin))
        eventHandler.addListener(ItemProtectionListener(plugin))
        eventHandler.addListener(InteractionProtectionListener(plugin))
        eventHandler.addListener(InventoryProtectionListener(plugin))
        eventHandler.addListener(PortalProtectionListener(plugin))
        
        // Additional protection for lobby items (swap protection only)
        eventHandler.addListener(HotbarProtectionListener(plugin))
        
        // Feature listeners
        eventHandler.addListener(DoubleJumpListener(plugin))
        eventHandler.addListener(LaunchPadListener(plugin))
        eventHandler.addListener(MenuListener(plugin))
        eventHandler.addListener(InventoryMenuListener(plugin))
        
        LobbyPlugin.logger.info("All event listeners registered successfully!")
    }
}
