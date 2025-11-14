# CR Layers Generator

A Fabric mod for Minecraft 1.20.1 that automatically generates/removes Conquest Reforged terrain layers based on the surrounding blocks.

## Features

- **Three Generation Modes**: BASIC (linear), EXTENDED (2x distance), EXTREME (3x distance)
- **Smart Height Detection**: Uses neighbor block heights to determine appropriate layer counts
- **Plant Handling**: Automatically replaces and restores vanilla plants with Conquest Reforged variants
- **Configurable System**: JSON-based configuration for easy customization
- **Preset System**: Quick configuration presets for different generation styles
- **Conquest Reforged Integration**: Maps vanilla blocks to their Conquest Reforged layer equivalents

## Installation

1. Install Fabric Loader 0.15.11+ for Minecraft 1.20.1
2. Install Fabric API 0.92.2+
3. Install Conquest Reforged mod
4. Place this mod's JAR file in your mods folder

## Commands

### Layer Generation
```
/generateLayers [chunkRadius]
```
Generate layers in the specified chunk radius (default: 3, max: 32). Automatically handles plant replacement.

```
/removeLayers [chunkRadius]
```
Remove layers in the specified chunk radius (default: 3, max: 32). Automatically restores original plants.

### Configuration
```
/layerConfig show                           # Display current configuration
/layerConfig mode <basic|extended|extreme>  # Set generation mode
/layerConfig distance <blocks>              # Set max layer distance (3-25)
/layerConfig edgeThreshold <blocks>         # Set edge height threshold (1-5)
/layerConfig smoothingCycles <cycles>       # Set smoothing cycles (0-20)
/layerConfig roundingMode <up|down|nearest> # Set rounding mode
/layerConfig smoothingPriority <up|down>    # Set smoothing priority
/layerConfig preset <basic|extended|extreme> # Apply preset configuration
/layerConfig reload                         # Reload config from file
```

### Presets
- **basic**: Mode: BASIC, Distance: 7, Smoothing: 6, Rounding: DOWN
- **extended**: Mode: EXTENDED, Distance: 14, Smoothing: 13, Rounding: DOWN
- **extreme**: Mode: EXTREME, Distance: 21, Smoothing: 20, Rounding: DOWN

## Configuration

Configuration files are automatically created in `config/crlayers/` on first run.

### Generation Modes

- **BASIC**: Linear gradients (7→6→5→4→3→2→1) with standard distance
- **EXTENDED**: Gradual gradients (7,7→6,6→5,5→...) with 2x distance
- **EXTREME**: Very gradual gradients (7,7,7→6,6,6→5,5,5→...) with 3x distance

### Configuration Files

**layer_config.json**: Main generation settings
- `mode`: Generation mode (BASIC, EXTENDED, EXTREME)
- `max_layer_distance`: Base distance for layer spreading
- `edge_height_threshold`: Minimum height difference to detect edges
- `smoothing_cycles`: Number of smoothing passes
- `smoothing_rounding_mode`: Rounding method (UP, DOWN, NEAREST)
- `smoothing_priority`: UP (preserve gradients) or DOWN (smooth near edges)

**block_mappings.json**: Vanilla to Conquest Reforged block mappings
```json
{
  "minecraft:grass_block": "conquest:grass_block_layer",
  "minecraft:dirt": "conquest:loamy_dirt_slab"
}
```

**plant_mappings.json**: Vanilla to Conquest Reforged plant mappings
```json
{
  "minecraft:grass": "conquest:grass",
  "minecraft:tall_grass": "conquest:tall_grass"
}
```

## Building from Source

1. Clone this repository
2. Open a terminal in the project directory
3. Run `./gradlew build` (Linux/Mac) or `gradlew.bat build` (Windows)
4. The compiled JAR will be in `build/libs/`

### Testing Without Conquest Reforged

The mod will fall back to placing snow layers if Conquest Reforged blocks are not found. This allows you to test the core generation logic without having CR installed.

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.15.11+
- Fabric API 0.92.2+
- Conquest Reforged (for actual layer blocks)

## License

MIT License - Feel free to modify and distribute

## Author

Arona74

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## Notes

- Requires OP permission level 2 to use commands
- Configuration changes via commands automatically save to file
- Generation happens synchronously, so very large radii may cause temporary lag
- Make backups before using in important worlds
- Plant replacement and restoration is handled automatically
