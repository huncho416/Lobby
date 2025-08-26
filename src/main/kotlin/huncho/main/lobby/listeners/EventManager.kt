package huncho.main.lobby.listeners

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.listeners.player.*
import huncho.main.lobby.listeners.protection.*
import huncho.main.lobby.listeners.features.*
import huncho.main.lobby.protection.JoinItemMonitor
import net.minestom.server.MinecraftServer

class EventManager(private val plugin: LobbyPlugin) {
    
    private lateinit var joinItemMonitor: JoinItemMonitor
    
    fun registerAllListeners() {
        val eventHandler = MinecraftServer.getGlobalEventHandler()
        
        // Initialize the join item monitor
        joinItemMonitor = JoinItemMonitor(plugin)
        joinItemMonitor.startMonitor()
        
        // Player listeners
        eventHandler.addListener(PlayerJoinListener(plugin))
        eventHandler.addListener(PlayerLeaveListener(plugin))
        eventHandler.addListener(PlayerChatListener(plugin))
        eventHandler.addListener(PlayerMoveListener(plugin))
        
        // Protection listeners
        eventHandler.addListener(BlockProtectionListener(plugin))
        eventHandler.addListener(ItemProtectionListener(plugin, joinItemMonitor))
        eventHandler.addListener(InteractionProtectionListener(plugin))
        eventHandler.addListener(InventoryProtectionListener(plugin, joinItemMonitor))
        eventHandler.addListener(PortalProtectionListener(plugin))
        
        // Item pickup protection
        eventHandler.addListener(ItemPickupListener(plugin))
        
        // Robust hotbar protection for join items
        eventHandler.addListener(HotbarProtectionListener(plugin, joinItemMonitor))
        
        // Feature listeners
        eventHandler.addListener(DoubleJumpListener(plugin))
        eventHandler.addListener(LaunchPadListener(plugin))
        eventHandler.addListener(MenuListener(plugin))
        eventHandler.addListener(InventoryMenuListener(plugin))
        
        LobbyPlugin.logger.info("All event listeners registered successfully!")
        LobbyPlugin.logger.info("Join item protection monitor started - items will be automatically restored if moved")
    }
    
    fun shutdown() {
        if (::joinItemMonitor.isInitialized) {
            joinItemMonitor.stopMonitor()
        }
    }
}
