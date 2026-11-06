# Changelog

## 1.0.7

- Fix Nether portal ignition in the active run's custom Overworld and Nether.
  Apply the existing run-dimension mapping to Minecraft's shared fire/portal
  ignition check, including side clicks with flint and steel or fire charges.
- Preserve vanilla frame validation, coordinate scaling, paired destinations,
  ordinary fire, and the restriction against Nether portals in the End or
  unrelated custom dimensions. Existing runs need no migration or reset.
- Add a real Fabric server regression suite for ignition and round-trip travel.

## 1.0.6

- Built-in persistent vanilla death counter across runs and restarts.
- Add operator `/scoreboard show`, `hide`, `reset [player]`, `set <player> <count>`,
  `get <player>`, `list`, and `help` shortcuts.
- Preserve unrelated objectives and vanilla scoreboard commands. Hide/show state
  persists; offline players are included in a full counter reset.

## 1.0.5

- Add operator-only `/nextseed <seed>` for the next map, then return to random seeds.
  Numeric and text seeds use Minecraft's normal parsing, including zero and negative values.
- Allow replacing a queued seed during an active run; reject changes after the reset
  countdown starts. Failed generation/transfer retries retain the selected map's seed.
- Remove the "New world ready!" completion title and clear reset titles after transfer.
- Check one-shot seed behavior and command permissions with 21 controller/state tests
  and four command integration checks using Minecraft's actual APIs.

## 1.0.4

- Allow Fabric API 0.165.0+26.3 and newer instead of requiring exactly 0.165.0+26.3.
- Keep Minecraft restricted to 26.3 and Fabric Loader at 0.20.1 or newer.
- Build with Fabric API 0.165.2+26.3 and check both API versions in CI.
- Separate the runtime API minimum from the pinned build dependency so future
  dependency updates do not automatically raise the installation requirement.

## 1.0.3

- Add the supplied mod icon.
- Show the dying player's username, countdown, and reset as on-screen titles.

## 1.0.2

- Keep the dying player's spectator respawn in the same dimension at their death
  position and facing direction, instead of vanilla's default world/bed spawn.
- Keep the complete vanilla respawn lifecycle from 1.0.1. Other players remain at
  their existing locations. Transfer to the next run still happens after the countdown.
- Focused build checked with the existing controller tests; in-game location
  verification is left to the user for this small patch.

## 1.0.1

- Use the complete vanilla respawn handler, fixing the stale connection player,
  duplicate UUID symptoms, stale movement state, and uncleared respawn-wait flag.
- Stage logins in the permanent infrastructure world so configuration cannot wait
  on a retired dimension. Repair dead and stale spectator saves before joining.
- Preserve valid current-run logout positions and reset stale-run players on entry.
- Replace the artificial ocean platform with Minecraft's natural spawn search.
- Isolate each run's generator and check its real generation seed against its manifest.
- Add console `hardcorereset status` diagnostics and a saved-player regression helper.

Validated with three live resets, repeated deaths of one player, corrupted-save
recovery, logout/reconnect during a countdown, saved-health checks, four distinct
seed/terrain fingerprints, and the existing 14 controller/state tests.

## 1.0.0

First release for Minecraft 26.3 and Fabric API 0.165.0+26.3.

- A player death places everyone in spectator mode and starts a ten-second countdown.
- Fresh Overworld, Nether, and End run dimensions replace the old run without stopping the server or intentionally disconnecting players.
- Player inventory, ender chest, XP, effects, recipes, advancements, statistics, and old respawn points reset for a new run.
- Nether and End portals route to the current run; the End dragon fight uses Minecraft's dimension-type support.
- Ordinary server restarts reopen the active run and seed.
- A console-only recovery command, `hardcorereset retry`, resumes a failed reset.
- Fantasy 0.8.4+26.3 is bundled inside the mod JAR.

Validation: successful Java 25 build, 14 controller/state tests, and two live death resets using two connected Minecraft 26.3 protocol clients. See `VALIDATION.md` for the test scope and limits.

World generation and cleanup can cause tick pauses. Completed resets delete old gameplay dimensions; back up saves before trying the mod.
