package dev.deepcore.challenge.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.seeseemelk.mockbukkit.MockBukkit;
import dev.deepcore.logging.DeepCoreLogger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewSampleServiceTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PreviewSampleService createService() {
        return new PreviewSampleService(mock(JavaPlugin.class), mock(DeepCoreLogger.class));
    }

    @Test
    void calculateEasedSpawnTarget_respectsBoundsAndEndpoints() {
        PreviewSampleService service = createService();
        int totalBlocks = 1000;
        int totalTicks = 60;

        assertEquals(0, service.calculateEasedSpawnTarget(totalBlocks, 0, totalTicks));
        assertEquals(totalBlocks, service.calculateEasedSpawnTarget(totalBlocks, totalTicks, totalTicks));
        assertEquals(totalBlocks, service.calculateEasedSpawnTarget(totalBlocks, totalTicks + 100, totalTicks));
        assertEquals(0, service.calculateEasedSpawnTarget(totalBlocks, -10, totalTicks));
    }

    @Test
    void calculateEasedSpawnTarget_isMonotonicAcrossTimeline() {
        PreviewSampleService service = createService();
        int totalBlocks = 500;
        int totalTicks = 60;

        int previous = -1;
        for (int tick = 0; tick <= totalTicks; tick++) {
            int current = service.calculateEasedSpawnTarget(totalBlocks, tick, totalTicks);
            assertTrue(current >= previous, "Spawn target must not decrease at tick " + tick);
            previous = current;
        }
    }

    @Test
    void calculateEasedSpawnTarget_zeroOrInvalidInputsHandled() {
        PreviewSampleService service = createService();

        assertEquals(0, service.calculateEasedSpawnTarget(0, 10, 60));
        assertEquals(0, service.calculateEasedSpawnTarget(-5, 10, 60));
        assertEquals(42, service.calculateEasedSpawnTarget(42, 1, 0));
    }

    @Test
    void formatBiomeName_nullInputReturnsUnknown() {
        PreviewSampleService service = createService();
        assertEquals("Unknown", service.formatBiomeName(null));
    }

    @Test
    void orderingAndBiomeFormatting_coverUtilityPaths() {
        PreviewSampleService service = createService();

        List<PreviewSampleService.PreviewBlock> sample = List.of(
                new PreviewSampleService.PreviewBlock(0.0D, 2.0D, 0.0D, mock(org.bukkit.block.data.BlockData.class)),
                new PreviewSampleService.PreviewBlock(0.0D, 1.0D, 0.0D, mock(org.bukkit.block.data.BlockData.class)),
                new PreviewSampleService.PreviewBlock(1.0D, 1.0D, 0.0D, mock(org.bukkit.block.data.BlockData.class)));

        List<PreviewSampleService.PreviewBlock> ordered = service.orderPreviewBlocksForBuild(sample);
        assertEquals(3, ordered.size());
        assertTrue(ordered.get(0).relY() <= ordered.get(1).relY());
        assertTrue(ordered.get(1).relY() <= ordered.get(2).relY());
    }

    @Test
    void buildDiscoPreviewSample_missingResources_returnsEmptyAndSkipsRepeatedLoads() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getResource("disco/minecraftDot.grid.json")).thenReturn(null);
        when(plugin.getResource("minecraftDot.grid.json")).thenReturn(null);

        PreviewSampleService service = new PreviewSampleService(plugin, log);

        assertTrue(service.buildDiscoPreviewSample().isEmpty());
        assertTrue(service.buildDiscoPreviewSample().isEmpty());

        verify(plugin, times(1)).getResource("disco/minecraftDot.grid.json");
        verify(plugin, times(1)).getResource("minecraftDot.grid.json");
        verify(log).warn(contains("was not found"));
    }

    @Test
    void buildDiscoPreviewSample_usesFallbackResource_andCachesParsedResult() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        String json = "{\"grid\":[[\"air\",\"minecraft:air\"],[\"AIR\",\" \"]]}";

        when(plugin.getResource("disco/minecraftDot.grid.json")).thenReturn(null);
        when(plugin.getResource("minecraftDot.grid.json"))
                .thenReturn(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        PreviewSampleService service = new PreviewSampleService(plugin, log);

        assertTrue(service.buildDiscoPreviewSample().isEmpty());
        assertTrue(service.buildDiscoPreviewSample().isEmpty());

        verify(plugin, times(1)).getResource("disco/minecraftDot.grid.json");
        verify(plugin, times(1)).getResource("minecraftDot.grid.json");
    }

    @Test
    void buildDiscoPreviewSample_warnsWhenGridMissing() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        String json = "{}";

        when(plugin.getResource("disco/minecraftDot.grid.json"))
                .thenReturn(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        PreviewSampleService service = new PreviewSampleService(plugin, log);

        assertTrue(service.buildDiscoPreviewSample().isEmpty());
        verify(log).warn(contains("did not contain a usable grid"));
    }

    @Test
    void buildDiscoPreviewSample_handlesReadFailure() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };

        when(plugin.getResource("disco/minecraftDot.grid.json")).thenReturn(failing);

        PreviewSampleService service = new PreviewSampleService(plugin, log);
        assertTrue(service.buildDiscoPreviewSample().isEmpty());
        verify(log).warn(contains("Failed reading disco preview JSON"));
    }

    @Test
    void parseDiscoGridRows_handlesMissingAndValidGridPayloads() throws Exception {
        PreviewSampleService service = createService();

        List<String[]> missing = invokeParseRows(service, "{}");
        List<String[]> valid = invokeParseRows(service, "{\"grid\":[[\"A\",\"B\"],[\"C\"]]}");

        assertTrue(missing.isEmpty());
        assertEquals(2, valid.size());
        assertEquals(2, valid.get(0).length);
        assertEquals("C", valid.get(1)[0]);
    }

    @Test
    void resolveDiscoPreviewCsvMaterial_handlesNamespacesAirAndUnknown() throws Exception {
        PreviewSampleService service = createService();

        Material namespaced = invokeResolveMaterial(service, "minecraft:stone");
        Material plain = invokeResolveMaterial(service, "dirt");
        Material air = invokeResolveMaterial(service, "air");
        Material unknown = invokeResolveMaterial(service, "not_a_real_block");

        assertEquals(Material.STONE, namespaced);
        assertEquals(Material.DIRT, plain);
        assertNull(air);
        assertNull(unknown);
    }

    @Test
    void parseDiscoGridRows_handlesEscapedQuotesInsideTokens() throws Exception {
        PreviewSampleService service = createService();

        List<String[]> parsed = invokeParseRows(service, "{\"grid\":[[\"A\\\"B\",\"C\"]]}");

        assertEquals(1, parsed.size());
        assertEquals("A\"B", parsed.get(0)[0]);
        assertEquals("C", parsed.get(0)[1]);
    }

    @Test
    void parseDiscoGridRows_returnsEmptyForNullBlankOrMissingArrayStart() throws Exception {
        PreviewSampleService service = createService();

        assertTrue(invokeParseRows(service, null).isEmpty());
        assertTrue(invokeParseRows(service, "   ").isEmpty());
        assertTrue(invokeParseRows(service, "{\"grid\":\"not-an-array\"}").isEmpty());
    }

    @Test
    void resolveDiscoPreviewCsvMaterial_handlesNullAndTrimmedAirVariants() throws Exception {
        PreviewSampleService service = createService();

        assertNull(invokeResolveMaterial(service, null));
        assertNull(invokeResolveMaterial(service, "   "));
        assertNull(invokeResolveMaterial(service, "minecraft:air"));
    }

    @Test
    void resolveDiscoPreviewCsvMaterial_usesUppercaseFallbackForNamespacedToken() throws Exception {
        PreviewSampleService service = createService();

        assertEquals(Material.STONE, invokeResolveMaterial(service, "minecraft:Stone"));
    }

    @Test
    void hasSample_respectsBoundsAndPresence() throws Exception {
        PreviewSampleService service = createService();
        BlockData[][][] sampled = new BlockData[2][2][2];
        sampled[1][1][1] = block(Material.STONE);

        assertTrue(invokeHasSample(service, sampled, 1, 1, 1));
        assertFalse(invokeHasSample(service, sampled, 0, 0, 0));
        assertFalse(invokeHasSample(service, sampled, -1, 0, 0));
        assertFalse(invokeHasSample(service, sampled, 0, -1, 0));
        assertFalse(invokeHasSample(service, sampled, 0, 0, -1));
        assertFalse(invokeHasSample(service, sampled, 2, 0, 0));
        assertFalse(invokeHasSample(service, sampled, 0, 2, 0));
        assertFalse(invokeHasSample(service, sampled, 0, 0, 2));
    }

    @Test
    void toPreviewBlockData_mapsLiquidsAndPreservesSolidData() throws Exception {
        PreviewSampleService service = createService();

        BlockData stone = block(Material.STONE);
        BlockData water = block(Material.WATER);
        BlockData lava = block(Material.LAVA);
        BlockData bubble = block(Material.BUBBLE_COLUMN);

        BlockData mappedStone = invokeToPreviewBlockData(service, stone, false);
        BlockData mappedWater = invokeToPreviewBlockData(service, water, false);
        BlockData mappedLava = invokeToPreviewBlockData(service, lava, false);
        BlockData mappedBubble = invokeToPreviewBlockData(service, bubble, false);
        BlockData mappedLiquidFlag = invokeToPreviewBlockData(service, stone, true);

        assertTrue(mappedStone == stone);
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS, mappedWater.getMaterial());
        assertEquals(Material.ORANGE_STAINED_GLASS, mappedLava.getMaterial());
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS, mappedBubble.getMaterial());
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS, mappedLiquidFlag.getMaterial());
    }

    @Test
    void occlusionHelpers_coverSolidAndOutsideFacingBranches() throws Exception {
        PreviewSampleService service = createService();
        BlockData[][][] sampled = new BlockData[3][3][3];

        sampled[1][1][1] = block(Material.STONE);
        sampled[1][2][1] = block(Material.STONE);
        sampled[0][1][1] = block(Material.STONE);
        sampled[2][1][1] = block(Material.STONE);
        sampled[1][1][0] = block(Material.STONE);
        sampled[1][1][2] = block(Material.STONE);
        sampled[1][0][1] = block(Material.STONE);

        assertTrue(invokePrivateBoolean(service, "hasFullySolidCoverAbove", sampled, 1, 1, 1));
        assertFalse(invokePrivateBoolean(service, "hasNonFullySolidOnTop", sampled, 1, 1, 1));
        assertTrue(invokePrivateBoolean(service, "isCompletelyOccludedHorizontally", sampled, 1, 1, 1));
        assertFalse(invokePrivateBoolean(service, "isOutsideFacing", sampled, 1, 1, 1));

        sampled[2][1][1] = null;
        assertTrue(invokePrivateBoolean(service, "isOutsideFacing", sampled, 1, 1, 1));
    }

    @Test
    void hasFullySolidSample_handlesTreeCanopyAndNonOccludingAbove() throws Exception {
        PreviewSampleService service = createService();
        BlockData[][][] sampled = new BlockData[2][2][2];
        sampled[0][0][0] = block(Material.OAK_LEAVES);
        sampled[1][1][1] = block(Material.TORCH);

        assertFalse(invokePrivateBoolean(service, "hasFullySolidSample", sampled, 0, 0, 0));
        assertFalse(invokePrivateBoolean(service, "hasFullySolidSample", sampled, 1, 1, 1));

        sampled[1][1][1] = block(Material.STONE);
        assertTrue(invokePrivateBoolean(service, "hasFullySolidSample", sampled, 1, 1, 1));
    }

    @Test
    void hasNonFullySolidOnTop_returnsTrueWhenAboveIsNotOccluding() throws Exception {
        PreviewSampleService service = createService();
        BlockData[][][] sampled = new BlockData[2][2][2];
        sampled[0][0][0] = block(Material.STONE);
        sampled[0][1][0] = block(Material.TORCH);

        assertTrue(invokePrivateBoolean(service, "hasNonFullySolidOnTop", sampled, 0, 0, 0));
    }

    @Test
    void resolveSurfaceTopIgnoringLeaves_skipsAirAndCanopy_andFallsBackToStartY() throws Exception {
        PreviewSampleService service = createService();
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(0);

        Block air0 = blockAt(Material.AIR);
        Block leaves0 = blockAt(Material.OAK_LEAVES);
        Block stone0 = blockAt(Material.STONE);
        when(world.getBlockAt(0, 4, 0)).thenReturn(air0);
        when(world.getBlockAt(0, 3, 0)).thenReturn(leaves0);
        when(world.getBlockAt(0, 2, 0)).thenReturn(stone0);

        int resolved = invokeResolveSurfaceTopIgnoringLeaves(service, world, 0, 0, 4);
        assertEquals(2, resolved);

        Block air1a = blockAt(Material.AIR);
        Block leaves1a = blockAt(Material.OAK_LEAVES);
        Block air1b = blockAt(Material.AIR);
        Block leaves1b = blockAt(Material.OAK_LEAVES);
        Block air1c = blockAt(Material.AIR);
        when(world.getBlockAt(1, 4, 0)).thenReturn(air1a);
        when(world.getBlockAt(1, 3, 0)).thenReturn(leaves1a);
        when(world.getBlockAt(1, 2, 0)).thenReturn(air1b);
        when(world.getBlockAt(1, 1, 0)).thenReturn(leaves1b);
        when(world.getBlockAt(1, 0, 0)).thenReturn(air1c);

        int fallback = invokeResolveSurfaceTopIgnoringLeaves(service, world, 1, 0, 4);
        assertEquals(4, fallback);
    }

    @Test
    void isTreeCanopyMaterial_recognizesAdditionalCanopyLikeSuffixes() throws Exception {
        PreviewSampleService service = createService();

        assertTrue(invokeIsTreeCanopyMaterial(service, Material.CRIMSON_STEM));
        assertTrue(invokeIsTreeCanopyMaterial(service, Material.WARPED_HYPHAE));
        assertTrue(invokeIsTreeCanopyMaterial(service, Material.RED_MUSHROOM_BLOCK));
        assertTrue(invokeIsTreeCanopyMaterial(service, Material.MUSHROOM_STEM));
    }

    @Test
    void sampleSpawnSurface_coversTreeLiquidAndSolidFillBranches() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("challenge.preview_hologram_layers", 1);
        config.set("challenge.preview_hologram_depth_multiplier", 0.0D);
        config.set("challenge.preview_hologram_solid_fill", false);

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        PreviewSampleService service = new PreviewSampleService(plugin, mock(DeepCoreLogger.class));

        World runWorld = mock(World.class);
        Location spawn = new Location(runWorld, 16.0D, 64.0D, 16.0D);
        when(runWorld.getSpawnLocation()).thenReturn(spawn);
        when(runWorld.getMinHeight()).thenReturn(0);
        when(runWorld.getHighestBlockYAt(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.eq(org.bukkit.HeightMap.WORLD_SURFACE)))
                .thenAnswer(invocation -> {
                    int x = invocation.getArgument(0);
                    int z = invocation.getArgument(1);
                    if (x == 0 && z == 0) {
                        return 6;
                    }
                    if (x == 1 && z == 0) {
                        return 0;
                    }
                    if (x == 2 && z == 0) {
                        return 5;
                    }
                    if (x == 3 && z == 0) {
                        return 5;
                    }
                    return 5;
                });
        when(runWorld.getBlockAt(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    int x = invocation.getArgument(0);
                    int y = invocation.getArgument(1);
                    int z = invocation.getArgument(2);
                    Material material;
                    if (x == 0 && z == 0) {
                        if (y == 6) {
                            material = Material.OAK_LEAVES;
                        } else if (y == 5) {
                            material = Material.STONE;
                        } else {
                            material = Material.AIR;
                        }
                    } else if (x == 1 && z == 0) {
                        material = y == 0 ? Material.STONE : Material.AIR;
                    } else if (x == 2 && z == 0) {
                        material = y == 5 ? Material.WATER : Material.AIR;
                    } else if (x == 3 && z == 0) {
                        material = y == 5 ? Material.LAVA : Material.AIR;
                    } else {
                        material = y == 5 ? Material.STONE : Material.AIR;
                    }

                    Block block = mock(Block.class);
                    when(block.getType()).thenReturn(material);
                    when(block.getBlockData()).thenReturn(material.createBlockData());
                    when(block.isLiquid()).thenReturn(material == Material.WATER || material == Material.LAVA);
                    return block;
                });

        List<PreviewSampleService.PreviewBlock> preview = service.sampleSpawnSurface(runWorld);
        assertFalse(preview.isEmpty());

        config.set("challenge.preview_hologram_solid_fill", true);
        List<PreviewSampleService.PreviewBlock> filledPreview = service.sampleSpawnSurface(runWorld);
        assertFalse(filledPreview.isEmpty());
    }

    @Test
    void buildDiscoPreviewSample_parsesAndCachesRecognizedMaterials() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        String json = "{\"grid\":[[\"minecraft:stone\",\"air\"],[\"minecraft:oak_leaves\",\"minecraft:water\"]]}";

        when(plugin.getResource("disco/minecraftDot.grid.json")).thenReturn(null);
        when(plugin.getResource("minecraftDot.grid.json"))
                .thenReturn(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        PreviewSampleService service = new PreviewSampleService(plugin, log);
        List<PreviewSampleService.PreviewBlock> first = service.buildDiscoPreviewSample();
        List<PreviewSampleService.PreviewBlock> second = service.buildDiscoPreviewSample();

        assertEquals(3, first.size());
        assertEquals(3, second.size());
        assertTrue(first.stream().anyMatch(block -> block.blockData().getMaterial() == Material.STONE));
        assertTrue(first.stream().anyMatch(block -> block.blockData().getMaterial() == Material.OAK_LEAVES));
        assertTrue(first.stream().anyMatch(block -> block.blockData().getMaterial() == Material.WATER));
        verify(plugin, times(1)).getResource("disco/minecraftDot.grid.json");
        verify(plugin, times(1)).getResource("minecraftDot.grid.json");
    }

    private static BlockData block(Material material) {
        BlockData blockData = mock(BlockData.class);
        when(blockData.getMaterial()).thenReturn(material);
        return blockData;
    }

    private static Block blockAt(Material material) {
        Block block = mock(Block.class);
        BlockData blockData = block(material);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(blockData);
        when(block.isLiquid()).thenReturn(material == Material.WATER || material == Material.LAVA);
        return block;
    }

    @SuppressWarnings("unchecked")
    private static List<String[]> invokeParseRows(PreviewSampleService service, String json) throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod("parseDiscoGridRows", String.class);
        method.setAccessible(true);
        return (List<String[]>) method.invoke(service, json);
    }

    private static Material invokeResolveMaterial(PreviewSampleService service, String token) throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod("resolveDiscoPreviewCsvMaterial", String.class);
        method.setAccessible(true);
        return (Material) method.invoke(service, token);
    }

    private static boolean invokeHasSample(PreviewSampleService service, BlockData[][][] sampled, int x, int y, int z)
            throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod(
                "hasSample", BlockData[][][].class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, sampled, x, y, z);
    }

    private static BlockData invokeToPreviewBlockData(PreviewSampleService service, BlockData blockData, boolean liquid)
            throws Exception {
        Method method =
                PreviewSampleService.class.getDeclaredMethod("toPreviewBlockData", BlockData.class, boolean.class);
        method.setAccessible(true);
        return (BlockData) method.invoke(service, blockData, liquid);
    }

    private static boolean invokePrivateBoolean(
            PreviewSampleService service, String methodName, BlockData[][][] sampled, int x, int y, int z)
            throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod(
                methodName, BlockData[][][].class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, sampled, x, y, z);
    }

    private static int invokeResolveSurfaceTopIgnoringLeaves(
            PreviewSampleService service, World world, int worldX, int worldZ, int startY) throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod(
                "resolveSurfaceTopIgnoringLeaves", World.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(service, world, worldX, worldZ, startY);
    }

    private static boolean invokeIsTreeCanopyMaterial(PreviewSampleService service, Material material)
            throws Exception {
        Method method = PreviewSampleService.class.getDeclaredMethod("isTreeCanopyMaterial", Material.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, material);
    }
}
