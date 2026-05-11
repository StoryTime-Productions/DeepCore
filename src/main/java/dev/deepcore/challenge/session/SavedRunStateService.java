package dev.deepcore.challenge.session;

import dev.deepcore.logging.DeepCoreLogger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/** Persists and restores a single saved speedrun state across server restarts. */
public final class SavedRunStateService {

    /** Snapshot of the full run state serialized to disk on a save vote. */
    public record SavedRunSnapshot(
            long savedAtMs,
            long runStartMs,
            long accumulatedPausedMs,
            boolean reachedNether,
            long netherMs,
            boolean reachedBlazeObjective,
            long blazeObjectiveMs,
            boolean reachedEnd,
            long endMs,
            List<String> participantUuids,
            List<String> enabledComponents,
            String difficulty,
            Map<String, PlayerSnapshot> playerSnapshots) {}

    /** Snapshot of a single player's position, inventory, health, and status state. */
    public record PlayerSnapshot(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            double health,
            double maxHealth,
            int foodLevel,
            float saturation,
            float exhaustion,
            int fireTicks,
            int remainingAir,
            int level,
            float exp,
            int totalExperience,
            String gameMode,
            boolean allowFlight,
            boolean flying,
            ItemStack[] storageContents,
            ItemStack[] armorContents,
            ItemStack[] extraContents,
            List<PotionEffect> potionEffects) {}

    private static final double DEFAULT_MAX_HEALTH = 20.0;

    private final DeepCoreLogger log;
    private final File saveFile;

    /**
     * Creates a saved-run state service backed by a YAML file in the plugin data folder.
     *
     * @param plugin plugin instance used to resolve the data folder path
     * @param log    logger for save and load diagnostics
     */
    public SavedRunStateService(JavaPlugin plugin, DeepCoreLogger log) {
        this.log = log;
        this.saveFile = new File(plugin.getDataFolder(), "saved_run.yml");
    }

    /**
     * Returns whether a saved run file currently exists on disk.
     *
     * @return true when a saved run is available for restore
     */
    public boolean hasSavedRun() {
        return saveFile.exists();
    }

    /**
     * Serializes the given snapshot to the saved-run YAML file, overwriting any previous save.
     *
     * @param snapshot run snapshot to persist
     */
    public void saveRun(SavedRunSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("savedAtMs", snapshot.savedAtMs());
        yaml.set("runStartMs", snapshot.runStartMs());
        yaml.set("accumulatedPausedMs", snapshot.accumulatedPausedMs());
        yaml.set("reachedNether", snapshot.reachedNether());
        yaml.set("netherMs", snapshot.netherMs());
        yaml.set("reachedBlazeObjective", snapshot.reachedBlazeObjective());
        yaml.set("blazeObjectiveMs", snapshot.blazeObjectiveMs());
        yaml.set("reachedEnd", snapshot.reachedEnd());
        yaml.set("endMs", snapshot.endMs());
        yaml.set("participantUuids", snapshot.participantUuids());
        yaml.set("enabledComponents", snapshot.enabledComponents());
        yaml.set("difficulty", snapshot.difficulty());

        for (Map.Entry<String, PlayerSnapshot> entry :
                snapshot.playerSnapshots().entrySet()) {
            String prefix = "playerSnapshots." + entry.getKey();
            PlayerSnapshot ps = entry.getValue();
            yaml.set(prefix + ".worldName", ps.worldName());
            yaml.set(prefix + ".x", ps.x());
            yaml.set(prefix + ".y", ps.y());
            yaml.set(prefix + ".z", ps.z());
            yaml.set(prefix + ".yaw", (double) ps.yaw());
            yaml.set(prefix + ".pitch", (double) ps.pitch());
            yaml.set(prefix + ".health", ps.health());
            yaml.set(prefix + ".maxHealth", ps.maxHealth());
            yaml.set(prefix + ".foodLevel", ps.foodLevel());
            yaml.set(prefix + ".saturation", (double) ps.saturation());
            yaml.set(prefix + ".exhaustion", (double) ps.exhaustion());
            yaml.set(prefix + ".fireTicks", ps.fireTicks());
            yaml.set(prefix + ".remainingAir", ps.remainingAir());
            yaml.set(prefix + ".level", ps.level());
            yaml.set(prefix + ".exp", (double) ps.exp());
            yaml.set(prefix + ".totalExperience", ps.totalExperience());
            yaml.set(prefix + ".gameMode", ps.gameMode());
            yaml.set(prefix + ".allowFlight", ps.allowFlight());
            yaml.set(prefix + ".flying", ps.flying());
            saveItemArray(yaml, prefix + ".storage", ps.storageContents());
            saveItemArray(yaml, prefix + ".armor", ps.armorContents());
            saveItemArray(yaml, prefix + ".extra", ps.extraContents());
            yaml.set(prefix + ".potionEffects", new ArrayList<>(ps.potionEffects()));
        }

        try {
            yaml.save(saveFile);
            log.debug("Saved run state to " + saveFile.getPath());
        } catch (IOException e) {
            log.error("Failed to save run state: " + e.getMessage(), e);
        }
    }

