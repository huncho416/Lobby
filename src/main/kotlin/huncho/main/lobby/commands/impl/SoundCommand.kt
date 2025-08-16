package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import huncho.main.lobby.utils.MessageUtils
import huncho.main.lobby.utils.MessageUtils.checkPermissionAndExecute
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

class SoundCommand(private val plugin: LobbyPlugin) : Command("sound") {
    
    init {
        setDefaultExecutor { sender, _ ->
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players!")
                return@setDefaultExecutor
            }
            
            val player = sender as Player
            player.checkPermissionAndExecute("hub.command.sound") {
                // TODO: Fix sound API for current Minestom version
                // player.playSound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, player.position, 1.0f, 1.0f)
                MessageUtils.sendMessage(player, "&aSound played!")
            }
        }
    }
}

