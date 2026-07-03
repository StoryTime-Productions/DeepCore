# Training World Gym Plan

## Objective

Create a dedicated training world ("Gym") where players can practice core speedrun techniques through repeatable mini-challenges, receive immediate timing feedback, and track recent and best performance over time.

## Core Experience Requirements

- Players can enter the training gym through `/challenge train`.
- The gym contains multiple challenge regions, each mapped to one mini-challenge.
- Players can start a challenge quickly, reset quickly, and retry repeatedly.
- During a challenge, the action bar shows the active objective and a live timer.
- Outside an active challenge, the sidebar shows performance stats for the region the player is currently standing in.
- Outside challenge regions (while not in the training world), no gym sidebar is shown.
- Inside training world but not in an active challenge, sidebar is shown and displays `Current Mini-challenge: NONE` when not in a mapped mini-challenge region.
- Stats are persisted per player and per challenge, including:
- Last 5 tries.
- Best try (personal best).

## Mini-Challenge Set (Phase 1)

### 1) Nether Portal Build Drill

Goal:
Build and light a nether portal using speedrun-style lava pool + water source techniques.

Success conditions:

- Player creates a valid lit portal frame within the challenge area.
- All required actions are done within challenge constraints (defined below).

Configured constraints:

- Start inventory is standardized for practice and includes only a water bucket and flint and steel.
- Arena contains a prebuilt lava pool setup.
- Timer starts only when the player presses the start button and is teleported to the configured challenge start coordinates.
- Timer stops when a lit nether portal is detected in the arena region bounds.
- On completion, arena is reset immediately.

### 2) Rapid Crafting Drill

Goal:
Craft a target list quickly (beds, tools, eyes of ender) from provided materials.

Success conditions:

- Player crafts all target outputs before timer expires (if time limit enabled).

Configured target set:

- Beds: random count between 5 and 8 per attempt.
- Tools: one axe and one shovel of any valid material tier.
- Eyes of ender: random count between 7 and 9 per attempt.

Configured constraints:

- Materials are generated using one of three supply profiles per attempt: `sufficient`, `generous`, or `over-provisioned`.

### 3) Chest Looting Drill

Goal:
Transfer all items from one or more chests into player inventory as fast as possible.

Success conditions:

- Source chest set is fully emptied.
- No ordering requirement.

Configured constraints:

- Fixed chest contents per template.
- Optional randomization presets for advanced practice.

### 4) Bridge Drill

Goal:
Bridge from one platform to another as quickly and safely as possible.

Supported variants:

- Flat bridge (same Y-level).
- Rising bridge (target platform at higher Y-level).

Success conditions:

- Player reaches and activates the destination pressure plate within bounds.

Configured constraints:

- Attempt starts from a start button and teleports player to configured start coordinates.
- Completion trigger is pressure-plate based.
- Arena resets after completion or cancellation.
- Challenge definitions are extensible so additional mini-challenges can be added without redesigning core state/persistence systems.

## Player Entry and Commands

Primary command:

- `/challenge train` (teleport player to training world lobby/spawn)

Suggested command set:

- `/challenge train` -> enter gym lobby
- `/challenge train leave` -> return to previous world/spawn
- `/challenge train start <challenge>` -> start selected challenge immediately
- `/challenge train reset` -> reset current challenge arena and timer
- `/challenge train stats [challenge]` -> open personal stats view

Admin commands (optional):

- `/challengeadmin train setregion <challenge> <id>`
- `/challengeadmin train reload`
- `/challengeadmin train cleardata <player> [challenge]`

Permissions:

- All players have access to gym player commands by default.

## Region and Flow Design

- The training world is split into named regions, each linked to one challenge type.
- Entering a region updates the player's contextual HUD (sidebar when idle).
- Players can join challenge attempts from region start pads, NPCs, or command shortcuts.
- Timer begins only after pressing a region start button, then teleporting to challenge start coordinates.
- Leaving or disconnecting during an active attempt cancels the attempt.
- Only one active attempt per challenge arena at a time (shared arena model).

Recommended states per player:

- `IDLE` (not in challenge)
- `IN_CHALLENGE`
- `COMPLETED`
- `CANCELLED`

## Timing and HUD Behavior

### Action Bar (during active challenge)

Show continuously:

- Challenge name
- Current objective text
- Live elapsed timer (mm:ss.SS)

Example:

- `Portal Drill | Objective: Light a valid portal | 00:27.41`

### Sidebar (when not in active challenge)

Show content based on the region the player is currently in:

- Region challenge name
- Current mini-challenge name (`NONE` if no mapped region while still in training world)
- Personal best
- Last five tries (most recent first)

Example sidebar:

- `Gym Training`
- `Current Mini-challenge: Portal`
- `Best: 00:24.91`
- `Try 1: 00:25.30`
- `Try 2: 00:26.88`
- `Try 3: 00:24.91`
- `Try 4: 00:28.50`
- `Try 5: 00:27.14`

