package dev.deepcore.challenge.preview;

import java.util.UUID;

/** Represents a tracked preview display entity and its relative coordinates. */
public record PreviewDisplayEntry(UUID entityId, double relX, double relY, double relZ) {}
