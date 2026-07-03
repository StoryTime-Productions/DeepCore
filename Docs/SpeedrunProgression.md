# DeepCore Speedrun Difficulty Progression

Objective: spawn → kill the Ender Dragon.

Each tier adds one or more mechanics that compound on the previous ones. Configurations are ordered from least punishing to most punishing. Every configuration is a valid preset in the system (`ChallengeMode` key listed for reference).

---

## Mechanics Reference

| Mechanic | OFF means | ON means |
|---|---|---|
| **Keep Inventory** | Items drop on death | Items are kept on death |
| **Hardcore** | Unlimited deaths | Run ends on first death |
| **Health Refill** | No natural regen (potions/beacons still heal) | Passive regen from full hunger bar |
| **Shared Inventory** | Each player has independent items | All players draw from and deposit into a single shared pool |
| **Shared Health** | Each player has independent HP | All players share one HP pool; any hit on anyone damages everyone |
| **Degrading Inventory** | Inventory slots are fixed | Available slots shrink over time, eventually capping at 5 |
| **Initial Half Heart** | Run starts at full HP | Run starts at 0.5 HP per life (mutually exclusive with Health Refill) |

> **Note on Health Refill OFF:** only passive saturation-based regen is blocked. Regeneration status effects (potions, beacons, golden apples) still apply and are the primary healing source once natural regen is disabled.

---

## Tier 1 — Practice Mode

**Mode key:** `keep_inventory_non_hardcore`

| Mechanic | State |
|---|---|
| Keep Inventory | ON |
| Hardcore | OFF |
| Health Refill | ON |
| Shared Inventory | OFF |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** Pure execution practice. Items are kept on every death, health refills naturally, and deaths have no run-ending consequence. Best used for learning routes, practicing combat, or familiarising with the plugin's mechanics before attempting a real run.

**Key pressure:** None. This is a training configuration.

---

## Tier 2 — Standard

**Mode key:** `lose_inventory_non_hardcore`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | OFF |
| Health Refill | ON |
| Shared Inventory | OFF |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** Classical Minecraft. Dying loses all carried items, but the run continues. Health refills naturally between fights. The main risk is losing a critical item (sword, pearls, blaze rods) on a bad death and spending time recovering.

**Step up from Tier 1:** Item loss on death makes every fight consequential without ending the run outright.

---

## Tier 3 — Standard with Shared Inventory

**Mode key:** `lose_inventory_non_hardcore_shared_inventory`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | OFF |
| Health Refill | ON |
| Shared Inventory | ON |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** Tier 2 but the team operates from a single item pool. One player picking up iron is iron available to everyone; one player dying drops the shared supply. Team coordination and awareness of who is carrying what becomes essential. A careless death can strip the team of critical crafting materials mid-run.

**Step up from Tier 2:** Inventory is no longer isolated per player. A bad death from one member affects everyone's resource availability.

---

## Tier 4 — Hardcore Classic

**Mode key:** `hardcore_health_refill`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | ON |
| Shared Inventory | OFF |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** One life. Health still refills naturally, so the pacing feels similar to standard Minecraft, but every mistake carries the possibility of ending the run entirely. Fights that were previously recoverable (gravel fall, creeper, skeleton barrage) now require full attention.

**Step up from Tier 3:** Introducing the concept of a finite run. Any death ends it. Health regen provides a safety buffer between engagements.

---

## Tier 5 — Hardcore, No Natural Regen

**Mode key:** `hardcore_no_refill`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | OFF |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** One life, and health does not recover on its own. Every heart of damage taken is permanent unless actively healed with a regeneration potion, golden apple, or beacon. Resource management shifts significantly: healing items become as important as weapons and food. Players must route around obtaining healing supplies while maintaining speed.

**Step up from Tier 4:** Damage is now a resource. Taking a hit in the Nether is a choice that costs healing materials, not just time.

---

## Tier 6 — Hardcore, No Regen, Shared Inventory

**Mode key:** `hardcore_no_refill_shared_inventory`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | ON |
| Shared Health | OFF |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** Tier 5 but the healing resource pool is now shared. One player burning through golden apples forces every other player to operate on a thinner supply. Potions brewed by one player are available to all, which rewards splitting roles (one player prioritising Nether Wart and Blaze Powder while others gather materials), but punishes uncoordinated consumption.

**Step up from Tier 5:** Healing supplies and all other items are shared across the team. Wasteful play by any one member degrades everyone's safety margin.

---

## Tier 7 — Shared Health, Natural Regen

**Mode key:** `hardcore_shared_health`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | ON |
| Shared Inventory | OFF |
| Shared Health | ON |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** All players draw from the same health pool. A skeleton hitting Player A also drains health from Player B. The effective HP ceiling is 20 (not 20 per player), so the team must behave like a single entity in combat. Natural regen provides recovery between engagements, which provides some breathing room, but simultaneous combat across multiple players can overwhelm the pool faster than it refills.

