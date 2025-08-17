package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class HubCoreCommand(private val plugin: LobbyPlugin) : Command("hubcore") {
    
    init {
        setCondition { sender, _ ->
            when (sender) {
                is Player -> try {
                    plugin.radiumIntegration.hasPermission(sender.uuid, "lobby.admin").get() ||
                    plugin.radiumIntegration.hasPermission(sender.uuid, "hub.admin").get()
                } catch (e: Exception) {
                    false
                }
                else -> true // Allow console
            }
        }
        
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            sendHelpMessage(sender)
        }
        
        val reloadArg = ArgumentType.Literal("reload")
        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@addSyntax
            }
            
            plugin.radiumIntegration.hasPermission(sender.uuid, "hub.reload").thenAccept { hasPermission ->
                if (!hasPermission) {
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.no-permission")
                    MessageUtils.sendMessage(sender, message)
                    return@thenAccept
                }
                
                try {
                    plugin.reload()
                    val message = plugin.configManager.getString(plugin.configManager.messagesConfig, "commands.reload-success")
                    MessageUtils.sendMessage(sender, message)
                } catch (e: Exception) {
                    MessageUtils.sendMessage(sender, "&cFailed to reload plugin: ${e.message}")
                    LobbyPlugin.logger.error("Failed to reload plugin", e)
                }
            }
            
        }, reloadArg)
    }
    
    private fun sendHelpMessage(player: Player) {
        val prefix = plugin.configManager.getString(plugin.configManager.messagesConfig, "prefix")
        val messages = listOf(
            "&8&l&m-------------------&r &bLobby Plugin &8&l&m-------------------",
            "&b/hubcore reload &8- &7Reload the plugin configuration",
            "&b/spawn &8- &7Teleport to spawn",
            "&b/setspawn &8- &7Set the spawn location",
            "&b/fly &8- &7Toggle fly mode",
            "&b/build &8- &7Toggle build mode",
            "&b/playervis &8- &7Toggle player visibility",
            "&b/joinqueue <queue> &8- &7Join a server queue",
            "&b/leavequeue &8- &7Leave your current queue",
            "&b/opengadgetmenu &8- &7Open the gadgets menu",
            "&8&l&m------------------------------------------------"
        )
        
        messages.forEach { message ->
            MessageUtils.sendMessage(player, message)
        }
    }
}
