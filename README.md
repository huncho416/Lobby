# Lobby Plugin

A Kotlin/Minestom-based lobby server with comprehensive features for managing player queues, scoreboards, protection, and world schematics.

## Features

### Core Features
- **Player Management**: Spawn handling, fly mode, build permissions
- **Queue System**: Multi-server queue management with priority and player limits
- **Protection System**: Anti-grief protection with configurable rules
- **World Lighting**: Always-day lighting and clear weather control

### Visual Features
- **Scoreboard**: Dynamic scoreboard with server information and player counts
- **Tab List**: Formatted tab list with Radium integration
- **Join Items**: Customizable hotbar items for lobby navigation

### Integration
- **Radium API**: Permission and user data integration
- **Velocity Support**: Proxy forwarding and secret authentication
- **MongoDB**: Player data persistence
- **Redis**: Optional caching layer

### Schematic System
- **Automatic Loading**: Load and paste lobby schematics on startup
- **Admin Commands**: Full schematic management via commands
- **Caching**: Intelligent caching with LRU eviction
- **Async Operations**: Non-blocking file I/O and world pasting

## Schematic Features

The lobby supports loading and pasting Sponge schematic files (.schem) with the following capabilities:

### Supported Formats
- Sponge Schematic Format v1, v2, v3
- WorldEdit/FAWE exported schematics

### Configuration
```yaml
schematics:
  enabled: true
  paste_on_startup: true
  async_operations: true
  cache_enabled: true
  cache_max_size: 10
  files:
    lobby:
      file: "schematics/lobby.schem"
      origin:
        x: 0
        y: 64
        z: 0
      rotation: 0  # 0, 90, 180, 270 degrees
      mirror: false
      paste_air: false
      enabled: true
```

### Admin Commands
- `/schem paste <name> [x] [y] [z] [rotation]` - Paste a schematic
- `/schem reload` - Reload schematics from config
- `/schem info [name]` - Show schematic information
- `/schem list` - List all loaded schematics
- `/schem cache` - Clear schematic cache

### Permissions
- `lobby.admin` - Full access to all features including schematics
- `lobby.schematic` - Basic schematic access

## Installation

1. Place the compiled JAR in your server directory
2. Configure `config/lobby/config.yml` with your settings
3. Place schematic files in `config/lobby/schematics/`
4. Start the server

## Dependencies

- **Minestom**: 2025.08.12-1.21.8
- **hollow-cube/schem**: 1.3.1 (for schematic support)
- **MongoDB Driver**: 4.11.1
- **Jackson**: 2.16.1 (for configuration)
- **OkHttp**: 4.12.0 (for Radium API)

## Configuration

The main configuration file is located at `config/lobby/config.yml`. Key sections include:

- `radium`: API configuration for user data
- `database`: MongoDB and Redis settings
- `spawn`: Lobby spawn location
- `schematics`: Schematic loading and pasting settings
- `queue`: Server queue configuration
- `protection`: Anti-grief settings
- `messages`: Customizable player messages

## Development

Built with Kotlin and Gradle. Requires Java 21+.

```bash
./gradlew build
./gradlew shadowJar
```

## Schematic Setup

1. Create the schematics directory: `config/lobby/schematics/`
2. Export your lobby build as a .schem file using WorldEdit
3. Place the file in the schematics directory
4. Configure the schematic in `config.yml`
5. Restart the server or use `/schem reload`

## License

MIT License