**Step up from Tier 6:** Combat decisions by one player directly and immediately affect every other player's survivability. Splitting up to fight separate enemies simultaneously is now extremely dangerous.

---

## Tier 8 — Shared Health, No Regen

**Mode key:** `hardcore_shared_health_no_refill`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | OFF |
| Shared Health | ON |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** Tier 7 without the safety net of natural regen. Every hit to any player is permanent until actively healed. The shared pool means a careless moment anywhere on the map — a mob while mining, an unexpected creeper in the Nether — drains everyone. Healing resources must be actively manufactured and rationed.

**Step up from Tier 7:** Damage is permanent and shared. Combat avoidance and passive regen from food is no longer an option. Every engagement must be considered against the cost of the shared HP it will consume.

---

## Tier 9 — Shared Health, No Regen, Shared Inventory

**Mode key:** `hardcore_shared_health_no_refill_shared_inventory`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | ON |
| Shared Health | ON |
| Degrading Inventory | OFF |
| Initial Half Heart | OFF |

**What this feels like:** The full co-op pressure configuration. HP is shared and permanent; all items including healing materials are in one pool. Every potion, every golden apple, and every food item is a shared resource competing between immediate healing need and future combat. One person pulling pork chops from the shared pool while another is desperately low on health can cost the run. Role specialisation (combat, supply, logistics) becomes almost mandatory.

**Step up from Tier 8:** Combines the shared HP pressure with shared supply pressure. Every system the team relies on (health, items, crafting materials) is communal and finite.

---

## Tier 10 — Shared Health, No Regen, Degrading Inventory

**Mode key:** `hardcore_shared_health_no_refill_degrading_inventory`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | OFF |
| Shared Health | ON |
| Degrading Inventory | ON |
| Initial Half Heart | OFF |

**What this feels like:** Tier 8 with an added clock. As time passes, each player's available inventory slots shrink, eventually capping at 5 slots. Items in locked slots are lost. Slower runs are self-penalising: the team that takes an hour collecting iron will reach the End with far less carrying capacity than a team that pushes efficiently. Speed is no longer just about the timer — it is a survival mechanic.

**Step up from Tier 9:** Introduces a hard time pressure that compounds every other constraint. Taking longer to complete the run actively degrades the team's ability to carry the resources needed to finish it.

---

## Tier 11 — Full DeepCore (Maximum Difficulty)

**Mode key:** `hardcore_shared_health_no_refill_degrading_inventory_initial_half_heart`

| Mechanic | State |
|---|---|
| Keep Inventory | OFF |
| Hardcore | ON |
| Health Refill | OFF |
| Shared Inventory | OFF |
| Shared Health | ON |
| Degrading Inventory | ON |
| Initial Half Heart | ON |

**What this feels like:** Everything at once. The run begins with the shared HP pool at 0.5 HP. A single arrow, fall, or contact with fire ends the run before any gear is obtained. The opening minutes require playing at near-zero health while gathering enough materials to begin healing — in a world where healing is non-trivial and inventory space is already counting down. Every subsequent mechanic from every previous tier stacks simultaneously.

**Why this is the hardest:** The vulnerability of the initial half-heart creates an extremely fragile opening that cannot be skipped or prepared for. Surviving the first few minutes requires near-perfect play under conditions that would end most runs at Tier 4 or below. If the team survives that window, they then face the full weight of permanent shared damage, no passive recovery, and a shrinking inventory with a hard deadline.

---

## Progression Summary

| Tier | Name | Deaths | Items on Death | Regen | Shared Inv | Shared HP | Degrading Inv | Half Heart |
|---|---|---|---|---|---|---|---|---|
| 1 | Practice | Unlimited | Kept | Natural | — | — | — | — |
| 2 | Standard | Unlimited | Lost | Natural | — | — | — | — |
| 3 | Standard + Shared Inv | Unlimited | Lost | Natural | Shared | — | — | — |
| 4 | Hardcore Classic | 1 life | Lost | Natural | — | — | — | — |
| 5 | Hardcore No Regen | 1 life | Lost | Potions only | — | — | — | — |
| 6 | Hardcore No Regen + Shared Inv | 1 life | Lost | Potions only | Shared | — | — | — |
| 7 | Shared Health + Regen | 1 life | Lost | Natural | — | Shared | — | — |
| 8 | Shared Health No Regen | 1 life | Lost | Potions only | — | Shared | — | — |
| 9 | Shared Health + Shared Inv | 1 life | Lost | Potions only | Shared | Shared | — | — |
| 10 | Degrading Inventory | 1 life | Lost | Potions only | — | Shared | Shrinking | — |
| 11 | Full DeepCore | 1 life | Lost | Potions only | — | Shared | Shrinking | 0.5 HP |
