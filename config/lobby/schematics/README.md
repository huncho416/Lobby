# Schematics Directory

This directory contains schematic files (.schem) that can be loaded and pasted into the lobby.

## Supported Formats
- Sponge Schematic Format v1, v2, v3 (.schem files)
- WorldEdit schematic files exported from WorldEdit/FAWE

## File Organization
```
schematics/
├── lobby.schem          # Main lobby schematic
├── spawn/
│   ├── spawn-area.schem # Spawn area schematic
│   └── parkour.schem    # Parkour course
└── decorations/
    ├── fountain.schem   # Decorative fountain
    └── statue.schem     # Statue decoration
```

## Configuration
Schematics are configured in `config.yml` under the `schematics.files` section:

```yaml
schematics:
  enabled: true
  paste_on_startup: true
  files:
    lobby:
      file: "schematics/lobby.schem"
      origin:
        x: 0
        y: 64
        z: 0
      rotation: 0
      mirror: false
      paste_air: false
      enabled: true
```

## Commands
- `/schem paste <name>` - Paste a schematic at your location
- `/schem paste <name> <x> <y> <z>` - Paste a schematic at specific coordinates
- `/schem info <name>` - Show information about a schematic
- `/schem list` - List all loaded schematics
- `/schem reload` - Reload all schematics from config
- `/schem cache` - Clear the schematic cache

## Permissions
- `lobby.admin` - Full access to all schematic commands
- `lobby.schematic` - Basic schematic access

## Notes
- Place your .schem files in this directory
- Schematics are cached for performance
- Large schematics are pasted asynchronously to avoid server lag
- Air blocks are skipped by default (configurable)
