# CR Layers Generator

A Fabric mod for Minecraft 1.20.1 that automatically generates/remove Conquest Reforged terrain layers based on the surrounding blocks.

## Features

- **Automatic Layer Generation**: Generates layers that mimic natural terrain variation
- **Smart Height Detection**: Uses neighbor block heights to determine appropriate layer counts
- **Configurable Chunk Radius**: Generate layers in any chunk radius around the player
- **Conquest Reforged Integration**: Maps vanilla blocks to their Conquest Reforged layer equivalents

## Installation

1. Install Fabric Loader 0.15.11+ for Minecraft 1.20.1
2. Install Fabric API 0.92.2+
3. Install Conquest Reforged mod
4. Place this mod's JAR file in your mods folder

## Usage

Use the `/generateLayers` command in-game:

```
/generateLayers [chunkRadius]
```

- **chunk radius** (optional): The radius in chunk to generate layers (1-32). Default is 3.

Example:
```
/generateLayers 10
```
This will generate layers in a 10 chunk radius around your current position.

Use the `/removeLayers` command in-game:

```
/removeLayers [chunkRadius]
```

- **chunk radius** (optional): The radius in chunk to remove layers (1-32). Default is 3.

Example:
```
/removeLayers 10
```
This will remove layers in a 10 chunk radius around your current position.

## Configuration

### Block Mappings

The mod comes with default mappings for common terrain blocks. You can customize these mappings in `BlockMappingRegistry.java`:

```java
blockToLayerMapping.put(Blocks.GRASS_BLOCK, "conquest:grass_layer");
```

### Supported Blocks

Currently, layers can be generated on:
- Grass Block
- Dirt (all variants)
- Stone (all variants)
- Sand
- Red Sand
- Gravel
- Cobblestone
- And more terrain blocks...

You can add more blocks by modifying the `canGenerateLayersOn()` method in `LayerGenerator.java`.

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

- The mod requires OP permission level 2 to use the command
- Generation happens synchronously, so very large radii may cause temporary lag
- Make backups before using in important worlds
- The block mapping system is designed to be easily extensible
