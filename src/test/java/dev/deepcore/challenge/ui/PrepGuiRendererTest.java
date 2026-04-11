package dev.deepcore.challenge.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.records.RunRecord;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.LongFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrepGuiRendererTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void hasRunHistoryNextPage_handlesBoundaries() {
        PrepGuiRenderer renderer = new PrepGuiRenderer();

        assertFalse(renderer.hasRunHistoryNextPage(0, 21));
        assertTrue(renderer.hasRunHistoryNextPage(0, 22));
        assertFalse(renderer.hasRunHistoryNextPage(1, 42));
        assertTrue(renderer.hasRunHistoryNextPage(1, 43));
    }

    @Test
    void formatParticipantNames_returnsUnknownForNullOrEmpty() throws Exception {
        PrepGuiRenderer renderer = new PrepGuiRenderer();
        Method method = PrepGuiRenderer.class.getDeclaredMethod("formatParticipantNames", List.class);
        method.setAccessible(true);

        assertEquals("Unknown", method.invoke(renderer, new Object[] {null}));
        assertEquals("Unknown", method.invoke(renderer, List.of()));
        assertEquals("Alice, Bob", method.invoke(renderer, List.of("Alice", "Bob")));
    }

    @Test
    void applyPrepGuiDecorations_setsOnlyBorderSlots() {
        PrepGuiRenderer renderer = new PrepGuiRenderer();
        Inventory inventory = Bukkit.createInventory(null, 54, "prep");

        renderer.applyPrepGuiDecorations(inventory);

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                boolean border = row == 0 || row == 5 || col == 0 || col == 8;
                ItemStack item = inventory.getItem(slot);
                if (border) {
                    assertNotNull(item);
                    assertEquals(Material.BLACK_STAINED_GLASS_PANE, item.getType());
                } else {
                    assertNull(item);
                }
            }
        }
    }

    @Test
    void populatePages_renderExpectedControlsAndHistoryPaging() {
        PrepGuiRenderer renderer = new PrepGuiRenderer();
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        when(challengeManager.isComponentEnabled(ChallengeComponent.KEEP_INVENTORY))
                .thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_INVENTORY))
                .thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL))
                .thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(true);

        Inventory categories = Bukkit.createInventory(null, 54, "categories");
        renderer.populateCategoriesPage(categories, true, 2, 3, true);
        assertEquals(Material.EMERALD_BLOCK, categories.getItem(47).getType());
        assertTrue(categories.getItem(51).getItemMeta().getLore().get(0).contains("refresh pedestal preview"));

        Inventory inventoryPage = Bukkit.createInventory(null, 54, "inventory");
        renderer.populateInventoryPage(inventoryPage, challengeManager, false, 1, 3, false);
        assertEquals(Material.LIME_STAINED_GLASS, inventoryPage.getItem(20).getType());
        assertEquals(Material.RED_STAINED_GLASS, inventoryPage.getItem(22).getType());
        assertEquals(Material.REDSTONE_BLOCK, inventoryPage.getItem(47).getType());

        Inventory healthPage = Bukkit.createInventory(null, 54, "health");
        renderer.populateHealthPage(healthPage, challengeManager, true, 3, 3, false);
        assertEquals(Material.RED_STAINED_GLASS, healthPage.getItem(20).getType());
        assertEquals(Material.LIME_STAINED_GLASS, healthPage.getItem(22).getType());
        assertEquals(Material.LIME_STAINED_GLASS, healthPage.getItem(31).getType());

        List<RunRecord> records = java.util.stream.IntStream.range(0, 22)
                .mapToObj(i -> new RunRecord(
                        Instant.parse("2024-01-01T00:00:00Z").toEpochMilli() + i,
                        10_000L + i,
                        1_000L,
                        2_000L,
                        3_000L,
                        4_000L,
                        5_000L,
                        "A,B"))
                .toList();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
        LongFunction<String> durationFormatter = millis -> millis + "ms";

        Inventory history = Bukkit.createInventory(null, 54, "history");
        int safePage = renderer.populateRunHistoryPage(history, 99, records, dateFormatter, durationFormatter);
        assertEquals(1, safePage);
        assertEquals(Material.FILLED_MAP, history.getItem(10).getType());
        String runTitle = ChatColor.stripColor(history.getItem(10).getItemMeta().getDisplayName());
        assertTrue(runTitle.contains("Run #22"));
    }
}