    /**
     * Loads and deserializes the saved run from disk.
     *
     * @return the saved snapshot, or empty if no save file exists
     */
    public Optional<SavedRunSnapshot> loadSavedRun() {
        if (!saveFile.exists()) {
            return Optional.empty();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(saveFile);
        long savedAtMs = yaml.getLong("savedAtMs");
        long runStartMs = yaml.getLong("runStartMs");
        long accumulatedPausedMs = yaml.getLong("accumulatedPausedMs");
        boolean reachedNether = yaml.getBoolean("reachedNether");
        long netherMs = yaml.getLong("netherMs");
        boolean reachedBlazeObjective = yaml.getBoolean("reachedBlazeObjective");
        long blazeObjectiveMs = yaml.getLong("blazeObjectiveMs");
        boolean reachedEnd = yaml.getBoolean("reachedEnd");
        long endMs = yaml.getLong("endMs");
        List<String> participantUuids = yaml.getStringList("participantUuids");
        List<String> enabledComponents = yaml.getStringList("enabledComponents");
        String difficulty = yaml.getString("difficulty", "");

        Map<String, PlayerSnapshot> playerSnapshots = new HashMap<>();
        if (yaml.isConfigurationSection("playerSnapshots")) {
            for (String uuidStr :
                    yaml.getConfigurationSection("playerSnapshots").getKeys(false)) {
                String prefix = "playerSnapshots." + uuidStr;
                PlayerSnapshot ps = loadPlayerSnapshot(yaml, prefix);
                playerSnapshots.put(uuidStr, ps);
            }
        }

        return Optional.of(new SavedRunSnapshot(
                savedAtMs,
                runStartMs,
                accumulatedPausedMs,
                reachedNether,
                netherMs,
                reachedBlazeObjective,
                blazeObjectiveMs,
                reachedEnd,
                endMs,
                participantUuids,
                enabledComponents,
                difficulty,
                playerSnapshots));
    }

    /**
     * Deletes the saved run file from disk, clearing the restore slot.
     */
    public void clearSavedRun() {
        if (saveFile.exists() && !saveFile.delete()) {
            log.warn("Failed to delete saved run file: " + saveFile.getPath());
        }
    }

    /**
     * Captures the current in-game state of a player as a persistent snapshot.
     *
     * @param player player whose state should be captured
     * @return snapshot of the player's current position, inventory, health, and status
     */
    public static PlayerSnapshot capturePlayer(Player player) {
        PlayerInventory inventory = player.getInventory();
        double maxHealth = DEFAULT_MAX_HEALTH;
        try {
            Attribute attr = resolveMaxHealthAttribute();
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                maxHealth = instance.getBaseValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }

        Location loc = player.getLocation();
        return new PlayerSnapshot(
                loc.getWorld() != null ? loc.getWorld().getName() : "",
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                player.getHealth(),
                maxHealth,
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getFireTicks(),
                player.getRemainingAir(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getGameMode().name(),
                player.getAllowFlight(),
                player.isFlying(),
                cloneContents(inventory.getStorageContents()),
                cloneContents(inventory.getArmorContents()),
                cloneContents(inventory.getExtraContents()),
                new ArrayList<>(player.getActivePotionEffects()));
    }

    /**
     * Applies a saved snapshot to a player, teleporting them to their saved location and
     * restoring their inventory, health, hunger, XP, game mode, and potion effects.
     *
     * @param player   player to restore
     * @param snapshot snapshot to apply
     */
    public static void applySnapshot(Player player, PlayerSnapshot snapshot) {
        World world = Bukkit.getWorld(snapshot.worldName());
        if (world != null) {
            Location loc =
                    new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
            player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(cloneContents(snapshot.storageContents()));
        inventory.setArmorContents(cloneContents(snapshot.armorContents()));
        inventory.setExtraContents(cloneContents(snapshot.extraContents()));

        try {
            Attribute attr = resolveMaxHealthAttribute();
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null && instance.getBaseValue() != snapshot.maxHealth()) {
                instance.setBaseValue(snapshot.maxHealth());
            }
        } catch (ReflectiveOperationException ignored) {
        }

        double clampedHealth = Math.max(0.0D, Math.min(snapshot.maxHealth(), snapshot.health()));
        player.setHealth(clampedHealth <= 0.0D ? 0.5D : clampedHealth);
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setFireTicks(snapshot.fireTicks());
        player.setRemainingAir(snapshot.remainingAir());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());
        player.setTotalExperience(snapshot.totalExperience());

        try {
            player.setGameMode(GameMode.valueOf(snapshot.gameMode()));
        } catch (IllegalArgumentException ignored) {
        }

        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying() && snapshot.allowFlight());

        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : snapshot.potionEffects()) {
            player.addPotionEffect(effect);
        }
        player.updateInventory();
    }

    private void saveItemArray(YamlConfiguration yaml, String prefix, ItemStack[] contents) {
        yaml.set(prefix + ".size", contents.length);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                yaml.set(prefix + "." + i, contents[i]);
            }
        }
    }

    private static ItemStack[] loadItemArray(YamlConfiguration yaml, String prefix, int defaultSize) {
        int size = yaml.getInt(prefix + ".size", defaultSize);
        ItemStack[] result = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            result[i] = yaml.getItemStack(prefix + "." + i);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<PotionEffect> loadPotionEffects(YamlConfiguration yaml, String key) {
        List<?> raw = yaml.getList(key);
        if (raw == null) {
            return List.of();
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (Object obj : raw) {
            if (obj instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private static PlayerSnapshot loadPlayerSnapshot(YamlConfiguration yaml, String prefix) {
        String worldName = yaml.getString(prefix + ".worldName", "");
        double x = yaml.getDouble(prefix + ".x");
        double y = yaml.getDouble(prefix + ".y");
        double z = yaml.getDouble(prefix + ".z");
        float yaw = (float) yaml.getDouble(prefix + ".yaw");
        float pitch = (float) yaml.getDouble(prefix + ".pitch");
        double health = yaml.getDouble(prefix + ".health");
        double maxHealth = yaml.getDouble(prefix + ".maxHealth", DEFAULT_MAX_HEALTH);
        int foodLevel = yaml.getInt(prefix + ".foodLevel");
        float saturation = (float) yaml.getDouble(prefix + ".saturation");
        float exhaustion = (float) yaml.getDouble(prefix + ".exhaustion");
        int fireTicks = yaml.getInt(prefix + ".fireTicks");
        int remainingAir = yaml.getInt(prefix + ".remainingAir");
        int level = yaml.getInt(prefix + ".level");
        float exp = (float) yaml.getDouble(prefix + ".exp");
        int totalExperience = yaml.getInt(prefix + ".totalExperience");
        String gameMode = yaml.getString(prefix + ".gameMode", "SURVIVAL");
        boolean allowFlight = yaml.getBoolean(prefix + ".allowFlight");
        boolean flying = yaml.getBoolean(prefix + ".flying");
        ItemStack[] storage = loadItemArray(yaml, prefix + ".storage", 36);
        ItemStack[] armor = loadItemArray(yaml, prefix + ".armor", 4);
        ItemStack[] extra = loadItemArray(yaml, prefix + ".extra", 1);
        List<PotionEffect> effects = loadPotionEffects(yaml, prefix + ".potionEffects");

        return new PlayerSnapshot(
                worldName,
                x,
                y,
                z,
                yaw,
                pitch,
                health,
                maxHealth,
                foodLevel,
                saturation,
                exhaustion,
                fireTicks,
                remainingAir,
                level,
                exp,
                totalExperience,
                gameMode,
                allowFlight,
                flying,
                storage,
                armor,
                extra,
                effects);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] == null ? null : contents[i].clone();
        }
        return cloned;
    }

    private static Attribute resolveMaxHealthAttribute() throws ReflectiveOperationException {
        try {
            return (Attribute) Attribute.class.getField("MAX_HEALTH").get(null);
        } catch (ReflectiveOperationException ignored) {
            return (Attribute) Attribute.class.getField("GENERIC_MAX_HEALTH").get(null);
        }
    }
}
