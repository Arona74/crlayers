package io.arona74.crlayers;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for mapping vanilla plants to their Conquest Reforged equivalents
 */
public class PlantMappingRegistry {
    private final Map<Block, String> vanillaToConquestPlant;
    private final Map<String, Block> conquestToVanillaPlant;
    
    public PlantMappingRegistry() {
        this.vanillaToConquestPlant = new HashMap<>();
        this.conquestToVanillaPlant = new HashMap<>();
        registerDefaultMappings();
    }
    
    /**
     * Register default plant mappings
     * TODO: Replace these with actual Conquest Reforged plant block IDs
     */
    private void registerDefaultMappings() {
        // Grass variants (Note: SHORT_GRASS is GRASS in 1.20.1)
        registerPlantMapping(Blocks.GRASS, "conquest:grass");
        registerPlantMapping(Blocks.TALL_GRASS, "conquest:tall_grass");
        registerPlantMapping(Blocks.FERN, "conquest:fern");
        registerPlantMapping(Blocks.LARGE_FERN, "conquest:large_fern");
        
        // Flowers
        registerPlantMapping(Blocks.DANDELION, "conquest:dandelion");
        registerPlantMapping(Blocks.POPPY, "conquest:poppy");
        registerPlantMapping(Blocks.BLUE_ORCHID, "conquest:blue_orchid");
        registerPlantMapping(Blocks.ALLIUM, "conquest:allium");
        registerPlantMapping(Blocks.AZURE_BLUET, "conquest:azure_bluet");
        registerPlantMapping(Blocks.RED_TULIP, "conquest:red_tulip");
        registerPlantMapping(Blocks.ORANGE_TULIP, "conquest:orange_tulip");
        registerPlantMapping(Blocks.WHITE_TULIP, "conquest:white_tulip");
        registerPlantMapping(Blocks.PINK_TULIP, "conquest:pink_tulip");
        registerPlantMapping(Blocks.OXEYE_DAISY, "conquest:oxeye_daisy");
        registerPlantMapping(Blocks.CORNFLOWER, "conquest:cornflower");
        registerPlantMapping(Blocks.LILY_OF_THE_VALLEY, "conquest:lily_of_the_valley");
        
        // Other plants
        registerPlantMapping(Blocks.DEAD_BUSH, "conquest:dead_bush");
        registerPlantMapping(Blocks.SUNFLOWER, "conquest:sunflower");
        registerPlantMapping(Blocks.LILAC, "conquest:lilac");
        registerPlantMapping(Blocks.ROSE_BUSH, "conquest:rose_bush");
        registerPlantMapping(Blocks.PEONY, "conquest:peony");
        //registerPlantMapping(Blocks.SWEET_BERRY_BUSH, "conquest:xxx");
        
        CRLayers.LOGGER.info("Registered {} plant mappings", vanillaToConquestPlant.size());
    }
    
    /**
     * Register a bidirectional plant mapping
     */
    private void registerPlantMapping(Block vanillaPlant, String conquestPlantId) {
        vanillaToConquestPlant.put(vanillaPlant, conquestPlantId);
        conquestToVanillaPlant.put(conquestPlantId, vanillaPlant);
    }
    
    /**
     * Get Conquest Reforged plant for a vanilla plant
     * @return Conquest plant block, or null if no mapping or block not found
     */
    public Block getConquestPlant(Block vanillaPlant) {
        String conquestId = vanillaToConquestPlant.get(vanillaPlant);
        if (conquestId == null) {
            return null; // No mapping
        }
        
        Identifier identifier = Identifier.tryParse(conquestId);
        if (identifier == null) {
            CRLayers.LOGGER.warn("Invalid conquest plant identifier: {}", conquestId);
            return null;
        }
        
        Block conquestPlant = Registries.BLOCK.get(identifier);
        if (conquestPlant == Blocks.AIR) {
            CRLayers.LOGGER.warn("Conquest plant not found: {}. Is Conquest Reforged installed?", conquestId);
            return null;
        }
        
        return conquestPlant;
    }
    
    /**
     * Get vanilla plant for a Conquest Reforged plant ID
     * Used when restoring plants
     */
    public Block getVanillaPlant(String conquestPlantId) {
        return conquestToVanillaPlant.get(conquestPlantId);
    }
    
    /**
     * Get vanilla plant for a Conquest Reforged plant block
     */
    public Block getVanillaPlant(Block conquestPlant) {
        String conquestId = Registries.BLOCK.getId(conquestPlant).toString();
        return conquestToVanillaPlant.get(conquestId);
    }
    
    /**
     * Check if a vanilla block is a plant that can be replaced
     */
    public boolean isReplaceablePlant(Block block) {
        return vanillaToConquestPlant.containsKey(block);
    }
    
    /**
     * Check if a block is a Conquest Reforged plant
     */
    public boolean isConquestPlant(Block block) {
        String blockId = Registries.BLOCK.getId(block).toString();
        return conquestToVanillaPlant.containsKey(blockId);
    }
}