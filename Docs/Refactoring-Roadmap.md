# DeepCore Refactoring Roadmap

## Goal

Reduce complexity in challenge orchestration code, improve readability, and make behavior safer to change with smaller, focused classes.

## Current baseline

- Core orchestration is still concentrated in ChallengeSessionManager.
- Good progress already exists via extracted services:
  - SharedInventorySyncService
  - SharedVitalsService
  - DegradingInventoryService
  - PrepGuiRenderer
  - PrepCountdownService
  - PausedRunStateService
  - LobbySidebarService

## Priority order

1. Extract the largest remaining domains from ChallengeSessionManager.
2. Reduce event-handler noise by routing events through feature-specific listener classes.
3. Introduce explicit state objects for run lifecycle and preview lifecycle.
4. Centralize config keys and remove string duplication.
5. Add targeted tests for each extracted behavior boundary.

## Refactor backlog

### P0: High impact cleanup

1. Extract Preview/Hologram domain into PreviewOrchestratorService.

- Move preview build, destroy, spin, seed reveal, and display-entity lifecycle logic.
- Move related fields from manager into a single PreviewState object.
- Keep manager responsible only for calling high-level preview actions.

2. Extract Portal and dimension routing into PortalRoutingService.

- Move methods that resolve linked worlds, end platform creation, and portal transit targets.
- Keep all cross-world mapping and cooldown checks in one place.

3. Extract RunProgressService.

- Move world progression markers (nether reached, blaze objective, end reached, dragon killed).
- Move objective text and split calculation support.
- Expose immutable snapshot API for sidebar/action bar rendering.

4. Extract ActionBarTickerService.

- Move task scheduling for run status/action bar updates.
- Keep the manager free of repeating timer mechanics.

5. Extract RespawnRoutingService.

- Move pending respawn world tracking and run/lobby respawn resolution.
- Keep death/respawn event handlers small and declarative.

6. Split event handlers into dedicated listeners.

- Create one listener per domain:
  - SessionLifecycleListener
  - InventoryMechanicsListener
  - SharedVitalsListener
  - PortalTransitListener
  - PreviewListener
- Keep ChallengeSessionManager as coordinator only.

### P1: Readability and maintainability

7. Replace scattered config path constants with typed config accessor.

- Create ChallengeConfigView with methods like previewEnabled(), countdownSeconds(), degradingMinSlots().
- Remove direct string-path lookups from hot paths.

8. Introduce SessionState aggregate.

- Group run timestamps, phase, paused timings, and elimination sets.
- Reduce constructor and field sprawl in manager.

9. Introduce immutable value objects for repeated concepts.

- SplitTimes
- RunProgressSnapshot
- SidebarModel
- PreviewAnchorConfig

10. Normalize scheduler lifecycle handling.

- Replace repeated cancelTask patterns with TaskGroup helper.
- One place to register, cancel, and clear all per-phase tasks.

11. Reduce repeated online participant scans.

- Introduce ParticipantsView with filtered views:
  - onlineParticipants()
  - activeParticipants()
  - sharedVitalsParticipants()

12. Move prep border logic into PrepAreaService.

- Keep border apply, clamp, and area checks together.

13. Move prep book operations into PrepBookService.

- Keep key creation, item construction, detection, and give/remove logic in one class.

14. Consolidate world naming and limbo checks.

- Create WorldClassificationService for limbo, lobby, active run world classification.

15. Eliminate direct static Bukkit calls in deeper services where possible.

- Pass small interfaces or adapters to improve testability.

### P2: Design consistency and polish

16. Standardize naming and intent.

- Use names that indicate side effects, for example:
  - scheduleSharedInventorySync -> requestSharedInventorySync
  - maybeStartCountdown -> tryStartCountdown

17. Flatten long methods into command-style steps.

- Example for startRun:
  - validateStartPreconditions
  - transitionToRunningPhase
  - prepareParticipantsForRun
  - launchRunTasks

18. Replace magic numbers with grouped constants objects.

- Example groups:
  - PreviewTuning
  - SidebarLayout
  - EndPlatformLayout

19. Move record formatting to dedicated formatter classes.

- RunRecordFormatter for timestamp, split display, participant list display.

20. Add package-level architecture notes.

- Explain class responsibilities and dependency direction in challenge package docs.

## Completed package moves

- `dev.deepcore.records` → `dev.deepcore.challenge.records` (RunRecord, RunRecordsService + tests)
- `dev.deepcore.challenge.{ChallengeCommand,LobbyCommand,ChallengeCoreCommandHandler,ChallengeLogsCommandHandler,ChallengeAdminFacade}` → `dev.deepcore.challenge.command` (+ tests)
- `dev.deepcore.challenge.PrepGuiPage` → `dev.deepcore.challenge.ui.PrepGuiPage`

## Suggested target package layout

- dev.deepcore.challenge.session
- dev.deepcore.challenge.events
- dev.deepcore.challenge.inventory
- dev.deepcore.challenge.vitals
- dev.deepcore.challenge.preview
- dev.deepcore.challenge.portal
- dev.deepcore.challenge.ui
- dev.deepcore.challenge.world
- dev.deepcore.challenge.config
- dev.deepcore.challenge.records
- dev.deepcore.challenge.command

## Safe execution sequence

1. PreviewOrchestratorService extraction.
2. PortalRoutingService extraction.
3. RunProgressService extraction.
4. ActionBarTickerService extraction.
5. RespawnRoutingService extraction.
6. Listener split by domain.
7. SessionState and ChallengeConfigView introduction.
8. Remaining P1 and P2 cleanup.

## Definition of done for each extraction

- Build passes with gradlew classes.
- No behavior drift in manual smoke tests:
  - prep flow and ready countdown
  - shared inventory and shared vitals
  - portal travel and respawn routing
  - preview build/destroy animations
  - sidebar and action bar updates
- Manager class size and field count both decrease after each slice.
- New code has clear responsibility boundaries and minimal cross-service leakage.

## Suggested first implementation slice

Extract PreviewOrchestratorService first. It has the highest complexity concentration and gives the largest readability gain with minimal functional risk if done incrementally.
