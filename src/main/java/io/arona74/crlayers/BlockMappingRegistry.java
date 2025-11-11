package io.arona74.crlayers;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for mapping vanilla blocks to their Conquest Reforged layer equivalents
 */
public class BlockMappingRegistry {
    private final Map<Block, String> blockToLayerMapping;
    
    public BlockMappingRegistry() {
        this.blockToLayerMapping = new HashMap<>();
        registerDefaultMappings();
    }
    
    /**
     * Register default block to Conquest Reforged layer mappings
     * NOTE: These are placeholder mappings. You'll need to replace these with actual
     * Conquest Reforged block IDs once you know their naming convention.
     */
    private void registerDefaultMappings() {
        // Example mappings - REPLACE THESE WITH ACTUAL CONQUEST REFORGED BLOCK IDS
        // Format is typically: conquest:block_name_layer or similar
        
        // Grass blocks
        blockToLayerMapping.put(Blocks.GRASS_BLOCK, "conquest:grass_block_layer");
        
        // Dirt variants
        blockToLayerMapping.put(Blocks.DIRT, "conquest:loamy_dirt_slab");
        blockToLayerMapping.put(Blocks.COARSE_DIRT, "conquest:dirt_path_layer");
        blockToLayerMapping.put(Blocks.PODZOL, "conquest:vibrant_autumnal_forest_floor_layer");
        
        // Stone variants
        blockToLayerMapping.put(Blocks.STONE, "conquest:limestone_slab");
        blockToLayerMapping.put(Blocks.COBBLESTONE, "conquest:limestone_cobble_slab");
        blockToLayerMapping.put(Blocks.MOSSY_COBBLESTONE, "conquest:mossy_limestone_cobble_slab");
        
        // Stone types
        blockToLayerMapping.put(Blocks.ANDESITE, "conquest:andesite_slab");
        blockToLayerMapping.put(Blocks.DIORITE, "conquest:diorite_slab");
        blockToLayerMapping.put(Blocks.GRANITE, "conquest:granite_slab");
        
        // Sand and gravel
        blockToLayerMapping.put(Blocks.SAND, "conquest:sand_layer");
        blockToLayerMapping.put(Blocks.RED_SAND, "conquest:red_sand_layer");
        blockToLayerMapping.put(Blocks.GRAVEL, "conquest:gravel_layer");
        
        // Other terrain
        blockToLayerMapping.put(Blocks.MYCELIUM, "conquest:mycelium_layer");
        
        CRLayers.LOGGER.info("Registered {} block-to-layer mappings", blockToLayerMapping.size());
    }
    
    /**
     * Get the Conquest Reforged layer block for a given vanilla block
     * @param vanillaBlock The vanilla block
     * @param layerCount The number of layers (1-8, though this may not be used depending on CR's implementation)
     * @return The Conquest Reforged layer block, or null if no mapping exists or block not found
     */
    public Block getLayerBlock(Block vanillaBlock, int layerCount) {
        String layerBlockId = blockToLayerMapping.get(vanillaBlock);
        
        if (layerBlockId == null) {
            // No mapping found, fall back to snow layers for now
            CRLayers.LOGGER.debug("No layer mapping found for block: {}, using snow as fallback", 
                Registries.BLOCK.getId(vanillaBlock));
            return Blocks.SNOW;
        }
        
        // Try to get the block from registry
        Identifier identifier = Identifier.tryParse(layerBlockId);
        if (identifier == null) {
            CRLayers.LOGGER.warn("Invalid block identifier: {}", layerBlockId);
            return Blocks.SNOW; // Fallback
        }
        
        // Check if Conquest Reforged uses different blocks for different layer counts
        // Some implementations might use: conquest:grass_layer_1, conquest:grass_layer_2, etc.
        // If so, you can modify the identifier here before looking it up
        
        Block layerBlock = Registries.BLOCK.get(identifier);
        
        if (layerBlock == Blocks.AIR) {
            // Block not found in registry, might mean Conquest Reforged isn't loaded
            CRLayers.LOGGER.warn("Layer block not found in registry: {}. Is Conquest Reforged installed?", layerBlockId);
            return Blocks.SNOW; // Fallback to snow for testing
        }
        
        return layerBlock;
    }
    
    /**
     * Register a custom mapping
     * @param vanillaBlock The vanilla block
     * @param conquestLayerBlockId The Conquest Reforged layer block ID (e.g., "conquest:grass_layer")
     */
    public void registerMapping(Block vanillaBlock, String conquestLayerBlockId) {
        blockToLayerMapping.put(vanillaBlock, conquestLayerBlockId);
        CRLayers.LOGGER.info("Registered custom mapping: {} -> {}", 
            Registries.BLOCK.getId(vanillaBlock), conquestLayerBlockId);
    }
    
    /**
     * Check if a mapping exists for a block
     */
    public boolean hasMapping(Block block) {
        return blockToLayerMapping.containsKey(block);
    }
}
