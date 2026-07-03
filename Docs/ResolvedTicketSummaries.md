# Resolved Ticket Summaries

---

## Difficulty Setting

### Summary:

Add an Easy, Normal, and Hard difficulty selector to the challenge preparation screen, persist the chosen difficulty when a run is recorded, and display it alongside each entry in the completed runs history.

### Description:

The preparation screen had no way to set the world difficulty before starting a run. Players needed a visible, toggleable selector that cycled through Easy, Normal, and Hard on click. The selected difficulty needed to carry through to the actual Minecraft world when the run started — not just exist as a label — and be saved permanently with each completed run record so past runs could be reviewed with their difficulty context intact. Existing run history required a non-destructive migration so old records were preserved without difficulty data rather than wiped.

---

**Estimated Ideal Time:** 5 hours

---

## Shared Inventory & Armor Sync Bugs

### Summary:

Fix a set of bugs in the shared inventory system where equipping or un-equipping armor corrupted other players' inventory views, item movement between slots lagged by one interaction for other players, and placing a boat caused it to briefly reappear in the placer's inventory.

### Description:

The shared inventory mechanic, which keeps all participants' inventories synchronised in real time, exhibited three distinct failure modes under normal gameplay. First, equipping a piece of armor removed the equivalent armor piece from other players who already had something in that slot, destroying items that should have been untouched. Second, moving an item between inventory slots (for example picking up dirt and placing it elsewhere) caused other players to see the item disappear then reappear one interaction later, creating a persistent one-event lag in their view. Third, placing a boat consumed it from the hand correctly on the placer's side but briefly re-showed it in the placer's inventory before disappearing, which also created a desync for other participants. Each bug had a distinct cause related to when the sync read inventory state relative to when Bukkit actually applied the mutation.

---

**Estimated Ideal Time:** 6 hours

---

## Completed Run Mechanics Display

### Summary:

Show the full list of enabled challenge mechanics for each completed speedrun in the run history screen, so players can see exactly which modifiers were active during any past attempt.

### Description:

The completed runs history screen showed split times and participant names but gave no indication of which challenge mechanics (shared inventory, shared health, degrading inventory, etc.) were active during that run. Players had no way to compare runs across different configurations or understand the context of a past record. The feature needed to capture the active mechanics at the moment the dragon was killed and attach them permanently to the run record. Runs completed before this feature was added needed to display gracefully with a neutral label rather than showing blank or broken data.

---

**Estimated Ideal Time:** 2 hours

---

## Configurable Test Database

### Summary:

Allow the run-records database filename to be set in the server configuration so a separate development database can be used without touching production run history.

### Description:

Testing the completed runs screen or trialling new run configurations required either risking pollution of the live run history or manually moving database files. There was no way to point the plugin at a separate database for development or QA purposes. The feature needed to let server operators specify a different database filename in the configuration file, have that database created and fully migrated on startup if it did not exist, and revert to the production database with a one-line config change. The default behaviour needed to remain unchanged for servers that did not set the option.

---

**Estimated Ideal Time:** 1 hour

---

## Persistent Speedrun Save and Restore

### Summary:

Allow participants to unanimously vote to save the current run state to disk, pause the session, and later restore it exactly — including player positions, inventories, health, and timer state — via an admin command.

### Description:

The existing pause and resume system kept run state only in memory, meaning a server restart or unexpected shutdown lost all run progress. There was no way to save a run mid-session and return to it later. The feature required a unanimous vote system where every online participant had to agree before the save occurred, ensuring no single player could unilaterally pause the run. The saved state needed to capture enough information to reconstruct the run exactly: where each player was, what they were carrying, their health and status effects, the elapsed timer, and which milestones (Nether, Blaze, End) had already been reached. When restored, the run timer needed to account for however long the team spent in the lobby between saving and restoring, so that lobby time was not counted against the final run time. Restore was intentionally restricted to an admin-only command with no in-game GUI item, to prevent accidental or unauthorised restores.

---

**Estimated Ideal Time:** 10 hours
