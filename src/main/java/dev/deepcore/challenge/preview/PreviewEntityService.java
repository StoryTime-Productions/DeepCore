package dev.deepcore.challenge.preview;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

/** Provides lifecycle helpers for tracked preview display entities. */
public final class PreviewEntityService {
    /**
     * Returns whether the entity id resolves to a currently valid entity.
     *
     * @param entityId entity UUID to resolve
     * @return true when the entity exists and is valid
     */
    public boolean isLive(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid();
    }

    /**
     * Removes a live entity by id when it exists and is valid.
     *
     * @param entityId entity UUID to remove
     */
    public void removeLive(UUID entityId) {
        if (entityId == null) {
            return;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /**
     * Returns true when any referenced entity is live and prunes invalid entries.
     *
     * @param entries     tracked entries containing entity references
     * @param idExtractor extractor that maps an entry to its entity UUID
     * @param <T>         tracked entry type
     * @return true when at least one entry still points to a live entity
     */
    public <T> boolean hasAnyLiveAndPruneInvalid(List<T> entries, Function<T, UUID> idExtractor) {
        Iterator<T> iterator = entries.iterator();
        while (iterator.hasNext()) {
            T entry = iterator.next();
            UUID entityId = idExtractor.apply(entry);
            if (isLive(entityId)) {
                return true;
            }
            iterator.remove();
        }

        return false;
    }

    /**
     * Removes block/text preview entities that contain the configured tag.
     *
     * @param world            world to scan for preview entities
     * @param previewEntityTag scoreboard tag used to identify preview entities
     */
    public void removeTaggedPreviewEntities(World world, String previewEntityTag) {
        if (world == null) {
            return;
        }

        for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
            if (display.getScoreboardTags().contains(previewEntityTag)) {
                display.remove();
            }
        }

        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getScoreboardTags().contains(previewEntityTag)) {
                display.remove();
            }
        }
    }
}
