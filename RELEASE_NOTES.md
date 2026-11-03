Focused spectator-location fix for Minecraft 26.3.

The dying player now respawns as a spectator at their death position and facing
direction in the **same run and dimension**, instead of vanilla's default spawn
in the infrastructure world. Other players stay at their existing locations.
Only when the countdown finishes does everyone move into the new run.

The complete vanilla respawn handler and login recovery from 1.0.1 are retained.

**Update:** stop the server once, replace the previous mod JAR with
`hardcorereset-1.0.2.jar`, and restart. Keep existing world/player files.
Requires Java 25, Fabric Loader 0.20.1+, Minecraft 26.3, and Fabric API
0.165.0+26.3. Fantasy is bundled; the ZIP also includes Fabric API.

Validation: build and the existing 14 controller tests. This focused patch has
not undergone another extended live-server test; the in-game spectator-location
check is left to the user.
