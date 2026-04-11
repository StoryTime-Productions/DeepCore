package dev.deepcore.challenge.preview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PreviewEntityServiceTest {

    @Test
    void isLive_handlesNullMissingAndValidEntities() {
        PreviewEntityService service = new PreviewEntityService();
        UUID id = UUID.randomUUID();
        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            assertFalse(service.isLive(null));

            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(null);
            assertFalse(service.isLive(id));

            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(entity);
            assertTrue(service.isLive(id));
        }
    }

    @Test
    void removeLive_removesOnlyValidEntity() {
        PreviewEntityService service = new PreviewEntityService();
        UUID id = UUID.randomUUID();
        Entity validEntity = mock(Entity.class);
        Entity invalidEntity = mock(Entity.class);
        when(validEntity.isValid()).thenReturn(true);
        when(invalidEntity.isValid()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(validEntity);
            service.removeLive(id);
            verify(validEntity).remove();

            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(invalidEntity);
            service.removeLive(id);
            verify(invalidEntity, never()).remove();

            service.removeLive(null);
        }
    }

    @Test
    void hasAnyLiveAndPruneInvalid_prunesUntilFirstLive() {
        PreviewEntityService service = new PreviewEntityService();
        UUID stale = UUID.randomUUID();
        UUID live = UUID.randomUUID();
        UUID untouched = UUID.randomUUID();

        Entity staleEntity = mock(Entity.class);
        Entity liveEntity = mock(Entity.class);
        when(staleEntity.isValid()).thenReturn(false);
        when(liveEntity.isValid()).thenReturn(true);

        List<UUID> entries = new ArrayList<>(List.of(stale, live, untouched));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(stale)).thenReturn(staleEntity);
            bukkit.when(() -> Bukkit.getEntity(live)).thenReturn(liveEntity);

            boolean anyLive = service.hasAnyLiveAndPruneInvalid(entries, id -> id);
            assertTrue(anyLive);
            assertTrue(entries.contains(live));
            assertTrue(entries.contains(untouched));
            assertFalse(entries.contains(stale));
        }
    }

    @Test
    void hasAnyLiveAndPruneInvalid_returnsFalseWhenNoneLive() {
        PreviewEntityService service = new PreviewEntityService();
        UUID id = UUID.randomUUID();
        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(false);
        List<UUID> entries = new ArrayList<>(List.of(id));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(entity);
            assertFalse(service.hasAnyLiveAndPruneInvalid(entries, value -> value));
            assertTrue(entries.isEmpty());
        }
    }

    @Test
    void removeTaggedPreviewEntities_removesTaggedDisplays() {
        PreviewEntityService service = new PreviewEntityService();
        World world = mock(World.class);

        BlockDisplay taggedBlock = mock(BlockDisplay.class);
        BlockDisplay untaggedBlock = mock(BlockDisplay.class);
        TextDisplay taggedText = mock(TextDisplay.class);
        TextDisplay untaggedText = mock(TextDisplay.class);

        when(taggedBlock.getScoreboardTags()).thenReturn(Set.of("preview"));
        when(untaggedBlock.getScoreboardTags()).thenReturn(Set.of("other"));
        when(taggedText.getScoreboardTags()).thenReturn(Set.of("preview"));
        when(untaggedText.getScoreboardTags()).thenReturn(Set.of("other"));

        when(world.getEntitiesByClass(BlockDisplay.class)).thenReturn(List.of(taggedBlock, untaggedBlock));
        when(world.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of(taggedText, untaggedText));

        service.removeTaggedPreviewEntities(world, "preview");

        verify(taggedBlock).remove();
        verify(taggedText).remove();
        verify(untaggedBlock, never()).remove();
        verify(untaggedText, never()).remove();
    }

    @Test
    void removeTaggedPreviewEntities_noopsForNullWorld() {
        PreviewEntityService service = new PreviewEntityService();
        service.removeTaggedPreviewEntities(null, "preview");
    }
}
