package dev.deepcore.challenge.preview;

import dev.deepcore.logging.DeepCoreLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

/** Samples and transforms world blocks into preview hologram block data. */
public final class PreviewSampleService {
    private static final String PREVIEW_LAYERS_PATH = "challenge.preview_hologram_layers";
    private static final String PREVIEW_DEPTH_MULTIPLIER_PATH = "challenge.preview_hologram_depth_multiplier";
    private static final String PREVIEW_SOLID_FILL_PATH = "challenge.preview_hologram_solid_fill";
    private static final int PREVIEW_SAMPLE_SIZE = 32;

    private final JavaPlugin plugin;
    private final DeepCoreLogger log;

    private List<PreviewBlock> discoPreviewCsvCache;
    private boolean discoPreviewCsvLoadAttempted;

    /**
     * Creates a preview sample service.
     *
     * @param plugin plugin instance used for config and resource access
     * @param log    logger used for sampling and resource warnings
     */
    public PreviewSampleService(JavaPlugin plugin, DeepCoreLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    /**
     * Samples the run-world spawn surface into a filtered preview block list.
     *
     * @param runWorld run world to sample around spawn
     * @return filtered list of sampled preview blocks relative to preview origin
     */
    public List<PreviewBlock> sampleSpawnSurface(World runWorld) {
        int extraDepth = Math.max(0, Math.min(128, plugin.getConfig().getInt(PREVIEW_LAYERS_PATH, 5)));
        boolean solidFill = plugin.getConfig().getBoolean(PREVIEW_SOLID_FILL_PATH, false);
        int halfSize = PREVIEW_SAMPLE_SIZE / 2;
        int startX = runWorld.getSpawnLocation().getBlockX() - halfSize;
        int startZ = runWorld.getSpawnLocation().getBlockZ() - halfSize;

        int[][] renderTopY = new int[PREVIEW_SAMPLE_SIZE][PREVIEW_SAMPLE_SIZE];
        int[][] surfaceTopY = new int[PREVIEW_SAMPLE_SIZE][PREVIEW_SAMPLE_SIZE];
        boolean[][] treeColumn = new boolean[PREVIEW_SAMPLE_SIZE][PREVIEW_SAMPLE_SIZE];
        boolean[][] includeColumn = new boolean[PREVIEW_SAMPLE_SIZE][PREVIEW_SAMPLE_SIZE];
        double depthMultiplier = Math.max(0.0D, plugin.getConfig().getDouble(PREVIEW_DEPTH_MULTIPLIER_PATH, 2.0D));
        int maxTopDelta = Math.max(0, (int) Math.round(extraDepth * depthMultiplier));
        List<Integer> candidateSurfaceHeights = new ArrayList<>();
        int minSurfaceTopY = Integer.MAX_VALUE;
        int maxRenderTopY = Integer.MIN_VALUE;
        for (int x = 0; x < PREVIEW_SAMPLE_SIZE; x++) {
            for (int z = 0; z < PREVIEW_SAMPLE_SIZE; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int highestY = runWorld.getHighestBlockYAt(worldX, worldZ, HeightMap.WORLD_SURFACE);
                highestY = Math.max(runWorld.getMinHeight(), highestY);
                Material highestMaterial =
                        runWorld.getBlockAt(worldX, highestY, worldZ).getType();
                treeColumn[x][z] = isTreeCanopyMaterial(highestMaterial);

                int surfaceY = resolveSurfaceTopIgnoringLeaves(runWorld, worldX, worldZ, highestY);
                renderTopY[x][z] = highestY;
                surfaceTopY[x][z] = surfaceY;
                int topDelta = highestY - surfaceY;
                includeColumn[x][z] = treeColumn[x][z] || topDelta <= maxTopDelta;
                if (!includeColumn[x][z]) {
                    continue;
                }

                candidateSurfaceHeights.add(surfaceY);

                if (highestY > maxRenderTopY) {
                    maxRenderTopY = highestY;
                }
            }
        }

        if (candidateSurfaceHeights.isEmpty() || maxRenderTopY == Integer.MIN_VALUE) {
            return List.of();
        }

        candidateSurfaceHeights.sort(Integer::compareTo);
        int medianSurfaceY = candidateSurfaceHeights.get(candidateSurfaceHeights.size() / 2);
        int minAllowedDepthBaselineY = medianSurfaceY - maxTopDelta;
        for (int x = 0; x < PREVIEW_SAMPLE_SIZE; x++) {
            for (int z = 0; z < PREVIEW_SAMPLE_SIZE; z++) {
                if (!includeColumn[x][z]) {
                    continue;
                }

                if (!treeColumn[x][z] && surfaceTopY[x][z] < minAllowedDepthBaselineY) {
                    includeColumn[x][z] = false;
                    continue;
                }

                if (surfaceTopY[x][z] < minSurfaceTopY) {
                    minSurfaceTopY = surfaceTopY[x][z];
                }
            }
        }

        if (minSurfaceTopY == Integer.MAX_VALUE) {
            minSurfaceTopY = candidateSurfaceHeights.get(0);
        }

        int lowestRenderY = Math.max(runWorld.getMinHeight(), minSurfaceTopY - extraDepth);
        int height = (maxRenderTopY - lowestRenderY) + 1;
        BlockData[][][] sampledBlocks = new BlockData[PREVIEW_SAMPLE_SIZE][height][PREVIEW_SAMPLE_SIZE];

        for (int x = 0; x < PREVIEW_SAMPLE_SIZE; x++) {
            for (int z = 0; z < PREVIEW_SAMPLE_SIZE; z++) {
                if (!includeColumn[x][z]) {
                    continue;
                }

                int worldX = startX + x;
                int worldZ = startZ + z;
                int renderTop = renderTopY[x][z];
                int surfaceTop = surfaceTopY[x][z];
                BlockData surfaceTopBlockData =
                        runWorld.getBlockAt(worldX, surfaceTop, worldZ).getBlockData();
                if (surfaceTopBlockData.getMaterial().isAir()) {
                    continue;
                }

                BlockData fillData = surfaceTopBlockData;
                for (int worldY = maxRenderTopY; worldY >= lowestRenderY; worldY--) {
                    if (worldY > renderTop) {
                        continue;
                    }

                    Block block = runWorld.getBlockAt(worldX, worldY, worldZ);
                    BlockData blockData = block.getBlockData();
                    if (blockData.getMaterial().isAir()) {
                        if (solidFill) {
                            blockData = fillData;
                        } else {
                            continue;
                        }
                    } else {
                        fillData = blockData;
                    }

                    int relYIndex = worldY - lowestRenderY;
                    sampledBlocks[x][relYIndex][z] = toPreviewBlockData(blockData, block.isLiquid());
                }
            }
        }

        List<PreviewBlock> blocks = new ArrayList<>();
        double centerOffset = (PREVIEW_SAMPLE_SIZE - 1) / 2.0D;
        for (int x = 0; x < PREVIEW_SAMPLE_SIZE; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < PREVIEW_SAMPLE_SIZE; z++) {
                    BlockData blockData = sampledBlocks[x][y][z];
                    if (blockData == null) {
                        continue;
                    }

                    if (hasFullySolidCoverAbove(sampledBlocks, x, y, z)
                            && !hasNonFullySolidOnTop(sampledBlocks, x, y, z)
                            && isCompletelyOccludedHorizontally(sampledBlocks, x, y, z)) {
                        continue;
                    }

                    if (!isOutsideFacing(sampledBlocks, x, y, z)) {
                        continue;
                    }

                    double relX = x - centerOffset;
                    double relY = y;
                    double relZ = z - centerOffset;
                    blocks.add(new PreviewBlock(relX, relY, relZ, blockData.clone()));
                }
            }
        }

