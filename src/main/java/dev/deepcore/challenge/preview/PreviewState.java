package dev.deepcore.challenge.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

/** Holds mutable runtime state for lobby preview entities and animations. */
public final class PreviewState {
    public final List<PreviewDisplayEntry> lobbyPreviewDisplays = new ArrayList<>();
    public final List<FallingPreviewEntry> activeFallingPreviewDisplays = new ArrayList<>();
    public final List<DestroyPreviewEntry> activeDestroyPreviewEntries = new ArrayList<>();
    public Location previewAnchor;
    public double previewSpinAngleDegrees;
    public boolean initialPreviewDisplayed;
    public boolean previewDestroying;
    public UUID previewSeedDisplayId;
    public String previewSeedText;
    public String previewBiomeText;
    public boolean previewDiscoLabelMode;
    public boolean previewSeedRevealPending;
    public int previewSeedRevealedDigits;
    public int previewSeedRevealElapsedTicks;
    public int previewSeedRevealDurationTicks;
}
