First release of **Hardcore Reset** for **Minecraft 26.3**.

When any player dies, everyone becomes a spectator, a ten-second countdown starts, and everyone moves into a fresh randomly seeded run. The server stays running and players keep their connections. No external launcher or client mod is required.

### Downloads and installation

- **`hardcorereset-1.0.0.jar`** — put this in the Fabric server's `mods/` folder alongside Fabric API **0.165.0+26.3**.
- **`hardcorereset-1.0.0-server-bundle.zip`** — includes the mod, the exact Fabric API JAR, and instructions. Extract the two JARs into `mods/`; remove duplicate or older copies.
- **`SHA256SUMS`** — SHA-256 checksums for both downloads.
- **`INSTALL.txt`** — short installation instructions.

Requires **Java 25**, **Minecraft 26.3**, and **Fabric Loader 0.20.1 or newer**. Keep Nether and End enabled and set `pause-when-empty-seconds=0` in `server.properties`. Fantasy **0.8.4+26.3** is bundled inside the mod.

### Included

- Fresh Overworld, Nether, and End dimensions with portals routed to the current run.
- Automatic spectator respawn for the dying player and spectator enforcement during resets.
- New-run resets for inventory, ender chest, XP, effects, health/food, recipe unlocks, advancements, statistics, and old respawn points.
- Active run and seed persistence across ordinary operator-initiated server restarts.
- Console recovery command: `hardcorereset retry`.

### Validation and limits

The Java 25 build and all **14 controller/state tests passed**. Two connected Minecraft 26.3 protocol clients completed **two consecutive death resets without reconnecting**, including a Nether death. Inventory/ender-chest/XP resets, old-world deletion, End travel and return, dragon spawning, Nether routing, and ordinary restart persistence were checked. These were headless protocol clients, not graphical-client tests; see [VALIDATION.md](https://github.com/AriyenG1/hardcorereset/blob/v1.0.0/VALIDATION.md) for the full scope.

**Back up before trying the mod. Completed resets delete old gameplay worlds.** The server's root save remains as infrastructure while each run uses separate gameplay dimension folders. World generation and cleanup can pause ticks, especially for larger worlds. Server-global scoreboards and other mods' separately stored data are retained.
