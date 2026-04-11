package dev.deepcore.challenge.ui;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.records.RunRecord;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Renders prep GUI pages and run history entries for challenge setup flows. */
public final class PrepGuiRenderer {
    private static final int RUN_HISTORY_PAGE_SIZE = 21;

    /**
     * Applies decorative border panes to the prep GUI inventory frame.
     *
     * @param inventory prep GUI inventory to decorate
     */
    public void applyPrepGuiDecorations(Inventory inventory) {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                boolean topRow = row == 0;
                boolean bottomRow = row == 5;
                boolean leftEdge = col == 0;
                boolean rightEdge = col == 8;
                if (topRow || bottomRow || leftEdge || rightEdge) {
                    inventory.setItem(slot, createEdgePaneItem());
                }
            }
        }
    }

    /**
     * Populates the prep categories page with navigation and ready controls.
     *
     * @param inventory      prep GUI inventory to populate
     * @param ready          whether the viewing player is marked ready
     * @param readyCount     number of ready participants
     * @param onlineCount    number of online participants
     * @param previewEnabled whether world preview mechanics are enabled
     */
    public void populateCategoriesPage(
            Inventory inventory, boolean ready, int readyCount, int onlineCount, boolean previewEnabled) {
        inventory.setItem(4, createInfoItem(Material.NETHER_STAR, "Mechanic Categories", "Select a category"));
        inventory.setItem(
                20, createInfoItem(Material.CHEST, "Inventory Mechanics", "Open inventory-related mechanics"));
        inventory.setItem(
                22,
                createInfoItem(
                        Material.WRITABLE_BOOK,
                        "Completed Runs",
                        "View successful runs with date, participants, and split times"));
        inventory.setItem(24, createInfoItem(Material.GOLDEN_APPLE, "Health Mechanics", "Open health mechanics"));
        inventory.setItem(45, createInfoItem(Material.BARRIER, "Close", "Close this menu"));
        inventory.setItem(47, createToggleItem("Ready", ready, "Each online player must ready up"));
        inventory.setItem(
                49,
                createInfoItem(
                        Material.CLOCK,
                        "Ready: " + readyCount + "/" + onlineCount,
                        "Countdown starts automatically when all are ready"));
        inventory.setItem(
                51,
                createInfoItem(
                        Material.RECOVERY_COMPASS,
                        "Regenerate World",
                        previewEnabled
                                ? "Create a new run world and refresh pedestal preview"
                                : "Create a new run world"));
    }

    /**
     * Populates the inventory mechanics page and ready/world controls.
     *
     * @param inventory        prep GUI inventory to populate
     * @param challengeManager challenge manager that provides enabled component
     *                         states
     * @param ready            whether the viewing player is marked ready
     * @param readyCount       number of ready participants
     * @param onlineCount      number of online participants
     * @param previewEnabled   whether world preview mechanics are enabled
     */
    public void populateInventoryPage(
            Inventory inventory,
            ChallengeManager challengeManager,
            boolean ready,
            int readyCount,
            int onlineCount,
            boolean previewEnabled) {
        inventory.setItem(4, createInfoItem(Material.CHEST, "Inventory Mechanics", "Manage inventory rules"));
        inventory.setItem(
                20,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.KEEP_INVENTORY,
                        "Keep items on death",
                        "Exclusive with: none"));
        inventory.setItem(
                22,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.SHARED_INVENTORY,
                        "Team uses synchronized inventory",
                        "Exclusive with: none"));
        inventory.setItem(
                24,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.DEGRADING_INVENTORY,
                        "Available slots reduce over time",
                        "Exclusive with: none"));
        inventory.setItem(45, createInfoItem(Material.ARROW, "Back", "Return to categories"));
        inventory.setItem(47, createToggleItem("Ready", ready, "Each online player must ready up"));
        inventory.setItem(
                49,
                createInfoItem(
                        Material.CLOCK,
                        "Ready: " + readyCount + "/" + onlineCount,
                        "Countdown starts automatically when all are ready"));
        inventory.setItem(
                51,
                createInfoItem(
                        Material.RECOVERY_COMPASS,
                        "Regenerate World",
                        previewEnabled
                                ? "Create a new run world and refresh pedestal preview"
                                : "Create a new run world"));
    }

    /**
     * Populates the health mechanics page and ready/world controls.
     *
     * @param inventory        prep GUI inventory to populate
     * @param challengeManager challenge manager that provides enabled component
     *                         states
     * @param ready            whether the viewing player is marked ready
     * @param readyCount       number of ready participants
     * @param onlineCount      number of online participants
     * @param previewEnabled   whether world preview mechanics are enabled
     */
    public void populateHealthPage(
            Inventory inventory,
            ChallengeManager challengeManager,
            boolean ready,
            int readyCount,
            int onlineCount,
            boolean previewEnabled) {
        inventory.setItem(4, createInfoItem(Material.GOLDEN_APPLE, "Health Mechanics", "Manage health rules"));
        inventory.setItem(
                20,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.HEALTH_REFILL,
                        "Allow normal regeneration/refill",
                        "Exclusive with: Initial Half Heart"));
        inventory.setItem(
                22,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.SHARED_HEALTH,
                        "Team shares health and hunger",
                        "Exclusive with: none"));
        inventory.setItem(
                24,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.INITIAL_HALF_HEART,
                        "Start with half-heart max health",
                        "Exclusive with: Health Refill"));
        inventory.setItem(
                31,
                createMechanicItem(
                        challengeManager,
                        ChallengeComponent.HARDCORE,
                        "ON: one death ends the run. OFF: unlimited deaths",
                        "Exclusive with: none"));
        inventory.setItem(45, createInfoItem(Material.ARROW, "Back", "Return to categories"));
        inventory.setItem(47, createToggleItem("Ready", ready, "Each online player must ready up"));
        inventory.setItem(
                49,
                createInfoItem(
                        Material.CLOCK,
                        "Ready: " + readyCount + "/" + onlineCount,
                        "Countdown starts automatically when all are ready"));
        inventory.setItem(
                51,
                createInfoItem(
                        Material.RECOVERY_COMPASS,
                        "Regenerate World",
                        previewEnabled
                                ? "Create a new run world and refresh pedestal preview"
                                : "Create a new run world"));
    }

    /**
     * Populates one run-history page and returns the normalized page index used.
     *
     * @param inventory         prep GUI inventory to populate
     * @param page              requested zero-based run history page index
     * @param records           run records ordered for display
     * @param dateFormatter     formatter for record timestamps
     * @param durationFormatter formatter for split durations in milliseconds
     * @return normalized page index that was actually rendered
     */
    public int populateRunHistoryPage(
            Inventory inventory,
            int page,
            List<RunRecord> records,
            DateTimeFormatter dateFormatter,
            LongFunction<String> durationFormatter) {
        int safePage = Math.max(0, page);
        int startIndex = safePage * RUN_HISTORY_PAGE_SIZE;
        if (startIndex >= records.size() && safePage > 0) {
            safePage = Math.max(0, (records.size() - 1) / RUN_HISTORY_PAGE_SIZE);
            startIndex = safePage * RUN_HISTORY_PAGE_SIZE;
        }

        inventory.setItem(
                4,
                createInfoItem(
                        Material.WRITABLE_BOOK,
                        "Completed Runs",
                        "Successful runs with full split details and participants"));

        int[] recordSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 31, 34, 37, 39, 41
        };

        for (int i = 0; i < recordSlots.length; i++) {
            int recordIndex = startIndex + i;
            if (recordIndex >= records.size()) {
                break;
            }

            RunRecord record = records.get(recordIndex);
            inventory.setItem(
                    recordSlots[i], createRunRecordItem(record, recordIndex + 1, dateFormatter, durationFormatter));
        }

        inventory.setItem(45, createInfoItem(Material.ARROW, "Back", "Return to categories"));
        inventory.setItem(
                47,
                createInfoItem(
                        Material.SPECTRAL_ARROW,
                        "Previous Page",
                        safePage > 0 ? "Go to newer runs" : "You are on the first page"));
        inventory.setItem(
                51,
                createInfoItem(
                        Material.ARROW,
                        "Next Page",
                        hasRunHistoryNextPage(safePage, records.size())
                                ? "Go to older runs"
                                : "No more run history pages"));

        return safePage;
    }

    /**
     * Returns whether a subsequent run-history page exists for the current index.
     *
     * @param page         current zero-based page index
     * @param totalRecords total number of available run records
     * @return true when a later page exists
     */
    public boolean hasRunHistoryNextPage(int page, int totalRecords) {
        return ((page + 1) * RUN_HISTORY_PAGE_SIZE) < totalRecords;
    }

    private ItemStack createRunRecordItem(
            RunRecord record, int rank, DateTimeFormatter dateFormatter, LongFunction<String> durationFormatter) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.AQUA + "Run #" + rank + ChatColor.GRAY + " - " + ChatColor.GREEN
                + durationFormatter.apply(record.getOverallTimeMs()));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Date: " + ChatColor.WHITE
                + dateFormatter.format(Instant.ofEpochMilli(record.getTimestamp())));
        lore.add(ChatColor.YELLOW + "Participants: " + ChatColor.WHITE
                + formatParticipantNames(record.getParticipants()));
        lore.add(ChatColor.DARK_GRAY + " ");
        lore.add(ChatColor.GOLD + "Split Times");
        lore.add(ChatColor.AQUA + durationFormatter.apply(record.getOverworldToNetherMs()) + ChatColor.GRAY
                + " - Overworld to Nether");
        lore.add(ChatColor.AQUA + durationFormatter.apply(record.getNetherToBlazeRodsMs()) + ChatColor.GRAY
                + " - Nether to Blaze Rods");
        lore.add(ChatColor.AQUA + durationFormatter.apply(record.getBlazeRodsToEndMs()) + ChatColor.GRAY
                + " - Blaze Rods to End");
        lore.add(ChatColor.AQUA + durationFormatter.apply(record.getNetherToEndMs()) + ChatColor.GRAY
                + " - Nether to End");
        lore.add(ChatColor.AQUA + durationFormatter.apply(record.getEndToDragonMs()) + ChatColor.GRAY
                + " - End to Dragon");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatParticipantNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "Unknown";
        }

        return String.join(", ", names);
    }

    private ItemStack createToggleItem(String name, boolean enabled, String detail) {
        ItemStack item = new ItemStack(enabled ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + detail);
            lore.add(enabled ? ChatColor.GREEN + "State: ON" : ChatColor.RED + "State: OFF");
            lore.add(ChatColor.DARK_GRAY + "Click to toggle");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(Material material, String name, String detail) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            meta.setLore(List.of(ChatColor.GRAY + detail));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMechanicItem(
            ChallengeManager challengeManager, ChallengeComponent component, String detail, String exclusivity) {
        boolean enabled = challengeManager.isComponentEnabled(component);
        ItemStack item = new ItemStack(enabled ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + component.displayName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + detail);
            lore.add(enabled ? ChatColor.GREEN + "State: ON" : ChatColor.RED + "State: OFF");
            lore.add(ChatColor.DARK_AQUA + exclusivity);
            lore.add(ChatColor.DARK_GRAY + "Click to toggle");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createEdgePaneItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLACK + " ");
            item.setItemMeta(meta);
        }
        return item;
    }
}
