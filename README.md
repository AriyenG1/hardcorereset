# Hardcore Reset

Server-side Fabric mod for **Minecraft 26.3**, **Fabric API 0.165.0+26.3**,
**Fabric Loader 0.20.1+**, and **Java 25**.

Any player death makes everyone a spectator, starts a **10-second countdown**,
and moves everyone into a fresh randomly seeded run. The server keeps running
and players keep their connections. No restart launcher or client mod is needed.

## Install

Download the JAR or the ZIP with Fabric API from the
[GitHub Releases page](https://github.com/AriyenG1/hardcorereset/releases).

1. Back up your server before trying the mod. Old gameplay worlds are deleted
   after a successful reset.
2. Put `hardcorereset-1.0.0.jar` in the Fabric server's `mods/` directory alongside
   **Fabric API 0.165.0+26.3**. Fantasy, the runtime-world library, is already
   bundled inside the mod JAR; you do not need a separate Fantasy download.
3. Use **Java 25** and keep the vanilla Nether and End enabled. Set
   `pause-when-empty-seconds=0` in `server.properties` so a countdown can finish
   even if all players leave. The new run's game mode is survival.
4. Start the server normally. A first run is created automatically. Players join
   it automatically. No Python script or special environment variables are needed.

Only the server needs the mod. The optional release ZIP contains the mod JAR,
the exact Fabric API JAR, and an installation note; copy its two JARs to `mods/`.
Do not also keep duplicate copies of Fabric API or this mod in that folder.

## What happens during a reset

- Successful totem saves do not end the run; an actual player death does.
- All online players become spectators. The dying player is respawned
  automatically into spectator without clicking Respawn.
- Repeated deaths do not restart the countdown. New arrivals during a reset
  also become spectators.
- Next-run generation begins during the countdown. Players remain spectators
  if generation needs longer than ten seconds. Generation and world cleanup can
  pause server ticks, so countdown messages may skip numbers under load.
- Players move using normal dimension-transfer packets over their existing
  connections, then return to survival.
- Old dimensions are closed and deleted only after every player has left.
  Preparation/transfer errors retain the worlds and pause the reset for inspection.

The mod uses persistent runtime Overworld, Nether, and End dimensions, with
Nether/End portals routed into the current run. Minecraft 26.3 initializes and
saves the dragon fight from the End dimension type. The run's End portal returns
players to a valid current-run bed/anchor or the current-run spawn.

## Saves and player state

The server root save stays open as infrastructure. Each run gets new folders:

```text
world/
  hardcorereset/run.properties
  dimensions/hardcorereset/run_<unique-id>/
    overworld/
    nether/
    end/
```

The manifest stores the active ID and random seed. Ordinary operator-initiated
server restarts resume that run. A new run does not reuse `server.properties`'s
fixed seed. If an ocean/unsafe spawn has no suitable land nearby, the mod creates
a small starter platform. Corrupt manifest data is reported rather than silently
replacing the run. Failed/crash-interrupted preparation may retain unused run
folders; empty old parent directories can also remain. Inspect them only with
the server stopped.

A saved Fabric attachment identifies each player's run. Players returning from
an old run are reset on join, preventing logout from preserving old inventories.
The reset clears inventory, ender chest, XP, effects, health, hunger/exhaustion,
absorption, fire, freezing, air, fall distance, recipe unlocks, advancements,
statistics, and old beds/anchors.

Server-global scoreboards, operators, whitelist, bans, configuration, and other
mods' separately stored data remain. Mods with custom portal handling or their
own player progression may need additional integration.

## Recovery

On a preparation or transfer failure, players remain connected as spectators.
Inspect the server log, fix the cause, then run **`hardcorereset retry` from the
server console**. Failed handoffs reuse the same prepared run so players are not
split between successive attempts. The recovery command is console-only.

## Build and validation

Select JDK 25 using `JAVA_HOME`, then:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. The installable artifact is
`build/libs/hardcorereset-1.0.0.jar`, not the sources JAR.

The build compiles the Minecraft integration, runs 14 standalone controller/state
tests, and compiles the optional protocol smoke client. The standalone tests can
also run directly with `./tools/test-controller.sh`.

The release was smoke-tested on an actual dedicated Fabric server with two
headless clients using Minecraft's own **26.3 packet codecs**. Both clients kept
one connection through two death resets, including a Nether death. Inventory,
ender chest, XP, survival mode, new seeds, old-world deletion, End travel and
return, dragon spawning, and Nether portal routing were checked. These are
protocol clients, not graphical-client rendering tests. See [VALIDATION.md](VALIDATION.md)
for evidence and remaining test limits.

An optional helper is available for a disposable **localhost, offline-mode** test
server whose EULA you have accepted. It does not reconnect automatically:

```sh
./gradlew protocolSmokeClient -PsmokeName=ResetTester -PsmokePort=25575 -PsmokeSeconds=120
```

GitHub Actions builds the mod and uploads the artifact after a push; it was not
run or published from this workspace.

## Bundled library

Fantasy **0.8.4+26.3**, Nucleoid Contributors, is bundled unmodified under
LGPL-3.0. Its source at the exact release is available at
<https://github.com/NucleoidMC/fantasy/tree/7107b189e78848b062b7b53799161f6e494ae75d>.
The license is in `LICENSES/Fantasy-LGPL-3.0.txt` and the mod JAR's
`META-INF/licenses` directory. This mod supplies a separate resource-closure
mixin; the embedded Fantasy archive is unchanged. Fabric can load a separately
installed compatible Fantasy JAR instead of the nested copy.
