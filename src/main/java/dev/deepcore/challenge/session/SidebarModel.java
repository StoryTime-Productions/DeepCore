package dev.deepcore.challenge.session;

/**
 * Immutable sidebar data prepared by the session layer before UI rendering.
 */
public record SidebarModel(
        long bestOverall,
        long bestOverworldToNether,
        long bestNetherToBlaze,
        long bestBlazeToEnd,
        long bestNetherToEnd,
        long bestEndToDragon,
        int onlineCount,
        String phaseText,
        int readyCount) {}
