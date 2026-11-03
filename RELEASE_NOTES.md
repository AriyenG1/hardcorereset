One-shot map seeds and a quieter reset completion for Minecraft 26.3.

Operators can run `/nextseed <seed>` during an active run. The next reset uses
that seed for all three dimensions. Later resets use random seeds unless the
command is run again. Numeric seeds (including zero and negatives) and text seeds
follow Minecraft's world-creation rules. The last queued value wins.

The command requires operator permission level 1 or higher and also works from
the server console. Set the seed before the countdown starts; generation is
already in progress during a reset. Generation/transfer retries retain the
selected seed. Queued seeds are held in memory and clear on server restart.

The "New world ready!" title is removed. Existing reset titles clear when players
arrive in the new map; countdown and failure titles remain.

Update: stop the server, replace the old mod JAR with hardcorereset-x.x.x.jar,
and restart. Keep existing world/player files. Requires exactly Minecraft 26.3,
Java 25+, Fabric Loader 0.20.1+, and Fabric API 0.165.0+26.3 or newer for Minecraft
26.3. Fantasy remains bundled; the server bundle includes Fabric API 0.165.2+26.3.

Validation: Java 25 builds with both supported Fabric API versions, 21 controller/state
tests, and four command integration checks using Minecraft's dispatcher and permission
APIs. No new live-server or graphical-client session was run.
