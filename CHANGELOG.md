# Changelog

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
