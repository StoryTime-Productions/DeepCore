# Holographic Chunk Preview Plan

## Objective

Create a holographic 3D terrain preview above a pedestal in the prep area using display entities, so players can preview a randomized speedrun start terrain before the challenge begins.

## Core Experience Requirements

- Players join directly into the limbo world by default.
- Limbo acts as a proper lobby with a central pedestal.
- The pedestal shows a holographic preview of the generated run world spawn surface.
- Preview can either rotate slowly or remain static, controlled by config.
- Players can regenerate the run world from lobby controls, and the pedestal preview must immediately refresh to match the new spawn surface.
- Countdown does not begin until all active players are marked ready.
- The ready book is removed only once all players are ready and countdown begins.

## Scope (Phase 1)

- Render a 16 x 16 spawn-surface preview (with optional depth layers for richer 3D effect).
- Build preview from generated world data tied to a selected/random seed.
- Show preview in limbo lobby during PREP and COUNTDOWN phases.
- Add lobby/prep action to regenerate preview with a new random seed.
- Cleanly remove all preview entities when challenge starts, resets, or plugin disables.

## Non-Goals (Phase 1)

- Full biome visualization beyond block identity.
- Animated terrain transitions.
- Multi-chunk or zoomable map views.
- Persistent previews across server restarts.

## Technical Direction

Use Paper display entities (BlockDisplay) as the primary rendering path.

Why this approach:

- Purpose-built for visual block rendering without world block edits.
- Easy cleanup (track spawned entities and remove).
- Allows transform/scaling if we want compact presentation later.

Fallback strategy:

- If Display entities are unavailable in the running server API, skip preview generation and show a clear admin warning in logs.

## Proposed Components

### 1) Preview data extraction service

Responsibilities:

- Generate or load temporary terrain context for a seed.
- Read block states for region 16 x 16 x 5.
- Return a compact block-state matrix.

Suggested class:

- TerrainPreviewSampler

Key API:

- sample(seed, originX, originZ, yStart, yLayers) -> PreviewVolume

### 2) Hologram rendering service

Responsibilities:

- Convert PreviewVolume blocks to BlockDisplay entities.
- Place them relative to a pedestal anchor location.
- Track spawned entity UUIDs by session.
- Remove/rebuild hologram on demand.

Suggested class:

- HolographicPreviewRenderer

Key API:

- render(volume, anchor)
- clear()
- rebuild(volume, anchor)

### 3) Session integration

Responsibilities:

- Route all joining players into limbo lobby by default.
- Trigger first preview creation on prep start (or immediately after world regeneration in lobby).
- Regenerate when user clicks prep/lobby action, then rebuild preview from the new world spawn surface.
- Keep ready workflow active in limbo.
- Start countdown only when all active players are ready.
- Remove ready book only at countdown start.
- Clear preview on challenge start/end/reset.

Suggested touchpoints:

- ChallengeSessionManager phase transitions
- Prep GUI click handler

### 4) Seed management

Responsibilities:

- Keep current preview seed in session state.
- Generate cryptographically reasonable random long when regenerating.
- Optionally display seed in action bar/chat for transparency.

Suggested touchpoints:

- ChallengeSessionManager session fields

## Coordinate and Layout Plan

- Input region: local chunk coordinates x:[0..15], z:[0..15] around spawnpoint.
- Surface mode (default): sample the topmost solid/terrain block per x/z column.
- Depth mode (optional): include N layers below sampled surface for stronger 3D readability.
- Anchor: pedestal top center in limbo/prep world.
- Placement: map each sample block to anchor + (x, y, z).
- Optional compact mode (future): apply display scale (e.g. 0.5) and spacing transforms.
- Rotation mode: if enabled, rotate display root slowly around Y axis at configurable speed.

## Performance Budget and Safeguards

- Max displays per render: 1280 (16*16*5).
- Skip AIR blocks to reduce entities (expected significant reduction).
- Hard cap: if non-air display count exceeds configured threshold, abort render and log warning.
- Build hologram in small scheduled batches (e.g. 100-200 entities per tick) to avoid tick spikes.
- Clear previous render before creating a new one.

## Config Additions

Add under challenge config:

- preview_hologram_enabled: true
- preview_hologram_surface_mode: true
- preview_hologram_layers: 1
- preview_hologram_base_y_offset: 0
- preview_hologram_max_entities: 1500
- preview_hologram_batch_size: 150
- preview_hologram_spin_enabled: true
- preview_hologram_spin_degrees_per_second: 8.0
- lobby_spawn_in_limbo_by_default: true
- countdown_requires_all_ready: true
- remove_ready_book_on_countdown_start: true

## Failure Handling

- If terrain sampling fails: notify operator, keep session functional, disable only preview feature for that run.
- If rendering partially fails: clear spawned entities for that render attempt and retry once.
- If world/chunk unavailable: queue retry after short delay during prep.

## Testing Plan

### Unit-ish logic tests (where practical)

- Matrix bounds and coordinate mapping validation.
- AIR filtering logic and entity-count cap logic.
- Seed regeneration changes value and updates state.

### In-server verification

- Join server: player lands in limbo lobby by default.
- Start prep/lobby idle: preview appears above pedestal.
- Click regenerate: old hologram removed, new one appears matching new spawnpoint surface.
- Spin enabled: preview rotates slowly.
- Spin disabled: preview remains fixed.
- Mark players ready: countdown starts only when all active players are ready.
- Countdown start: ready book is removed at this point (not earlier).
- Start run: preview removed immediately.
- Reset run: preview can be generated again.
- Multiple players online: same shared preview visibility.
- Performance check: no major lag spike during render batches.

## Milestone Breakdown

1. Scaffolding

- Add plan classes and session state hooks.
- Add config keys with defaults.

2. Sampling

- Implement PreviewVolume and sampler for 16 x 16 x 5.
- Validate sampled block counts and bounds.

3. Rendering

- Implement BlockDisplay spawning, tracking, and cleanup.
- Add batch rendering and caps.
- Add optional spin scheduler/transform updates.

4. Integration

- Wire into limbo join/prep/regenerate/start-run/reset lifecycle.
- Add prep/lobby control for regenerate.
- Enforce all-ready gate before countdown.
- Remove ready book exactly on countdown start.

5. Verification

- Run gradle tests.
- Manual server test pass using minecraft-test instance.

## Open Decisions

- Exact pedestal anchor location in prep world.
- Whether to include transparent blocks (water/leaves) in Phase 1 surface sampling.
- Whether seed should be shown to players or admins only.
- Whether preview should be per-team or global shared.
- What counts as active players for all-ready gating (online only vs participants only).

## Definition of Done

- A visible 3D holographic terrain preview appears in prep.
- Regenerate action replaces it with a seed-different preview.
- Preview is always cleaned up at run start/reset/disable.
- Feature can be disabled via config.
- Build/tests pass and manual in-server checks succeed.
