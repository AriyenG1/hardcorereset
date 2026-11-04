Persistent death counter and operator scoreboard shortcuts for Minecraft 26.3.

Commands (operator level 2+ or console):
- `/scoreboard show` / `/scoreboard hide`: control the sidebar; hidden scores still count.
- `/scoreboard reset`: reset all tracked death counts, including offline players, to zero.
- `/scoreboard reset <player>`: reset one literal username.
- `/scoreboard set <player> <count>`: set a nonnegative count.
- `/scoreboard get <player>` / `/scoreboard list` / `/scoreboard help`.

The counter uses vanilla deathCount, persists across run resets and server restarts,
and begins tracking upon installation. New joins get a zero entry. Other objectives
and vanilla scoreboard commands are preserved. The sidebar appears automatically
on first setup if it is free. Show replaces the sidebar selection; hide only clears
it when the Hardcore Reset counter is displayed. No extra death-counter mod is needed.

Update: back up, stop the server once, replace the old JAR with hardcorereset-1.0.6.jar,
and restart. Keep world/player files. Requires Minecraft 26.3, Java 25+, Fabric Loader
0.20.1+, and Fabric API 0.165.0+26.3 or newer for Minecraft 26.3. Fantasy is bundled.

Validation: build, 21 controller/state tests, four next-seed command checks, and a
Minecraft scoreboard/dispatcher integration test covering death increments, permissions,
offline and single-player resets, hidden counting, other-objective isolation, vanilla
command merging, and invalid negative input. No additional live-server session was run.
