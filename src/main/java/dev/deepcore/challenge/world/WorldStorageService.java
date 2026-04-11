package dev.deepcore.challenge.world;

import dev.deepcore.logging.DeepCoreLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Ensures required world storage directories exist for all loaded worlds.
 */
public final class WorldStorageService {
    private final DeepCoreLogger log;

    /**
     * Creates a world storage service.
     *
     * @param log logger used for storage directory warnings
     */
    public WorldStorageService(DeepCoreLogger log) {
        this.log = log;
    }

    /** Ensures required storage directories exist for all loaded worlds. */
    public void ensureAllWorldStorageDirectories() {
        for (World world : Bukkit.getWorlds()) {
            ensureWorldStorageDirectories(world);
        }
    }

    /**
     * Ensures required storage directories exist for the provided world.
     *
     * @param world world whose storage directories should be ensured
     */
    public void ensureWorldStorageDirectories(World world) {
        try {
            Path worldPath = world.getWorldFolder().toPath();
            Files.createDirectories(worldPath);
            Files.createDirectories(worldPath.resolve("data"));
            Files.createDirectories(worldPath.resolve("playerdata"));
            Files.createDirectories(worldPath.resolve("poi"));
            Files.createDirectories(worldPath.resolve("stats"));
            Files.createDirectories(worldPath.resolve("advancements"));
        } catch (Exception ex) {
            log.warn("Could not ensure storage dirs for world " + world.getName() + ": " + ex.getMessage());
        }
    }
}
