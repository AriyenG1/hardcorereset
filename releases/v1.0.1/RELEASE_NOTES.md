Bug-fix release for Minecraft 26.3 / Fabric API 0.165.0+26.3.

### Fixed

- Automatic respawn now uses vanilla's full handler: the connection follows the replacement player, movement tracking resets, and the respawn/loading flags are cleared. This fixes the stale dead-player save and repeated-respawn symptoms found in 1.0.0.
- Login configuration is staged in the permanent infrastructure world. A run can retire during login without leaving the connection waiting on that dimension.
- Existing dead/spectator saves and unknown run dimensions are recovered before joining. Valid current-run logout positions are preserved.
- Artificial ocean platforms are removed. Fresh runs use Minecraft's climate-biased origin and natural-ground spawn search.
- Each run gets an independent generator, and the actual world-generation seed is checked against the run's saved seed.
- New console diagnostic: `hardcorereset status`.

### Update

Back up your server. Stop it once, replace **hardcorereset-1.0.0.jar** with **hardcorereset-1.0.1.jar**, and start normally. Keep existing world/player files; do **not** delete player data as a workaround. The mod automatically repairs the broken login state. Recovery from an old/dead run starts that player fresh in the active run.

Requires **Java 25**, **Fabric Loader 0.20.1+**, **Minecraft 26.3**, and **Fabric API 0.165.0+26.3**. Fantasy is bundled. The server bundle ZIP includes the exact Fabric API JAR and instructions. Keep Nether and End enabled and `pause-when-empty-seconds=0`.

Completed death resets keep the server and player connections running. Old gameplay dimensions are deleted after transfer. World generation/cleanup can still cause tick pauses, especially with large worlds.

### Verified

Java build and 14 controller tests passed. Live regressions covered three resets, repeated deaths of one player, actual/reconstructed corrupted saves, alive/current-dimension saved NBT, and a reconnect during a countdown. Four inspected runs had distinct matching manifest/generation seeds and different terrain fingerprints. No duplicate-UUID warnings occurred in the final regression run. Testing used headless Minecraft 26.3 protocol clients; graphical rendering and arbitrary mod packs were not tested.
