package dev.deepcore.challenge.preview;

import java.util.UUID;

/** Represents one preview entity with per-tick destroy animation velocity. */
public record DestroyPreviewEntry(UUID entityId, double vx, double vy, double vz) {}
