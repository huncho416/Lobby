package huncho.main.lobby.commands.impl

import huncho.main.lobby.LobbyPlugin
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.entity.GameMode
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class TestGamemodeCommand : Command("testgamemode", "tgm") {

    init {
        val gamemodeArg = ArgumentType.Word("gamemode")
            .from("survival", "creative", "adventure", "spectator", "0", "1", "2", "3")

        addSyntax({ sender, context ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be used by players!", NamedTextColor.RED))
                return@addSyntax
            }

            val gamemodeString = context.get(gamemodeArg)
            val gamemode = parseGamemode(gamemodeString)

            if (gamemode == null) {
                sender.sendMessage(Component.text("Invalid gamemode: $gamemodeString", NamedTextColor.RED))
                return@addSyntax
            }

            // Test the HTTP API (simulating what Radium would do)
            try {
                // Create the request data
                val requestData = "playerId=${sender.uuid}&gamemode=$gamemodeString&staff=${sender.username}"
                
                // Make HTTP request to our own API to test it
                val url = "http://localhost:8081/api/gamemode"
                val process = ProcessBuilder("curl", "-X", "POST", "-d", requestData, url)
                    .redirectErrorStream(true)
                    .start()
                
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    sender.sendMessage(Component.text("HTTP API request sent successfully (testing)", NamedTextColor.GREEN))
                } else {
                    // Fallback to direct gamemode change
                    sender.gameMode = gamemode
                    sender.sendMessage(Component.text("Changed gamemode directly to ${gamemode.name} (HTTP API test failed)", NamedTextColor.YELLOW))
                }
            } catch (e: Exception) {
                // Fallback to direct gamemode change
                sender.gameMode = gamemode
                sender.sendMessage(Component.text("Changed gamemode directly to ${gamemode.name} (HTTP API unavailable)", NamedTextColor.YELLOW))
            }

        }, gamemodeArg)

        addSyntax({ sender, _ ->
            sender.sendMessage(Component.text("Usage: /testgamemode <survival|creative|adventure|spectator>", NamedTextColor.YELLOW))
        })
    }

    private fun parseGamemode(gamemodeString: String): GameMode? {
        return when (gamemodeString.lowercase()) {
            "survival", "0" -> GameMode.SURVIVAL
            "creative", "1" -> GameMode.CREATIVE
            "adventure", "2" -> GameMode.ADVENTURE
            "spectator", "3" -> GameMode.SPECTATOR
            else -> null
        }
    }
}
