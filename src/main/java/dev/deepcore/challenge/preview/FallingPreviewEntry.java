package dev.deepcore.challenge.preview;

import java.util.UUID;

/**
 * Represents one preview block entity currently animating downward into place.
 */
public record FallingPreviewEntry(
        UUID entityId,
        long startTimeMillis,
        long durationMillis,
        double fallHeight,
        double blockScale,
        float rotationRadians) {}
