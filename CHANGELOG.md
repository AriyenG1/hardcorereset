# Changelog

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
