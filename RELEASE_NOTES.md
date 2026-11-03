Fabric compatibility update for Minecraft 26.3.

Hardcore Reset 1.0.4 accepts Fabric API 0.165.0+26.3 and newer releases for
Minecraft 26.3, including 0.165.2+26.3. The previous exact version requirement
rejected newer Fabric API installations. Fabric Loader remains 0.20.1 or newer;
Minecraft remains exactly 26.3 and Java 25 or newer is required.

The build dependency is pinned separately from the runtime minimum. Routine
Fabric updates no longer require a new mod JAR solely because the version
number changed. Future breaking API changes may still need an update.

Update: stop the server, replace the old mod JAR with hardcorereset-x.x.x.jar,
and restart. Keep existing world/player files. Install one Fabric API JAR for
Minecraft 26.3; the optional bundle includes 0.165.2+26.3. Fantasy remains bundled.

Validation: Java 25 builds with Fabric API 0.165.0+26.3 and 0.165.2+26.3,
14 existing controller/state tests, and packaged version constraints checked
using Fabric Loader's version matcher. No new live-server session was run.
