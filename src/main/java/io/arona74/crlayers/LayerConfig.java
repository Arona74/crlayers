package io.arona74.crlayers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simplified configuration for layer generation
 * Loaded from layer_config.json
 */
public class LayerConfig {
    private static final Gson GSON = new Gson();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("crlayers");
    private static final String CONFIG_FILE = "layer_config.json";

    /**
     * Generation mode
     * BASIC: Simple linear gradient from edge (7→6→5→4→3→2→1)
     * EXTENDED: Extended gradients with 2x distance:
     *   - Uses 2x MAX_LAYER_DISTANCE for spreading
     *   - Gradual gradients with repeated values (7,7,6,6,5,5,4,4,3,3,2,2,1,1)
     * EXTREME: Extreme gradients with 3x distance:
     *   - Uses 3x MAX_LAYER_DISTANCE for spreading
     *   - Very gradual gradients with triple repeated values
     */
    public static GenerationMode MODE = GenerationMode.BASIC;

    /**
     * Maximum distance from edge to place layers (in blocks)
     * BASIC mode: Layers fade over this distance (7→6→5→4→3→2→1→0)
     * EXTENDED mode: Automatically uses 2x this distance with repeated value gradients
     * EXTREME mode: Automatically uses 3x this distance with triple repeated value gradients
     *
     * Recommended: 5-10 blocks (EXTENDED will use 10-20 blocks, EXTREME will use 15-30 blocks)
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
        BASIC,    // Linear gradient: 7→6→5→4→3→2→1
        EXTENDED, // Extended gradients with 2x distance: 7,7→6,6→5,5→4,4→3,3→2,2→1,1
        EXTREME   // Extreme gradients with 3x distance: 7,7,7→6,6,6→5,5,5→4,4,4→3,3,3→2,2,2→1,1,1
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

    /**
     * Smoothing priority mode
     * UP: Only apply smoothing if it increases the value (preserves extended/extreme gradients)
     * DOWN: Same as UP except near edge where it will use smoothed value even if it go down from existing value (traditional smoothing)
     */
    public static SmoothingPriority SMOOTHING_PRIORITY = SmoothingPriority.DOWN;

    public enum SmoothingPriority {
        UP,   // Always preserve higher gradient values (only smooth if it increases value)
        DOWN  // Traditional smoothing near edges and always up everywhere else
    }

    static {
        loadConfig();
    }

    /**
     * Load configuration from file
     */
    public static void loadConfig() {
        // Try external config first
        Path externalConfig = CONFIG_DIR.resolve(CONFIG_FILE);
        if (Files.exists(externalConfig)) {
            try {
                loadFromFile(externalConfig);
                CRLayers.LOGGER.info("Loaded layer config from: {}", externalConfig);
                return;
            } catch (IOException e) {
                CRLayers.LOGGER.error("Failed to load external config, using resource", e);
            }
        }

        // Fall back to resource
        try {
            loadFromResource();
            CRLayers.LOGGER.info("Loaded layer config from resource");
            // Create external config for user customization
            createExternalConfig();
        } catch (IOException e) {
            CRLayers.LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    private static void loadFromFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            parseConfig(reader);
        }
    }

    private static void loadFromResource() throws IOException {
        InputStream inputStream = LayerConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + CONFIG_FILE);
        }

        try (Reader reader = new InputStreamReader(inputStream)) {
            parseConfig(reader);
        }
    }

    private static void parseConfig(Reader reader) {
        JsonObject config = GSON.fromJson(reader, JsonObject.class);

        if (config.has("mode")) {
            try {
                MODE = GenerationMode.valueOf(config.get("mode").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                CRLayers.LOGGER.warn("Invalid mode in config, using default");
            }
        }

        if (config.has("max_layer_distance")) {
            MAX_LAYER_DISTANCE = config.get("max_layer_distance").getAsInt();
        }

        if (config.has("edge_height_threshold")) {
            EDGE_HEIGHT_THRESHOLD = config.get("edge_height_threshold").getAsInt();
        }

        if (config.has("smoothing_cycles")) {
            SMOOTHING_CYCLES = config.get("smoothing_cycles").getAsInt();
        }

        if (config.has("smoothing_rounding_mode")) {
            try {
                SMOOTHING_ROUNDING_MODE = RoundingMode.valueOf(
                    config.get("smoothing_rounding_mode").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                CRLayers.LOGGER.warn("Invalid rounding mode in config, using default");
            }
        }

        if (config.has("smoothing_priority")) {
            try {
                SMOOTHING_PRIORITY = SmoothingPriority.valueOf(
                    config.get("smoothing_priority").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                CRLayers.LOGGER.warn("Invalid smoothing priority in config, using default");
            }
        }
    }

    private static void createExternalConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            Path configPath = CONFIG_DIR.resolve(CONFIG_FILE);
            if (!Files.exists(configPath)) {
                // Copy from resource
                InputStream inputStream = LayerConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
                if (inputStream != null) {
                    Files.copy(inputStream, configPath);
                    CRLayers.LOGGER.info("Created external config: {}", configPath);
                }
            }
        } catch (IOException e) {
            CRLayers.LOGGER.error("Failed to create external config", e);
        }
    }

    /**
     * Reload configuration from file
     */
    public static void reload() {
        loadConfig();
    }

    /**
     * Save current configuration to external config file
     */
    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            Path configPath = CONFIG_DIR.resolve(CONFIG_FILE);

            JsonObject config = new JsonObject();
            config.addProperty("_comment", "Configuration for CR Layers generation behavior. Edit this file to customize layer generation.");
            config.addProperty("_comment_mode", "Generation mode: BASIC (linear gradients 7→6→5→4→3→2→1), EXTENDED (2x distance with repeated values 7,7,6,6,5,5,4,4,3,3,2,2,1,1), or EXTREME (3x distance with triple repeated values 7,7,7→6,6,6→5,5,5→4,4,4→3,3,3→2,2,2→1,1,1)");
            config.addProperty("mode", MODE.name());
            config.addProperty("_comment_max_layer_distance", "Maximum distance from edges to place layers. EXTENDED mode automatically uses 2x this value, EXTREME mode uses 3x. Recommended: 5-10");
            config.addProperty("max_layer_distance", MAX_LAYER_DISTANCE);
            config.addProperty("_comment_edge_height_threshold", "Minimum height difference to detect an edge. 1 = any height change, 2 = only 2+ block differences. Recommended: 1");
            config.addProperty("edge_height_threshold", EDGE_HEIGHT_THRESHOLD);
            config.addProperty("_comment_smoothing_cycles", "Number of smoothing passes after layer spreading. More cycles = smoother transitions. Recommended: 4-8");
            config.addProperty("smoothing_cycles", SMOOTHING_CYCLES);
            config.addProperty("_comment_smoothing_rounding_mode", "How to round averages during smoothing: UP (aggressive), DOWN (conservative), NEAREST (balanced)");
            config.addProperty("smoothing_rounding_mode", SMOOTHING_ROUNDING_MODE.name());
            config.addProperty("_comment_smoothing_priority", "Smoothing priority: UP (preserve higher gradient values), DOWN (traditional smoothing near edges)");
            config.addProperty("smoothing_priority", SMOOTHING_PRIORITY.name());

            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.newBuilder().setPrettyPrinting().create().toJson(config, writer);
            }

            CRLayers.LOGGER.info("Saved config to: {}", configPath);
        } catch (IOException e) {
            CRLayers.LOGGER.error("Failed to save config", e);
            throw new RuntimeException("Failed to save config: " + e.getMessage());
        }
    }
}