package io.arona74.crlayers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class LayerGeneratorCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Main generation command
        dispatcher.register(CommandManager.literal("generateLayers")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("chunkRadius", IntegerArgumentType.integer(1, 20))
                .executes(LayerGeneratorCommand::executeWithRadius)
                .then(CommandManager.literal("--replacePlants")
                    .executes(LayerGeneratorCommand::executeWithRadiusAndPlants)))
            .then(CommandManager.literal("--replacePlants")
                .executes(LayerGeneratorCommand::executeDefaultWithPlants))
            .executes(LayerGeneratorCommand::executeDefault)
        );
        
        // Remove layers command
        dispatcher.register(CommandManager.literal("removeLayers")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("chunkRadius", IntegerArgumentType.integer(1, 20))
                .executes(LayerGeneratorCommand::executeRemoveWithRadius)
                .then(CommandManager.literal("--restorePlants")
                    .executes(LayerGeneratorCommand::executeRemoveWithRadiusAndPlants)))
            .then(CommandManager.literal("--restorePlants")
                .executes(LayerGeneratorCommand::executeRemoveDefaultWithPlants))
            .executes(LayerGeneratorCommand::executeRemoveDefault)
        );
        
        // Configuration commands
        dispatcher.register(CommandManager.literal("layerConfig")
            .requires(source -> source.hasPermissionLevel(2))
            
            .then(CommandManager.literal("show")
                .executes(LayerGeneratorCommand::showConfig))
            
            .then(CommandManager.literal("mode")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("basic");
                        builder.suggest("smooth");
                        return builder.buildFuture();
                    })
                    .executes(LayerGeneratorCommand::setMode)))
            
            .then(CommandManager.literal("distance")
                .then(CommandManager.argument("blocks", IntegerArgumentType.integer(3, 15))
                    .executes(LayerGeneratorCommand::setDistance)))
            
            .then(CommandManager.literal("edgeThreshold")
                .then(CommandManager.argument("blocks", IntegerArgumentType.integer(1, 5))
                    .executes(LayerGeneratorCommand::setEdgeThreshold)))
            
            .then(CommandManager.literal("smoothingCycles")
                .then(CommandManager.argument("cycles", IntegerArgumentType.integer(0, 20))
                    .executes(LayerGeneratorCommand::setSmoothingCycles)))
            
            .then(CommandManager.literal("roundingMode")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("up");
                        builder.suggest("down");
                        builder.suggest("nearest");
                        return builder.buildFuture();
                    })
                    .executes(LayerGeneratorCommand::setRoundingMode)))
        );

        // Debug log & file command
        dispatcher.register(CommandManager.literal("debugLayers")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 10))
            .executes(LayerGeneratorCommand::executeDebug))
            .executes(context -> executeDebug(context, 3))
        );
    }
    
    // ==================== Generation Commands ====================
    
    private static int executeDefault(CommandContext<ServerCommandSource> context) {
        return execute(context, 3, false);
    }
    
    private static int executeDefaultWithPlants(CommandContext<ServerCommandSource> context) {
        return execute(context, 3, true);
    }
    
    private static int executeWithRadius(CommandContext<ServerCommandSource> context) {
        int chunkRadius = IntegerArgumentType.getInteger(context, "chunkRadius");
        return execute(context, chunkRadius, false);
    }
    
    private static int executeWithRadiusAndPlants(CommandContext<ServerCommandSource> context) {
        int chunkRadius = IntegerArgumentType.getInteger(context, "chunkRadius");
        return execute(context, chunkRadius, true);
    }
    
    private static int execute(CommandContext<ServerCommandSource> context, int chunkRadius, boolean replacePlants) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        BlockPos playerPos = player.getBlockPos();
        
        String configInfo = String.format("Mode: %s, Distance: %d blocks, Edge threshold: %d", 
            LayerConfig.MODE.name(),
            LayerConfig.MAX_LAYER_DISTANCE,
            LayerConfig.EDGE_HEIGHT_THRESHOLD);
        source.sendFeedback(() -> Text.literal("§7" + configInfo), false);
        
        int blockRadius = chunkRadius * 16;
        String message = replacePlants ? 
            String.format("§eGenerating layers in %d chunk radius (~%d blocks) with plant replacement...", chunkRadius, blockRadius) :
            String.format("§eGenerating layers in %d chunk radius (~%d blocks)...", chunkRadius, blockRadius);
        source.sendFeedback(() -> Text.literal(message), false);
        
        LayerGenerator generator = new LayerGenerator(player.getServerWorld());
        
        try {
            long startTime = System.currentTimeMillis();
            int blocksGenerated = generator.generateLayers(playerPos, chunkRadius, replacePlants);
            long duration = System.currentTimeMillis() - startTime;
            
            if (blocksGenerated == 0) {
                source.sendFeedback(() -> Text.literal("§eNo layers generated. Possible reasons:"), false);
                source.sendFeedback(() -> Text.literal("§7- Terrain is completely flat (no height changes)"), false);
                source.sendFeedback(() -> Text.literal("§7- Standing on invalid blocks"), false);
                source.sendFeedback(() -> Text.literal("§7- Near water"), false);
                return 1;
            }
            
            source.sendFeedback(() -> Text.literal(
                String.format("§aGenerated %d layer blocks! §7(took %dms)", blocksGenerated, duration)), 
                true);
            return 1;
            
        } catch (Exception e) {
            CRLayers.LOGGER.error("Error generating layers", e);
            source.sendError(Text.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
    
    // ==================== Removal Commands ====================
    
    private static int executeRemoveDefault(CommandContext<ServerCommandSource> context) {
        return executeRemove(context, 1, false);
    }
    
    private static int executeRemoveDefaultWithPlants(CommandContext<ServerCommandSource> context) {
        return executeRemove(context, 1, true);
    }
    
    private static int executeRemoveWithRadius(CommandContext<ServerCommandSource> context) {
        int chunkRadius = IntegerArgumentType.getInteger(context, "chunkRadius");
        return executeRemove(context, chunkRadius, false);
    }
    
    private static int executeRemoveWithRadiusAndPlants(CommandContext<ServerCommandSource> context) {
        int chunkRadius = IntegerArgumentType.getInteger(context, "chunkRadius");
        return executeRemove(context, chunkRadius, true);
    }
    
    private static int executeRemove(CommandContext<ServerCommandSource> context, int chunkRadius, boolean restorePlants) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        BlockPos playerPos = player.getBlockPos();
        
        int blockRadius = chunkRadius * 16;
        String message = restorePlants ?
            String.format("§eRemoving layers in %d chunk radius (~%d blocks) and restoring plants...", chunkRadius, blockRadius) :
            String.format("§eRemoving layers in %d chunk radius (~%d blocks)...", chunkRadius, blockRadius);
        source.sendFeedback(() -> Text.literal(message), false);
        
        LayerGenerator generator = new LayerGenerator(player.getServerWorld());
        
        try {
            long startTime = System.currentTimeMillis();
            int blocksRemoved = generator.removeLayers(playerPos, chunkRadius, restorePlants);
            long duration = System.currentTimeMillis() - startTime;
            
            source.sendFeedback(() -> Text.literal(
                String.format("§aRemoved %d layer blocks! §7(took %dms)", blocksRemoved, duration)), 
                true);
            return 1;
            
        } catch (Exception e) {
            CRLayers.LOGGER.error("Error removing layers", e);
            source.sendError(Text.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
    
    // ==================== Configuration Commands ====================
    
    private static int showConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal("§6=== Layer Configuration ==="), false);
        source.sendFeedback(() -> Text.literal(String.format(
            "§eMode: §f%s", LayerConfig.MODE.name())), false);
        source.sendFeedback(() -> Text.literal(String.format(
            "§eMax Distance: §f%d blocks", LayerConfig.MAX_LAYER_DISTANCE)), false);
        source.sendFeedback(() -> Text.literal(String.format(
            "§eEdge Threshold: §f%d blocks", LayerConfig.EDGE_HEIGHT_THRESHOLD)), false);
        source.sendFeedback(() -> Text.literal(String.format(
            "§eSmoothing Cycles: §f%d", LayerConfig.SMOOTHING_CYCLES)), false);
        source.sendFeedback(() -> Text.literal(String.format(
            "§eRounding Mode: §f%s", LayerConfig.SMOOTHING_ROUNDING_MODE.name())), false);
        
        String modeDesc = LayerConfig.MODE == LayerConfig.GenerationMode.BASIC ?
            "§7Linear gradient: 7→6→5→4→3→2→1" :
            "§7Gradual steps: 7,7→6,6→5,5→4,4→3,3→2,2→1,1";
        source.sendFeedback(() -> Text.literal(modeDesc), false);
        
        return 1;
    }
    
    private static int setMode(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String modeName = StringArgumentType.getString(context, "mode").toLowerCase();
        
        LayerConfig.GenerationMode mode = switch (modeName) {
            case "basic" -> LayerConfig.GenerationMode.BASIC;
            case "smooth" -> LayerConfig.GenerationMode.SMOOTH;
            default -> {
                source.sendError(Text.literal("§cUnknown mode. Use: basic or smooth"));
                yield null;
            }
        };
        
        if (mode == null) return 0;
        
        LayerConfig.MODE = mode;
        source.sendFeedback(() -> Text.literal(
            String.format("§aSet mode to: §f%s", mode.name())), true);
        
        return 1;
    }
    
    private static int setDistance(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int value = IntegerArgumentType.getInteger(context, "blocks");
        
        LayerConfig.MAX_LAYER_DISTANCE = value;
        source.sendFeedback(() -> Text.literal(
            String.format("§aSet max layer distance to: §f%d blocks", value)), true);
        
        return 1;
    }
    
    private static int setEdgeThreshold(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int value = IntegerArgumentType.getInteger(context, "blocks");
        
        LayerConfig.EDGE_HEIGHT_THRESHOLD = value;
        source.sendFeedback(() -> Text.literal(
            String.format("§aSet edge threshold to: §f%d blocks", value)), true);
        
        return 1;
    }
    
    private static int setSmoothingCycles(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int value = IntegerArgumentType.getInteger(context, "cycles");
        
        LayerConfig.SMOOTHING_CYCLES = value;
        source.sendFeedback(() -> Text.literal(
            String.format("§aSet smoothing cycles to: §f%d", value)), true);
        
        return 1;
    }
    
    private static int setRoundingMode(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String modeName = StringArgumentType.getString(context, "mode").toLowerCase();
        
        LayerConfig.RoundingMode mode = switch (modeName) {
            case "up" -> LayerConfig.RoundingMode.UP;
            case "down" -> LayerConfig.RoundingMode.DOWN;
            case "nearest" -> LayerConfig.RoundingMode.NEAREST;
            default -> {
                source.sendError(Text.literal("§cUnknown rounding mode. Use: up, down, or nearest"));
                yield null;
            }
        };
        
        if (mode == null) return 0;
        
        LayerConfig.SMOOTHING_ROUNDING_MODE = mode;
        source.sendFeedback(() -> Text.literal(
            String.format("§aSet rounding mode to: §f%s", mode.name())), true);
        
        return 1;
    }

    private static int executeDebug(CommandContext<ServerCommandSource> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return executeDebug(context, radius);
    }

    private static int executeDebug(CommandContext<ServerCommandSource> context, int radius) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by players"));
            return 0;
        }
        
        BlockPos center = player.getBlockPos();
        source.sendFeedback(() -> Text.literal(
            String.format("§eGenerating debug data for %dx%d area around %s...", 
                radius*2+1, radius*2+1, center)), false);
        
        LayerGenerator generator = new LayerGenerator(player.getServerWorld());
        String result = generator.debugExport(center, radius);
        
        source.sendFeedback(() -> Text.literal("§aDebug data exported to: §f" + result), true);
        
        return 1;
    }
}