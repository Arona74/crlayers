package io.arona74.crlayers;

/**
 * Simplified configuration for layer generation
 */
public class LayerConfig {
    
    /**
     * Generation mode
     * BASIC: Simple linear gradient from edge (7→6→5→4→3→2→1)
     * SMOOTH: Terrain-adaptive - flat areas get gradual steps (7,7,6,6,5,5,4,4,3,3,2,2,1,1)
     */
    public static GenerationMode MODE = GenerationMode.BASIC;
    
    /**
     * Maximum distance from edge to place layers (in blocks)
     * BASIC mode: Layers fade over this distance (7→6→5→4→3→2→1→0)
     * SMOOTH mode: Layers fade over 2x this distance with repeated values
     * 
     * Recommended: 5-10 blocks
     */
    public static int MAX_LAYER_DISTANCE = 7;
    
    /**
     * Minimum height difference to be considered an "edge"
     * 1 = Any height change creates an edge
     * 2 = Only 2+ block differences create edges
     * 
     * Recommended: 1 block
     */
    public static int EDGE_HEIGHT_THRESHOLD = 1;
    
    public enum GenerationMode {
        BASIC,  // Linear gradient: 7→6→5→4→3→2→1
        SMOOTH  // Gradual steps: 7,7→6,6→5,5→4,4→3,3→2,2→1,1 (for flat terrain)
    }

    /**
     * Number of smoothing cycles to run after layer spreading
     * More cycles = smoother transitions between layers
     * 
     * Recommended: 4-8 cycles
     */
    public static int SMOOTHING_CYCLES = 6;

    /**
     * How to round averages during smoothing
     */
    public static RoundingMode SMOOTHING_ROUNDING_MODE = RoundingMode.NEAREST;

    public enum RoundingMode {
        UP,      // Always round up (more aggressive layers)
        DOWN,    // Always round down (more conservative)  
        NEAREST  // Round to nearest integer
    }
}