If no tries exist yet:

- Show `Best: --`
- Show `No attempts yet`

## Persistence Requirements

Persist per player, per challenge:

- `bestTimeMs`
- `lastFiveAttemptsMs` (ring buffer or capped list)
- `attemptCount`
- `lastAttemptAt`

Scope:

- Stats are global per player (not season-scoped).

Behavior rules:

- On completion, record attempt time.
- Keep only latest 5 attempt times.
- Update best time if new time is lower.
- Record completed attempts only (cancelled attempts are not stored in history).
- Data survives restart and world unload/reload.

Storage options:

- Preferred: plugin database layer (if challenge data store already exists).
- Alternative: dedicated YAML/JSON files per player UUID.

## Challenge Completion and Validation

Validation events:

- Portal drill: detect valid lit portal creation in arena bounds.
- Crafting drill: track crafted item counts against objective set.
- Chest drill: detect all tracked container slots empty.
- Bridge drill: detect destination platform trigger reached within arena bounds.

Anti-abuse guards:

- Ignore completions outside registered challenge arenas.
- Ignore item events not tied to active attempt.
- Reset arena inventory/state between attempts.
- Enforce single active player/attempt per shared challenge arena.

## Integration with Existing Speedrun UX

- Reuse existing timer formatting and HUD update cadence from speedrun world systems.
- Reuse action bar style for objective + timer to keep experience consistent.
- Reuse challenge/session lifecycle components where practical.
- Keep gym challenges isolated from full-run scoring logic.

## Configuration Additions

Suggested config keys:

- `gym.enabled: true`
- `gym.world: deepcore_gym`
- `gym.enter-command: challenge train`
- `gym.sidebar.idle-enabled: true`
- `gym.actionbar.active-enabled: true`
- `gym.attempt-history-size: 5`
- `gym.portal-drill.enabled: true`
- `gym.crafting-drill.enabled: true`
- `gym.chest-drill.enabled: true`
- `gym.bridge-drill.enabled: true`
- `gym.auto-reset-on-complete: true`
- `gym.leave-cancels-attempt: true`
- `gym.disconnect-cancels-attempt: true`
- `gym.timer.format: mm:ss.SS`
- `gym.start.mode: button-teleport`
- `gym.crafting.beds.min: 5`
- `gym.crafting.beds.max: 8`
- `gym.crafting.tools.accept-any-tier: true`
- `gym.crafting.eyes-of-ender.min: 7`
- `gym.crafting.eyes-of-ender.max: 9`
- `gym.crafting.material-profiles: [sufficient, generous, over-provisioned]`
- `gym.bridge.completion-trigger: pressure-plate`

## Implementation Breakdown

1. World and command scaffolding

- Register `/gym` command and teleport flow.
- Register `/challenge train` command and teleport flow.
- Add gym lobby spawn and return location handling.

2. Region mapping and state machine

- Define challenge regions and per-player challenge state.
- Hook region enter/exit events.

3. Timer and HUD integration

- Add action bar objective + live timer for active attempts.
- Add sidebar idle stats view based on current region challenge.

4. Mini-challenge validators

- Implement portal completion detection.
- Implement crafting target tracking.
- Implement chest-empty tracking.
- Implement bridge destination trigger tracking.

5. Persistence layer

- Add per-player/per-challenge attempt store.
- Enforce last-5 history and personal best updates.

6. Reset and reliability

- Ensure clean challenge reset between attempts.
- Handle disconnect/reconnect and server restart safely.
- Enforce shared-arena single-run lock.

## Testing Plan

Manual scenario checks:

- `/challenge train` enters training lobby correctly.
- Starting each challenge shows objective + running timer on action bar.
- Completing each challenge records attempt and updates best time.
- Attempt history never exceeds five entries.
- Sidebar in each region shows matching challenge name + player stats while idle.
- Sidebar is hidden outside training world and shows `Current Mini-challenge: NONE` when in training world but outside mapped challenge regions.
- Restart server and verify stats remain persisted.
- Abandon, leave, or disconnect during challenge and verify cancel behavior with no attempt recorded.

Edge cases:

- Player disconnects mid-attempt.
- Multiple players contending for one shared arena (lock behavior and messaging).
- Region boundary flicker does not spam sidebar or break state.
- Arena reset occurs even after invalid completion attempt.

## Open Decisions

- Whether ghost replay or split times are desired in a later phase.
- Whether to support global leaderboard beyond personal stats.

## Definition of Done

- Players can enter gym via `/challenge train` and run all four mini-challenges.
- Active challenge always shows objective + timer on action bar.
- Idle-in-region sidebar always shows challenge name, last five tries, and best time.
- Sidebar behavior matches training world bounds rules and shows `NONE` when applicable.
- Attempts and personal best are persisted across restarts.
- Feature is configurable and can be toggled safely.