        return blocks;
    }

    /**
     * Builds the disco preview sample from bundled JSON grid resources.
     *
     * @return disco preview block sample parsed from bundled resources
     */
    public List<PreviewBlock> buildDiscoPreviewSample() {
        if (discoPreviewCsvCache != null) {
            return new ArrayList<>(discoPreviewCsvCache);
        }
        if (discoPreviewCsvLoadAttempted) {
            return List.of();
        }

        discoPreviewCsvLoadAttempted = true;

        InputStream gridJsonResource = plugin.getResource("disco/minecraftDot.grid.json");
        if (gridJsonResource == null) {
            gridJsonResource = plugin.getResource("minecraftDot.grid.json");
        }

        try (InputStream resource = gridJsonResource) {
            if (resource == null) {
                log.warn("Disco preview JSON 'minecraftDot.grid.json' was not found in plugin resources.");
                return List.of();
            }

            String jsonText = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            List<String[]> rows = parseDiscoGridRows(jsonText);
            int maxColumns = 0;
            for (String[] row : rows) {
                maxColumns = Math.max(maxColumns, row.length);
            }

            if (rows.isEmpty() || maxColumns <= 0) {
                log.warn("Disco preview JSON 'minecraftDot.grid.json' did not contain a usable grid.");
                return List.of();
            }

            double centerX = (maxColumns - 1) / 2.0D;
            double centerZ = (rows.size() - 1) / 2.0D;
            List<PreviewBlock> blocks = new ArrayList<>();

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                String[] cells = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                    if (colIndex >= cells.length) {
                        continue;
                    }

                    Material material = resolveDiscoPreviewCsvMaterial(cells[colIndex]);
                    if (material == null) {
                        continue;
                    }

                    double relX = colIndex - centerX;
                    double relZ = rowIndex - centerZ;
                    blocks.add(new PreviewBlock(relX, 0.0D, relZ, material.createBlockData()));
                }
            }

            discoPreviewCsvCache = blocks;
            return new ArrayList<>(discoPreviewCsvCache);
        } catch (IOException ex) {
            log.warn("Failed reading disco preview JSON 'minecraftDot.grid.json': " + ex.getMessage());
            return List.of();
        }
    }

    /**
     * Orders preview blocks by layer with randomized intra-layer build order.
     *
     * @param sample unsorted preview block sample
     * @return layer-ordered preview blocks with shuffled intra-layer order
     */
    public List<PreviewBlock> orderPreviewBlocksForBuild(List<PreviewBlock> sample) {
        Map<Integer, List<PreviewBlock>> byLayer = new HashMap<>();
        for (PreviewBlock block : sample) {
            int layer = (int) Math.round(block.relY());
            byLayer.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(block);
        }

        List<Integer> layers = new ArrayList<>(byLayer.keySet());
        layers.sort(Integer::compareTo);

        List<PreviewBlock> ordered = new ArrayList<>(sample.size());
        for (Integer layer : layers) {
            List<PreviewBlock> layerBlocks = new ArrayList<>(byLayer.get(layer));
            Collections.shuffle(layerBlocks);
            ordered.addAll(layerBlocks);
        }

        return ordered;
    }

    /**
     * Calculates eased block target count for preview spawn animation ticks.
     *
     * @param totalBlocks         total number of blocks in the preview sample
     * @param elapsedTicks        elapsed animation ticks since spawn start
     * @param totalAnimationTicks total ticks allocated for the full animation
     * @return eased count of blocks that should be visible at the current tick
     */
    public int calculateEasedSpawnTarget(int totalBlocks, int elapsedTicks, int totalAnimationTicks) {
        if (totalBlocks <= 0) {
            return 0;
        }
        if (totalAnimationTicks <= 0) {
            return totalBlocks;
        }

        int clampedTicks = Math.max(0, Math.min(elapsedTicks, totalAnimationTicks));
        double timelineProgress = (double) clampedTicks / (double) totalAnimationTicks;
        double acceleratedProgress = timelineProgress * timelineProgress;
        return Math.max(0, Math.min(totalBlocks, (int) Math.round(totalBlocks * acceleratedProgress)));
    }

    /**
     * Formats a biome enum key into a user-friendly title-cased name.
     *
     * @param biome biome to format
     * @return user-facing biome name
     */
    public String formatBiomeName(Biome biome) {
        if (biome == null) {
            return "Unknown";
        }

        String biomeKey = biome.getKey().getKey();
        if (biomeKey == null || biomeKey.isBlank()) {
            return "Unknown";
        }

        String[] words = biomeKey.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    private List<String[]> parseDiscoGridRows(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }

        int gridKeyIndex = jsonText.indexOf("\"grid\"");
        if (gridKeyIndex < 0) {
            return List.of();
        }

        int gridArrayStart = jsonText.indexOf('[', gridKeyIndex);
        if (gridArrayStart < 0) {
            return List.of();
        }

        List<String[]> rows = new ArrayList<>();
        List<String> currentRow = null;
        int depth = 0;

        for (int i = gridArrayStart; i < jsonText.length(); i++) {
            char ch = jsonText.charAt(i);

            if (ch == '[') {
                depth++;
                if (depth == 2) {
                    currentRow = new ArrayList<>();
                }
                continue;
            }

            if (ch == ']') {
                if (depth == 2 && currentRow != null) {
                    rows.add(currentRow.toArray(new String[0]));
                    currentRow = null;
                }
                depth--;
                if (depth <= 0) {
                    break;
                }
                continue;
            }

            if (ch == '"' && depth == 2 && currentRow != null) {
                StringBuilder token = new StringBuilder();
                boolean escaped = false;
                for (i = i + 1; i < jsonText.length(); i++) {
                    char valueChar = jsonText.charAt(i);
                    if (escaped) {
                        token.append(valueChar);
                        escaped = false;
                        continue;
                    }
                    if (valueChar == '\\') {
                        escaped = true;
                        continue;
                    }
                    if (valueChar == '"') {
                        break;
                    }
                    token.append(valueChar);
                }
                currentRow.add(token.toString());
            }
        }

        return rows;
    }

    private Material resolveDiscoPreviewCsvMaterial(String cellValue) {
        if (cellValue == null) {
            return null;
        }

        String token = cellValue.trim();
        if (token.isEmpty() || token.equalsIgnoreCase("air") || token.equalsIgnoreCase("minecraft:air")) {
            return null;
        }

        Material material = Material.matchMaterial(token);
        if (material != null) {
            return material;
        }

        if (token.startsWith("minecraft:")) {
            String stripped = token.substring("minecraft:".length());
            material = Material.matchMaterial(stripped);
            if (material != null) {
                return material;
            }
            material = Material.matchMaterial(stripped.toUpperCase(Locale.ROOT));
            if (material != null) {
                return material;
            }
        }

        return Material.matchMaterial(token.toUpperCase(Locale.ROOT));
    }

    private int resolveSurfaceTopIgnoringLeaves(World world, int worldX, int worldZ, int startY) {
        int minY = world.getMinHeight();
        for (int y = startY; y >= minY; y--) {
            Material material = world.getBlockAt(worldX, y, worldZ).getType();
            if (material.isAir()) {
                continue;
            }
            if (isTreeCanopyMaterial(material)) {
                continue;
            }
            return y;
        }
        return startY;
    }

    private boolean isTreeCanopyMaterial(Material material) {
        if (Tag.LEAVES.isTagged(material) || Tag.LOGS.isTagged(material)) {
            return true;
        }

        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_MUSHROOM_BLOCK")
                || material == Material.MUSHROOM_STEM;
    }

    private BlockData toPreviewBlockData(BlockData blockData, boolean isLiquidBlock) {
        Material material = blockData.getMaterial();
        if (isLiquidBlock || material == Material.WATER || material == Material.BUBBLE_COLUMN) {
            return Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
        }
        if (material == Material.LAVA) {
            return Material.ORANGE_STAINED_GLASS.createBlockData();
        }
        return blockData;
    }

    private boolean hasFullySolidCoverAbove(BlockData[][][] sampledBlocks, int x, int y, int z) {
        for (int above = y + 1; above < sampledBlocks[x].length; above++) {
            if (hasFullySolidSample(sampledBlocks, x, above, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonFullySolidOnTop(BlockData[][][] sampledBlocks, int x, int y, int z) {
        if (!hasSample(sampledBlocks, x, y + 1, z)) {
            return false;
        }

        Material aboveMaterial = sampledBlocks[x][y + 1][z].getMaterial();
        return !aboveMaterial.isOccluding();
    }

    private boolean isCompletelyOccludedHorizontally(BlockData[][][] sampledBlocks, int x, int y, int z) {
        return hasFullySolidSample(sampledBlocks, x - 1, y, z)
                && hasFullySolidSample(sampledBlocks, x + 1, y, z)
                && hasFullySolidSample(sampledBlocks, x, y, z - 1)
                && hasFullySolidSample(sampledBlocks, x, y, z + 1);
    }

    private boolean isOutsideFacing(BlockData[][][] sampledBlocks, int x, int y, int z) {
        return !hasFullySolidSample(sampledBlocks, x + 1, y, z)
                || !hasFullySolidSample(sampledBlocks, x - 1, y, z)
                || !hasFullySolidSample(sampledBlocks, x, y + 1, z)
                || !hasFullySolidSample(sampledBlocks, x, y - 1, z)
                || !hasFullySolidSample(sampledBlocks, x, y, z + 1)
                || !hasFullySolidSample(sampledBlocks, x, y, z - 1);
    }

    private boolean hasFullySolidSample(BlockData[][][] sampledBlocks, int x, int y, int z) {
        if (!hasSample(sampledBlocks, x, y, z)) {
            return false;
        }
        Material material = sampledBlocks[x][y][z].getMaterial();
        if (isTreeCanopyMaterial(material)) {
            return false;
        }
        return material.isOccluding();
    }

    private boolean hasSample(BlockData[][][] sampledBlocks, int x, int y, int z) {
        if (x < 0 || x >= sampledBlocks.length) {
            return false;
        }
        if (y < 0 || y >= sampledBlocks[x].length) {
            return false;
        }
        if (z < 0 || z >= sampledBlocks[x][y].length) {
            return false;
        }
        return sampledBlocks[x][y][z] != null;
    }

    /**
     * Represents a sampled preview block in coordinates relative to preview origin.
     */
    public record PreviewBlock(double relX, double relY, double relZ, BlockData blockData) {}
}
