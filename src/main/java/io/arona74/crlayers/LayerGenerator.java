package io.arona74.crlayers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.nio.file.Path;
import java.util.*;

/**
 * Simplified chunk-based layer generator using distance-from-edge algorithm
 */
public class LayerGenerator {
    private final ServerWorld world;
    private final BlockMappingRegistry mappingRegistry;
    private final PlantMappingRegistry plantMappingRegistry;
    private final PlantDataStorage plantDataStorage;
    
    public LayerGenerator(ServerWorld world) {
        this.world = world;
        this.mappingRegistry = new BlockMappingRegistry();
        this.plantMappingRegistry = new PlantMappingRegistry();
        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        this.plantDataStorage = new PlantDataStorage(worldDir);
    }
    
    /**
     * Generate layers in a chunk radius around center position
     */
    public int generateLayers(BlockPos center, int chunkRadius, boolean replacePlants) {
        CRLayers.LOGGER.info("Starting layer generation - Mode: {}, Max Distance: {}", 
            LayerConfig.MODE, LayerConfig.MAX_LAYER_DISTANCE);
        
        ChunkPos centerChunk = new ChunkPos(center);
        Set<ChunkPos> chunksToProcess = new HashSet<>();
        
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    chunksToProcess.add(chunkPos);
                }
            }
        }
        
        CRLayers.LOGGER.info("Processing {} loaded chunks", chunksToProcess.size());
        if (chunksToProcess.isEmpty()) {
            return 0;
        }
        
        // PHASE 1: Collect ALL surface heights (including holes) for edge detection
        Map<BlockPos, Integer> allSurfaceHeights = new HashMap<>();
        for (ChunkPos chunkPos : chunksToProcess) {
            collectAllSurfaceHeights(chunkPos, allSurfaceHeights);
        }
        
        CRLayers.LOGGER.info("Collected {} total surface positions for edge detection", allSurfaceHeights.size());
        
        // PHASE 2: Identify edges using complete heightmap
        Map<BlockPos, Integer> edges = identifyEdges(allSurfaceHeights);
        CRLayers.LOGGER.info("Identified {} edge positions", edges.size());
        
        if (edges.isEmpty()) {
            CRLayers.LOGGER.warn("No edges found");
            return 0;
        }
        
        // PHASE 3: Collect VALID placement positions (with filtering)
        Map<BlockPos, Integer> validSurfaceHeights = new HashMap<>();
        Map<BlockPos, Block> surfaceBlocks = new HashMap<>();
        
        for (ChunkPos chunkPos : chunksToProcess) {
            collectValidSurfaceData(chunkPos, validSurfaceHeights, surfaceBlocks);
        }
        
        CRLayers.LOGGER.info("Collected {} valid surface positions for layer placement", validSurfaceHeights.size());
        
        if (validSurfaceHeights.isEmpty()) {
            return 0;
        }
        
        // PHASE 4: Calculate layer values using FULL heightmap for distances
        // but only for VALID positions
        Map<BlockPos, Integer> layerValues = calculateLayerValues(
            validSurfaceHeights,  // Only calculate for valid positions
            allSurfaceHeights,    // But use full heightmap for distance calculations
            edges, 
            chunksToProcess
        );
        CRLayers.LOGGER.info("Calculated {} positions with layers", layerValues.size());
        
        // PHASE 5: Place layer blocks
        int blocksGenerated = placeAllLayers(layerValues, surfaceBlocks, replacePlants);
        
        if (replacePlants && blocksGenerated > 0) {
            plantDataStorage.save();
        }
        
        CRLayers.LOGGER.info("Generation complete! Generated {} blocks", blocksGenerated);
        return blocksGenerated;
    }

    /**
     * Collect ALL surface heights without filtering (for edge detection)
     */
    private void collectAllSurfaceHeights(ChunkPos chunkPos, Map<BlockPos, Integer> allHeights) {
        int foundInChunk = 0;
        int nullSurface = 0;
        Set<Integer> uniqueYLevels = new HashSet<>();
        
        for (int x = chunkPos.getStartX(); x <= chunkPos.getStartX() + 15; x++) {
            for (int z = chunkPos.getStartZ(); z <= chunkPos.getStartZ() + 15; z++) {
                BlockPos columnPos = new BlockPos(x, 0, z);
                BlockPos surfacePos = findSurfaceBlock(columnPos);
                
                if (surfacePos != null) {
                    allHeights.put(surfacePos, surfacePos.getY());
                    uniqueYLevels.add(surfacePos.getY());
                    foundInChunk++;
                } else {
                    nullSurface++;
                }
            }
        }
        
        CRLayers.LOGGER.info("Chunk {}: found {} surfaces ({} null), Y levels: {} (range: {} to {})", 
            chunkPos, foundInChunk, nullSurface, uniqueYLevels.size(),
            uniqueYLevels.isEmpty() ? 0 : Collections.min(uniqueYLevels),
            uniqueYLevels.isEmpty() ? 0 : Collections.max(uniqueYLevels));
    }

    /**
     * Collect VALID surface data with filtering (for layer placement)
     */
    private void collectValidSurfaceData(ChunkPos chunkPos, 
                                        Map<BlockPos, Integer> surfaceHeights, 
                                        Map<BlockPos, Block> surfaceBlocks) {
        for (int x = chunkPos.getStartX(); x <= chunkPos.getStartX() + 15; x++) {
            for (int z = chunkPos.getStartZ(); z <= chunkPos.getStartZ() + 15; z++) {
                BlockPos columnPos = new BlockPos(x, 0, z);
                BlockPos surfacePos = findSurfaceBlock(columnPos);
                
                if (surfacePos == null) continue;
                
                BlockState surfaceState = world.getBlockState(surfacePos);
                Block surfaceBlock = surfaceState.getBlock();
                
                // Apply filters for valid placement
                if (!canGenerateLayersOn(surfaceBlock)) continue;
                if (hasWaterNearby(surfacePos)) continue;
                
                surfaceHeights.put(surfacePos, surfacePos.getY());
                surfaceBlocks.put(surfacePos, surfaceBlock);
            }
        }
    }
    
    /**
     * Process a single chunk with neighbors
     */
    public int processChunk(ChunkPos chunkPos, boolean replacePlants) {
        if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
            return 0;
        }
        
        // Get neighbors for proper edge detection
        Set<ChunkPos> chunksToAnalyze = new HashSet<>();
        chunksToAnalyze.add(chunkPos);
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos neighbor = new ChunkPos(chunkPos.x + dx, chunkPos.z + dz);
                if (world.isChunkLoaded(neighbor.x, neighbor.z)) {
                    chunksToAnalyze.add(neighbor);
                }
            }
        }
        
        // Collect ALL surface heights (unfiltered) for edge detection
        Map<BlockPos, Integer> allSurfaceHeights = new HashMap<>();
        for (ChunkPos chunk : chunksToAnalyze) {
            collectAllSurfaceHeights(chunk, allSurfaceHeights);
        }
        
        // Identify edges across all analyzed chunks
        Map<BlockPos, Integer> edges = identifyEdges(allSurfaceHeights);
        
        // Collect valid surface data (filtered) for placement
        Map<BlockPos, Integer> validSurfaceHeights = new HashMap<>();
        Map<BlockPos, Block> surfaceBlocks = new HashMap<>();
        for (ChunkPos chunk : chunksToAnalyze) {
            collectValidSurfaceData(chunk, validSurfaceHeights, surfaceBlocks);
        }
        
        // Calculate layers only for center chunk
        // Pass BOTH validSurfaceHeights (where to place) and allSurfaceHeights (for distances)
        Set<ChunkPos> centerOnly = new HashSet<>();
        centerOnly.add(chunkPos);
        Map<BlockPos, Integer> layerValues = calculateLayerValues(
            validSurfaceHeights,   // Only calculate for valid positions
            allSurfaceHeights,     // But use full heightmap for distance calculations
            edges, 
            centerOnly
        );
        
        // Place layers
        int blocksGenerated = placeAllLayers(layerValues, surfaceBlocks, replacePlants);
        
        if (replacePlants && blocksGenerated > 0) {
            plantDataStorage.save();
        }
        
        return blocksGenerated;
    }
    
    /**
     * Identify edge positions - only HIGHER blocks at terrain changes
     * An edge is a block that has at least one neighbor LOWER than itself
     */
    private Map<BlockPos, Integer> identifyEdges(Map<BlockPos, Integer> surfaceHeights) {
        Map<BlockPos, Integer> edges = new HashMap<>();
        
        CRLayers.LOGGER.info("Starting edge detection on {} surface positions", surfaceHeights.size());
        
        // Create X,Z -> BlockPos lookup map for fast neighbor access
        Map<String, BlockPos> xzLookup = new HashMap<>();
        for (BlockPos pos : surfaceHeights.keySet()) {
            String key = pos.getX() + "," + pos.getZ();
            xzLookup.put(key, pos);
        }
        
        int edgesFound = 0;
        int positionsWithNeighbors = 0;
        int heightDifferencesFound = 0;
        int maxHeightDiffSeen = 0;
        
        for (Map.Entry<BlockPos, Integer> entry : surfaceHeights.entrySet()) {
            BlockPos pos = entry.getKey();
            int height = entry.getValue();
            
            // Check all 8 neighbors (cardinals + diagonals)
            int[] offsets = {-1, 0, 1};
            boolean hasLowerNeighbor = false;
            int validNeighborsChecked = 0;
            
            for (int dx : offsets) {
                for (int dz : offsets) {
                    if (dx == 0 && dz == 0) continue; // Skip self
                    
                    // Look up neighbor by X,Z coordinates
                    String neighborKey = (pos.getX() + dx) + "," + (pos.getZ() + dz);
                    BlockPos neighborPos = xzLookup.get(neighborKey);
                    
                    if (neighborPos == null) {
                        continue; // No surface block at this X,Z
                    }
                    
                    Integer neighborHeight = surfaceHeights.get(neighborPos);
                    if (neighborHeight == null) continue;
                    
                    validNeighborsChecked++;
                    int heightDiff = height - neighborHeight;
                    
                    // Track statistics
                    if (heightDiff != 0) {
                        heightDifferencesFound++;
                        maxHeightDiffSeen = Math.max(maxHeightDiffSeen, Math.abs(heightDiff));
                        
                        if (edgesFound == 0 && heightDifferencesFound <= 5) {
                            CRLayers.LOGGER.info("Height diff: {} (Y={}) vs {} (Y={}) = diff {}",
                                pos, height, neighborPos, neighborHeight, heightDiff);
                        }
                    }
                    
                    // If neighbor is LOWER by threshold or more, this block is an edge
                    if (heightDiff >= LayerConfig.EDGE_HEIGHT_THRESHOLD) {
                        hasLowerNeighbor = true;
                        
                        if (edgesFound < 10) {
                            CRLayers.LOGGER.info("EDGE at {} (Y={}) - neighbor {} lower (Y={}, diff={})", 
                                pos, height, neighborPos, neighborHeight, heightDiff);
                        }
                        break;
                    }
                }
                if (hasLowerNeighbor) break;
            }
            
            if (validNeighborsChecked > 0) {
                positionsWithNeighbors++;
            }
            
            if (hasLowerNeighbor) {
                edges.put(pos, height);
                edgesFound++;
            }
        }
        
        CRLayers.LOGGER.info("Edge detection: {} positions with neighbors, {} height diffs (max: {}), {} edges found", 
            positionsWithNeighbors, heightDifferencesFound, maxHeightDiffSeen, edges.size());
        
        return edges;
    }
    
    /**
     * Calculate layer values using Y-level pass approach
     * Processes each Y level separately to properly handle holes vs cliffs
     */
    private Map<BlockPos, Integer> calculateLayerValues(Map<BlockPos, Integer> validPositions,
                                                        Map<BlockPos, Integer> fullHeightmap,
                                                        Map<BlockPos, Integer> edges,
                                                        Set<ChunkPos> targetChunks) {
        Map<BlockPos, Integer> layerValues = new HashMap<>();
        
        // Group positions by Y level
        Map<Integer, List<BlockPos>> positionsByY = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : validPositions.entrySet()) {
            int y = entry.getValue();
            positionsByY.computeIfAbsent(y, k -> new ArrayList<>()).add(entry.getKey());
        }
        
        // Process each Y level
        for (Map.Entry<Integer, List<BlockPos>> yLevel : positionsByY.entrySet()) {
            int currentY = yLevel.getKey();
            List<BlockPos> positions = yLevel.getValue();
            
            CRLayers.LOGGER.info("Processing Y={} with {} positions", currentY, positions.size());
            
            // For each position at this Y level
            for (BlockPos pos : positions) {
                ChunkPos posChunk = new ChunkPos(pos);
                if (!targetChunks.contains(posChunk)) continue;
                
                // Skip edges
                if (edges.containsKey(pos)) continue;
                
                // Check if we're at the base of higher terrain
                int distanceToHigherTerrain = findDistanceToHigherTerrain(pos, currentY, fullHeightmap);
                
                if (distanceToHigherTerrain == Integer.MAX_VALUE) {
                    // No higher terrain nearby, skip
                    continue;
                }
                
                // Find how much space we have at this Y level before hitting an edge/hole
                int spaceAtSameLevel = findSpaceAtSameLevel(pos, currentY, fullHeightmap, edges);
                
                // Total available space
                int availableSpace = distanceToHigherTerrain + spaceAtSameLevel;
                
                // Calculate layers
                int layers = calculateAdaptiveBasicLayers(distanceToHigherTerrain, availableSpace);
                
                if (layers > 0) {
                    layerValues.put(pos, layers);
                    
                    if (availableSpace < 7 || distanceToHigherTerrain <= 2) {
                        CRLayers.LOGGER.info("Y={} pos {} - distToHigher={}, spaceAtLevel={}, total={} -> {} layers",
                            currentY, pos, distanceToHigherTerrain, spaceAtSameLevel, availableSpace, layers);
                    }
                }
            }
        }
        
        return layerValues;
    }

    /**
     * Find distance to nearest higher terrain (any Y level above current)
     */
    private int findDistanceToHigherTerrain(BlockPos pos, int currentY, Map<BlockPos, Integer> heightmap) {
        int minDistance = Integer.MAX_VALUE;
        int searchRadius = LayerConfig.MAX_LAYER_DISTANCE + 2;
        
        // Create XZ lookup for quick access
        Map<String, Integer> xzToHeight = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : heightmap.entrySet()) {
            String key = entry.getKey().getX() + "," + entry.getKey().getZ();
            xzToHeight.put(key, entry.getValue());
        }
        
        // Check all positions within search radius
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                String key = (pos.getX() + dx) + "," + (pos.getZ() + dz);
                Integer neighborHeight = xzToHeight.get(key);
                
                if (neighborHeight != null && neighborHeight > currentY) {
                    // Found higher terrain
                    int distance = Math.abs(dx) + Math.abs(dz); // Manhattan distance
                    minDistance = Math.min(minDistance, distance);
                }
            }
        }
        
        return minDistance;
    }

    /**
     * Find how much space exists at the same Y level before hitting edge/hole
     * This determines available space for gradient at this specific elevation
     */
    private int findSpaceAtSameLevel(BlockPos pos, int currentY, Map<BlockPos, Integer> heightmap, Map<BlockPos, Integer> edges) {
        int minDistanceToEdge = LayerConfig.MAX_LAYER_DISTANCE;
        int searchRadius = LayerConfig.MAX_LAYER_DISTANCE + 2;
        
        // Create XZ lookup
        Map<String, Integer> xzToHeight = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : heightmap.entrySet()) {
            String key = entry.getKey().getX() + "," + entry.getKey().getZ();
            xzToHeight.put(key, entry.getValue());
        }
        
        // Walk in all 8 directions at same Y level
        int[] offsets = {-1, 0, 1};
        
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) continue;
                
                // Walk in this direction until we hit:
                // 1. Missing terrain (hole/gap)
                // 2. Different Y level
                // 3. An edge block
                for (int dist = 1; dist <= searchRadius; dist++) {
                    int checkX = pos.getX() + (dx * dist);
                    int checkZ = pos.getZ() + (dz * dist);
                    String key = checkX + "," + checkZ;
                    
                    Integer checkHeight = xzToHeight.get(key);
                    
                    // Hit a hole/missing terrain
                    if (checkHeight == null) {
                        minDistanceToEdge = Math.min(minDistanceToEdge, dist);
                        break;
                    }
                    
                    // Hit different Y level (terrain rises or drops)
                    if (checkHeight != currentY) {
                        minDistanceToEdge = Math.min(minDistanceToEdge, dist);
                        break;
                    }
                    
                    // Hit an edge block
                    BlockPos checkPos = new BlockPos(checkX, currentY, checkZ);
                    if (edges.containsKey(checkPos)) {
                        minDistanceToEdge = Math.min(minDistanceToEdge, dist);
                        break;
                    }
                }
            }
        }
        
        return minDistanceToEdge;
    }

    /**
     * Calculate distance to nearest edge at HIGHER Y level only
     */
    private int calculateDistanceToHigherEdge(BlockPos pos, int posHeight, Map<BlockPos, Integer> edges) {
        int minDistance = Integer.MAX_VALUE;
        
        int searchRadius = LayerConfig.MAX_LAYER_DISTANCE + 2;
        
        for (Map.Entry<BlockPos, Integer> edgeEntry : edges.entrySet()) {
            BlockPos edge = edgeEntry.getKey();
            int edgeHeight = edgeEntry.getValue();
            
            // Only consider edges STRICTLY HIGHER
            if (edgeHeight <= posHeight) {
                continue;
            }
            
            // Quick bounds check
            if (Math.abs(edge.getX() - pos.getX()) > searchRadius) continue;
            if (Math.abs(edge.getZ() - pos.getZ()) > searchRadius) continue;
            
            int distance = Math.abs(edge.getX() - pos.getX()) + 
                        Math.abs(edge.getZ() - pos.getZ());
            
            if (distance < minDistance) {
                minDistance = distance;
                if (minDistance == 1) break;
            }
        }
        
        return minDistance;
    }

    /**
     * Calculate distance until terrain drops to lower Y level
     * Walk outward from position until we find terrain that's lower
     */
    private int calculateDistanceToLowerOrSameEdge(BlockPos pos, int posHeight, Map<BlockPos, Integer> surfaceHeights) {
        int minDistance = LayerConfig.MAX_LAYER_DISTANCE; // Default: assume full space available
        int searchRadius = LayerConfig.MAX_LAYER_DISTANCE + 2;
        
        // Create X,Z lookup for surfaceHeights
        Map<String, Integer> xzToHeight = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : surfaceHeights.entrySet()) {
            String key = entry.getKey().getX() + "," + entry.getKey().getZ();
            xzToHeight.put(key, entry.getValue());
        }
        
        // Check in all 8 directions for where terrain drops below current height
        int[] offsets = {-1, 0, 1};
        
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) continue;
                
                // Walk in this direction until we find lower terrain
                for (int dist = 1; dist <= searchRadius; dist++) {
                    int checkX = pos.getX() + (dx * dist);
                    int checkZ = pos.getZ() + (dz * dist);
                    String key = checkX + "," + checkZ;
                    
                    Integer checkHeight = xzToHeight.get(key);
                    
                    // If no terrain found (missing data), assume edge
                    if (checkHeight == null) {
                        minDistance = Math.min(minDistance, dist);
                        break;
                    }
                    
                    // If terrain is LOWER than current position, we found the drop
                    if (checkHeight < posHeight) {
                        minDistance = Math.min(minDistance, dist);
                        break;
                    }
                }
            }
        }
        
        return minDistance;
    }
    
    /**
     * BASIC mode with space awareness: compress gradient if needed
     */
    private int calculateAdaptiveBasicLayers(int distanceFromHigher, int availableSpace) {
        // If plenty of space (7+ blocks), use standard linear gradient
        if (availableSpace >= LayerConfig.MAX_LAYER_DISTANCE) {
            int layers = calculateBasicLayers(distanceFromHigher);
            CRLayers.LOGGER.info("Standard gradient: dist={}, space={}, layers={}", 
                distanceFromHigher, availableSpace, layers);
            return layers;
        }
        
        // Narrow area: use predefined compressed gradients
        if (distanceFromHigher > availableSpace) {
            CRLayers.LOGGER.info("Distance exceeded space: dist={}, space={}", 
                distanceFromHigher, availableSpace);
            return 0;
        }
        
        CRLayers.LOGGER.info("Adaptive gradient: dist={}, space={}", 
            distanceFromHigher, availableSpace);
        
        // Use custom gradients based on available space
        switch (availableSpace) {
            case 1:
                return 4;
                
            case 2:
                int[] gradient2 = {5, 2};
                return distanceFromHigher <= 2 ? gradient2[distanceFromHigher - 1] : 0;
                
            case 3:
                int[] gradient3 = {6, 4, 2};
                return distanceFromHigher <= 3 ? gradient3[distanceFromHigher - 1] : 0;
                
            case 4:
                int[] gradient4 = {7, 5, 3, 1};
                return distanceFromHigher <= 4 ? gradient4[distanceFromHigher - 1] : 0;
                
            case 5:
                int[] gradient5 = {6, 5, 4, 2, 1};
                return distanceFromHigher <= 5 ? gradient5[distanceFromHigher - 1] : 0;
                
            case 6:
                int[] gradient6 = {7, 6, 5, 3, 2, 1};
                return distanceFromHigher <= 6 ? gradient6[distanceFromHigher - 1] : 0;
                
            default:
                return calculateBasicLayers(distanceFromHigher);
        }
    }

    /**
     * SMOOTH mode with space awareness
     */
    private int calculateAdaptiveSmoothLayers(int distanceFromHigher, int availableSpace) {
        // If plenty of space, use standard smooth gradient (repeated values)
        if (availableSpace >= LayerConfig.MAX_LAYER_DISTANCE * 2) {
            return calculateSmoothLayers(distanceFromHigher);
        }
        
        // For narrow areas in smooth mode, use same compressed gradients as basic
        // but could extend with doubled values if needed
        return calculateAdaptiveBasicLayers(distanceFromHigher, availableSpace);
    }

    /**
     * Check if terrain is flat (few Y-level variations in chunks)
     */
    private boolean isTerrainFlat(Map<BlockPos, Integer> surfaceHeights, Set<ChunkPos> chunks) {
        Set<Integer> uniqueYLevels = new HashSet<>();
        
        for (Map.Entry<BlockPos, Integer> entry : surfaceHeights.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            if (chunks.contains(chunkPos)) {
                uniqueYLevels.add(entry.getValue());
            }
        }
        
        // If 3 or fewer Y levels, consider it flat
        return uniqueYLevels.size() <= 3;
    }
    
    /**
     * BASIC mode: Linear gradient 7→6→5→4→3→2→1
     */
    private int calculateBasicLayers(int distance) {
        if (distance == 0) return 7; // Edge itself
        if (distance > LayerConfig.MAX_LAYER_DISTANCE) return 0;
        
        return Math.max(1, 8 - distance); // 7, 6, 5, 4, 3, 2, 1
    }
    
    /**
     * SMOOTH mode: Gradual steps 7,7,6,6,5,5,4,4,3,3,2,2,1,1
     */
    private int calculateSmoothLayers(int distance) {
        if (distance == 0) return 7;
        if (distance > LayerConfig.MAX_LAYER_DISTANCE * 2) return 0;
        
        // Each layer value appears twice
        int layerValue = 8 - ((distance + 1) / 2);
        return Math.max(1, Math.min(7, layerValue));
    }
    
    /**
     * Place all layer blocks
     */
    private int placeAllLayers(Map<BlockPos, Integer> layerValues, 
                              Map<BlockPos, Block> surfaceBlocks, 
                              boolean replacePlants) {
        int blocksGenerated = 0;
        int plantsReplaced = 0;
        
        for (Map.Entry<BlockPos, Integer> entry : layerValues.entrySet()) {
            BlockPos surfacePos = entry.getKey();
            int layerCount = entry.getValue();
            Block surfaceBlock = surfaceBlocks.get(surfacePos);
            
            if (surfaceBlock == null) continue;
            
            Block layerBlock = mappingRegistry.getLayerBlock(surfaceBlock, layerCount);
            if (layerBlock == null) continue;
            
            BlockPos layerPos = surfacePos.up();
            BlockState existingState = world.getBlockState(layerPos);
            
            // Handle plant replacement
            if (replacePlants && !existingState.isAir()) {
                Block existingBlock = existingState.getBlock();
                
                if (plantMappingRegistry.isConquestPlant(existingBlock)) {
                    continue; // Skip existing conquest plants
                }
                
                if (plantMappingRegistry.isReplaceablePlant(existingBlock)) {
                    boolean isTallPlant = isTallPlant(existingBlock);
                    
                    if (isTallPlant) {
                        BlockPos upperPos = layerPos.up();
                        BlockState upperState = world.getBlockState(upperPos);
                        Block upperPlantBlock = upperState.getBlock();
                        plantDataStorage.storePlant(upperPos, upperPlantBlock);
                    }
                    
                    plantDataStorage.storePlant(layerPos, existingBlock);
                    if (isTallPlant) {
                        plantDataStorage.storeTallPlant(layerPos, true);
                    }
                    
                    // Place layer
                    BlockState layerState = layerBlock.getDefaultState();
                    if (layerState.contains(Properties.LAYERS)) {
                        layerState = layerState.with(Properties.LAYERS, Math.min(layerCount, 8));
                    }
                    world.setBlockState(layerPos, layerState);

                    // Place Conquest plant on top - matching the layer height beneath it
                    Block conquestPlant = plantMappingRegistry.getConquestPlant(existingBlock);
                    if (conquestPlant != null) {
                        BlockPos plantPos = layerPos.up();
                        
                        // Get the layer count we just placed (the block beneath the plant)
                        int plantLayerCount = layerCount;
                        
                        if (isTallPlant && isTallPlant(conquestPlant)) {
                            BlockState lowerState = conquestPlant.getDefaultState();
                            if (lowerState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                                lowerState = lowerState.with(Properties.DOUBLE_BLOCK_HALF, 
                                    DoubleBlockHalf.LOWER);
                            }
                            if (lowerState.contains(Properties.LAYERS)) {
                                lowerState = lowerState.with(Properties.LAYERS, Math.min(plantLayerCount, 8));
                            }
                            world.setBlockState(plantPos, lowerState);
                            
                            BlockPos upperPlantPos = plantPos.up();
                            if (world.getBlockState(upperPlantPos).isAir()) {
                                BlockState upperState = conquestPlant.getDefaultState();
                                if (upperState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                                    upperState = upperState.with(Properties.DOUBLE_BLOCK_HALF,
                                        DoubleBlockHalf.UPPER);
                                }
                                if (upperState.contains(Properties.LAYERS)) {
                                    upperState = upperState.with(Properties.LAYERS, Math.min(plantLayerCount, 8));
                                }
                                world.setBlockState(upperPlantPos, upperState);
                            }
                        } else {
                            if (world.getBlockState(plantPos).isAir()) {
                                BlockState plantState = conquestPlant.getDefaultState();
                                if (plantState.contains(Properties.LAYERS)) {
                                    plantState = plantState.with(Properties.LAYERS, Math.min(plantLayerCount, 8));
                                }
                                world.setBlockState(plantPos, plantState);
                            }
                        }
                        plantsReplaced++;
                    }
                    
                    blocksGenerated++;
                    continue;
                }
            }
            
            // Normal placement
            if (existingState.isAir() || existingState.isReplaceable()) {
                BlockState layerState = layerBlock.getDefaultState();
                
                if (layerState.contains(Properties.LAYERS)) {
                    layerState = layerState.with(Properties.LAYERS, Math.min(layerCount, 8));
                }
                
                world.setBlockState(layerPos, layerState);
                blocksGenerated++;
            }
        }
        
        if (plantsReplaced > 0) {
            CRLayers.LOGGER.info("Replaced {} plants with Conquest variants", plantsReplaced);
        }
        
        return blocksGenerated;
    }
    
    /**
     * Remove layers in chunk radius
     */
    public int removeLayers(BlockPos center, int chunkRadius, boolean restorePlants) {
        ChunkPos centerChunk = new ChunkPos(center);
        int blocksRemoved = 0;
        int plantsRestored = 0;
        
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                
                if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) continue;
                
                for (int x = chunkPos.getStartX(); x <= chunkPos.getStartX() + 15; x++) {
                    for (int z = chunkPos.getStartZ(); z <= chunkPos.getStartZ() + 15; z++) {
                        BlockPos columnPos = new BlockPos(x, 0, z);
                        BlockPos surfacePos = findSurfaceBlock(columnPos);
                        
                        if (surfacePos == null) continue;
                        
                        BlockPos layerPos = surfacePos.up();
                        BlockState layerState = world.getBlockState(layerPos);
                        Block layerBlock = layerState.getBlock();
                        
                        if (isLayerBlock(layerBlock)) {
                            BlockPos plantPos = layerPos.up();
                            BlockState plantState = world.getBlockState(plantPos);
                            Block plantBlock = plantState.getBlock();
                            
                            if (restorePlants) {
                                if (plantMappingRegistry.isConquestPlant(plantBlock)) {
                                    if (isTallPlant(plantBlock)) {
                                        BlockPos upperPos = plantPos.up();
                                        world.setBlockState(upperPos, Blocks.AIR.getDefaultState());
                                    }
                                    world.setBlockState(plantPos, Blocks.AIR.getDefaultState());
                                }
                                
                                if (plantDataStorage.hasPlantData(layerPos)) {
                                    Block originalPlant = plantDataStorage.getPlant(layerPos);
                                    boolean wasTallPlant = plantDataStorage.isTallPlant(layerPos);
                                    
                                    if (originalPlant != null) {
                                        if (wasTallPlant && isTallPlant(originalPlant)) {
                                            BlockState lowerState = originalPlant.getDefaultState();
                                            if (lowerState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                                                lowerState = lowerState.with(Properties.DOUBLE_BLOCK_HALF,
                                                    DoubleBlockHalf.LOWER);
                                            }
                                            world.setBlockState(layerPos, lowerState);
                                            
                                            BlockPos upperPos = layerPos.up();
                                            Block upperPlant = plantDataStorage.getPlant(upperPos);
                                            if (upperPlant != null) {
                                                BlockState upperState = upperPlant.getDefaultState();
                                                if (upperState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                                                    upperState = upperState.with(Properties.DOUBLE_BLOCK_HALF,
                                                        DoubleBlockHalf.UPPER);
                                                }
                                                world.setBlockState(upperPos, upperState);
                                                plantDataStorage.removePlant(upperPos);
                                            }
                                        } else {
                                            world.setBlockState(layerPos, originalPlant.getDefaultState());
                                        }
                                        
                                        plantDataStorage.removePlant(layerPos);
                                        plantDataStorage.removeTallPlantFlag(layerPos);
                                        plantsRestored++;
                                        blocksRemoved++;
                                        continue;
                                    }
                                }
                            }
                            
                            world.setBlockState(layerPos, Blocks.AIR.getDefaultState());
                            blocksRemoved++;
                        }
                    }
                }
            }
        }
        
        if (restorePlants && plantsRestored > 0) {
            plantDataStorage.save();
            CRLayers.LOGGER.info("Restored {} vanilla plants", plantsRestored);
        }
        
        CRLayers.LOGGER.info("Removed {} layer blocks", blocksRemoved);
        return blocksRemoved;
    }
    
    // ==================== Helper Methods ====================
    
    private boolean isLayerBlock(Block block) {
        if (block == Blocks.SNOW) return true;
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return blockId.contains("layer") || blockId.contains("slab");
    }
    
    private boolean isTallPlant(Block block) {
        return block == Blocks.TALL_GRASS ||
               block == Blocks.LARGE_FERN ||
               block == Blocks.SUNFLOWER ||
               block == Blocks.LILAC ||
               block == Blocks.ROSE_BUSH ||
               block == Blocks.PEONY ||
               block.getDefaultState().contains(Properties.DOUBLE_BLOCK_HALF);
    }
    
    private BlockPos findSurfaceBlock(BlockPos pos) {
        // Start from world surface heightmap
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
        
        // Walk down to find actual solid terrain (skip air, plants, leaves)
        while (surfacePos.getY() > world.getBottomY()) {
            BlockState state = world.getBlockState(surfacePos);
            Block block = state.getBlock();
            
            // Skip air
            if (state.isAir()) {
                surfacePos = surfacePos.down();
                continue;
            }
            
            // Skip leaves
            if (block.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                surfacePos = surfacePos.down();
                continue;
            }
            
            // Skip plants and flowers
            if (block.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) ||
                block.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS) ||
                block == Blocks.TALL_GRASS ||
                block == Blocks.GRASS ||
                block == Blocks.FERN ||
                block == Blocks.LARGE_FERN ||
                block == Blocks.DEAD_BUSH) {
                surfacePos = surfacePos.down();
                continue;
            }
            
            // Skip non-full blocks (slabs, stairs, etc.)
            if (!state.isFullCube(world, surfacePos)) {
                surfacePos = surfacePos.down();
                continue;
            }
            
            // Found solid surface block
            return surfacePos;
        }
        
        return null;
    }
    
    private boolean canGenerateLayersOn(Block block) {
        return block == Blocks.GRASS_BLOCK ||
               block == Blocks.DIRT ||
               block == Blocks.STONE ||
               block == Blocks.SAND ||
               block == Blocks.GRAVEL ||
               block == Blocks.COBBLESTONE ||
               block == Blocks.MOSSY_COBBLESTONE ||
               block == Blocks.ANDESITE ||
               block == Blocks.DIORITE ||
               block == Blocks.GRANITE ||
               block == Blocks.PODZOL ||
               block == Blocks.MYCELIUM ||
               block == Blocks.COARSE_DIRT ||
               block == Blocks.TERRACOTTA ||
               block.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.DIRT);
    }
    
    private boolean hasWaterNearby(BlockPos surfacePos) {
        BlockPos layerPos = surfacePos.up();
        BlockState stateAbove = world.getBlockState(layerPos);
        
        if (stateAbove.getBlock() == Blocks.WATER) return true;
        
        BlockState surfaceState = world.getBlockState(surfacePos);
        if (surfaceState.getBlock() == Blocks.WATER) return true;
        
        if (surfaceState.contains(Properties.WATERLOGGED) && surfaceState.get(Properties.WATERLOGGED)) {
            return true;
        }
        
        BlockPos[] adjacentPositions = {
            surfacePos.north(),
            surfacePos.south(),
            surfacePos.east(),
            surfacePos.west()
        };
        
        for (BlockPos adjacent : adjacentPositions) {
            BlockState adjacentState = world.getBlockState(adjacent);
            
            if (adjacentState.getBlock() == Blocks.WATER) return true;
            
            if (adjacentState.contains(Properties.WATERLOGGED) && adjacentState.get(Properties.WATERLOGGED)) {
                return true;
            }
            
            BlockState adjacentAbove = world.getBlockState(adjacent.up());
            if (adjacentAbove.getBlock() == Blocks.WATER) return true;
        }
        
        return false;
    }

    /**
     * Export debug data showing Y levels and layer values in a grid
     * @param center Center position
     * @param radius Radius in blocks (not chunks)
     * @return Path to exported file
     */
    public String debugExport(BlockPos center, int radius) {
        StringBuilder logOutput = new StringBuilder();
        StringBuilder fileOutput = new StringBuilder();
        
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        
        // Collect ALL surface heights (unfiltered) for edge detection
        Map<BlockPos, Integer> allSurfaceHeights = new HashMap<>();
        
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos columnPos = new BlockPos(x, 0, z);
                BlockPos surfacePos = findSurfaceBlock(columnPos);
                
                if (surfacePos != null) {
                    allSurfaceHeights.put(surfacePos, surfacePos.getY());
                }
            }
        }
        
        // Collect VALID surface heights (filtered) for placement
        Map<BlockPos, Integer> validSurfaceHeights = new HashMap<>();
        Map<BlockPos, Block> surfaceBlocks = new HashMap<>();
        
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos columnPos = new BlockPos(x, 0, z);
                BlockPos surfacePos = findSurfaceBlock(columnPos);
                
                if (surfacePos != null) {
                    BlockState surfaceState = world.getBlockState(surfacePos);
                    Block surfaceBlock = surfaceState.getBlock();
                    
                    if (canGenerateLayersOn(surfaceBlock) && !hasWaterNearby(surfacePos)) {
                        validSurfaceHeights.put(surfacePos, surfacePos.getY());
                        surfaceBlocks.put(surfacePos, surfaceBlock);
                    }
                }
            }
        }
        
        // Collect ACTUAL layers currently in world
        Map<BlockPos, Integer> actualLayers = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : validSurfaceHeights.entrySet()) {
            BlockPos surfacePos = entry.getKey();
            BlockPos layerPos = surfacePos.up();
            BlockState layerState = world.getBlockState(layerPos);
            
            if (isLayerBlock(layerState.getBlock())) {
                if (layerState.contains(Properties.LAYERS)) {
                    actualLayers.put(surfacePos, layerState.get(Properties.LAYERS));
                } else {
                    actualLayers.put(surfacePos, 1);
                }
            }
        }
        
        // Identify edges using FULL heightmap
        Map<String, BlockPos> xzLookup = new HashMap<>();
        for (BlockPos pos : allSurfaceHeights.keySet()) {
            String key = pos.getX() + "," + pos.getZ();
            xzLookup.put(key, pos);
        }
        
        Map<BlockPos, Integer> edges = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : allSurfaceHeights.entrySet()) {
            BlockPos pos = entry.getKey();
            int height = entry.getValue();
            
            boolean hasLowerNeighbor = false;
            int[] offsets = {-1, 0, 1};
            
            for (int dx : offsets) {
                for (int dz : offsets) {
                    if (dx == 0 && dz == 0) continue;
                    
                    String neighborKey = (pos.getX() + dx) + "," + (pos.getZ() + dz);
                    BlockPos neighborPos = xzLookup.get(neighborKey);
                    
                    if (neighborPos != null) {
                        Integer neighborHeight = allSurfaceHeights.get(neighborPos);
                        if (neighborHeight != null && (height - neighborHeight) >= LayerConfig.EDGE_HEIGHT_THRESHOLD) {
                            hasLowerNeighbor = true;
                            break;
                        }
                    }
                }
                if (hasLowerNeighbor) break;
            }
            
            if (hasLowerNeighbor) {
                edges.put(pos, height);
            }
        }
        
        // Calculate layer values for VALID positions using FULL heightmap for distances
        Map<BlockPos, Integer> layerValues = new HashMap<>();
        
        for (Map.Entry<BlockPos, Integer> entry : validSurfaceHeights.entrySet()) {
            BlockPos pos = entry.getKey();
            int posHeight = entry.getValue();
            
            if (edges.containsKey(pos)) continue;
            
            int distanceToHigher = calculateDistanceToHigherEdge(pos, posHeight, edges);
            if (distanceToHigher == Integer.MAX_VALUE) continue;
            
            // Use FULL heightmap for distance calculation
            int distanceToLower = calculateDistanceToLowerOrSameEdge(pos, posHeight, allSurfaceHeights);
            int availableSpace = distanceToHigher + distanceToLower;
            
            int layers = calculateAdaptiveBasicLayers(distanceToHigher, availableSpace);
            if (layers > 0) {
                layerValues.put(pos, layers);
            }
        }
        
        // Build output grids - use validSurfaceHeights for xzLookup in display
        Map<String, BlockPos> validXzLookup = new HashMap<>();
        for (BlockPos pos : validSurfaceHeights.keySet()) {
            String key = pos.getX() + "," + pos.getZ();
            validXzLookup.put(key, pos);
        }
        
        // Build output grids
        String header = String.format("Debug Export - Center: %s, Radius: %d blocks\n", center, radius);
        header += String.format("Area: X=%d to %d, Z=%d to %d\n", minX, maxX, minZ, maxZ);
        header += String.format("Mode: %s, Max Distance: %d\n\n", LayerConfig.MODE, LayerConfig.MAX_LAYER_DISTANCE);
        
        logOutput.append(header);
        fileOutput.append(header);
        
        // Y-Level Matrix - show VALID surfaces only
        String yMatrix = buildMatrix("Y-LEVELS (Surface Height)", minX, maxX, minZ, maxZ, 
            (x, z) -> {
                String key = x + "," + z;
                BlockPos pos = validXzLookup.get(key);
                if (pos == null) return "  --";
                Integer height = validSurfaceHeights.get(pos);
                if (height == null) return "  --";
                boolean isEdge = edges.containsKey(pos);
                return String.format(isEdge ? "[%2d]" : " %2d ", height);
            });
        
        logOutput.append(yMatrix).append("\n");
        fileOutput.append(yMatrix).append("\n");
        
        // ACTUAL Layer Values Matrix
        String actualMatrix = buildMatrix("ACTUAL LAYERS IN WORLD (0=no layers, E=edge)", minX, maxX, minZ, maxZ,
            (x, z) -> {
                String key = x + "," + z;
                BlockPos pos = validXzLookup.get(key);
                if (pos == null) return " --";
                if (edges.containsKey(pos)) return " E ";
                Integer layers = actualLayers.get(pos);
                if (layers == null) return " 0 ";
                return String.format(" %d ", layers);
            });
        
        logOutput.append(actualMatrix).append("\n");
        fileOutput.append(actualMatrix).append("\n");
        
        // CALCULATED Layer Values Matrix
        String layerMatrix = buildMatrix("CALCULATED LAYER VALUES (what would be placed)", minX, maxX, minZ, maxZ,
            (x, z) -> {
                String key = x + "," + z;
                BlockPos pos = validXzLookup.get(key);
                if (pos == null) return " --";
                if (edges.containsKey(pos)) return " E ";
                Integer layers = layerValues.get(pos);
                if (layers == null) return " 0 ";
                return String.format(" %d ", layers);
            });
        
        logOutput.append(layerMatrix).append("\n");
        fileOutput.append(layerMatrix).append("\n");
        
        // Log to console
        CRLayers.LOGGER.info("\n" + logOutput.toString());
        
        // Write to file
        try {
            Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
            Path debugFile = worldDir.resolve("crlayers_debug.txt");
            
            java.nio.file.Files.writeString(debugFile, fileOutput.toString());
            
            return debugFile.toString();
        } catch (Exception e) {
            CRLayers.LOGGER.error("Failed to write debug file", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Build a grid matrix string
     */
    private String buildMatrix(String title, int minX, int maxX, int minZ, int maxZ, 
                            java.util.function.BiFunction<Integer, Integer, String> cellProvider) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== ").append(title).append(" ===\n");
        
        // Header row (X coordinates)
        sb.append("     ");
        for (int x = minX; x <= maxX; x++) {
            sb.append(String.format("%4d", x));
        }
        sb.append("\n");
        
        // Data rows
        for (int z = minZ; z <= maxZ; z++) {
            sb.append(String.format("%4d ", z));
            for (int x = minX; x <= maxX; x++) {
                sb.append(cellProvider.apply(x, z));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}