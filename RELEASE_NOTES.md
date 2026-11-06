Fix Nether portal ignition in Hardcore Reset's custom run dimensions for Minecraft 26.3.

Nether portals now light normally in the active run's Overworld and Nether using
flint and steel or fire charges. The shared fire-placement check recognizes the
run's paired dimensions. Travel still uses vanilla portal placement and coordinate
scaling, and return portals lead to the same run's Overworld.

Existing runs, seeds, player data, death counts, and settings are preserved. No world
reset or migration is needed. Stop the server, replace the old Hardcore Reset JAR
with hardcorereset-1.0.7.jar, restart, and relight your existing valid portal frame.
Keep only one version of the mod in mods/.

Requires Minecraft 26.3, Java 25+, Fabric Loader 0.20.1+, and Fabric API
0.165.0+26.3 or newer built for Minecraft 26.3. Fantasy is bundled. The optional
server bundle includes Fabric API 0.165.2+26.3; avoid duplicate API JARs.

Validation: Java 25 build, all 21 controller/state tests, next-seed command and death
scoreboard checks, and real Fabric server portal integration tests with both
Fabric API 0.165.0+26.3 and 0.165.2+26.3. The portal suite checks both ignition items,
both frame orientations, bottom and side clicks, invalid frames, ordinary fire,
End/unrelated-dimension restrictions, and scaled entity round trips through the run
and vanilla portals. Removing the ignition fix reproduces the reported failure.
These are server checks; graphical-client rendering was not tested.
