# Hardcore Reset

<img src="src/main/resources/assets/hardcorereset/icon.png" width="128" alt="Hardcore Reset icon">

Server-side Fabric mod for **Minecraft 26.3**, **Fabric API 0.165.0+26.3 or newer for Minecraft 26.3**,
**Fabric Loader 0.20.1+**, and **Java 25**.

Any player death makes everyone a spectator, starts a **10-second countdown**,
and moves everyone into a fresh randomly seeded run. The server keeps running
and players keep their connections. No restart launcher or client mod is needed.

## Install

Download the JAR or the ZIP with Fabric API from the
[GitHub Releases page](https://github.com/AriyenG1/hardcorereset/releases).

1. Back up your server before trying the mod. Old gameplay worlds are deleted
   after a successful reset.
2. Put `hardcorereset-1.0.7.jar` in the Fabric server's `mods/` directory alongside
   **Fabric API 0.165.0+26.3 or newer for Minecraft 26.3**. Fantasy, the runtime-world library, is already
   bundled inside the mod JAR; you do not need a separate Fantasy download.
3. Use **Java 25** and keep the vanilla Nether and End enabled. Set
   `pause-when-empty-seconds=0` in `server.properties` so a countdown can finish
   even if all players leave. The new run's game mode is survival.
4. When updating from an earlier version, stop the server once and replace the old mod JAR. Keep
   the existing world and player files. Version 1.0.1 repairs dead/spectator saves
   and retired-dimension logins automatically; do not delete player data to work around it.
5. Start the server normally. A first run is created automatically. Players join
   it automatically. No Python script or special environment variables are needed.

Only the server needs the mod. The optional release ZIP contains the mod JAR,
Fabric API 0.165.2+26.3, and an installation note; copy its two JARs to `mods/`.
Do not also keep duplicate copies of Fabric API or this mod in that folder.

## Fabric compatibility

Minecraft must remain **26.3**. Fabric Loader **0.20.1 or newer** and Fabric API
**0.165.0+26.3 or newer built for Minecraft 26.3** satisfy the mod's requirements.
You can update these Fabric components without replacing Hardcore Reset just
because their version numbers changed. Do not install Fabric API for a different
Minecraft version or keep multiple Fabric API JARs in `mods/`.

The build uses a pinned Fabric API version for reproducible downloads. Its
separate runtime minimum allows newer releases. Version 1.0.7 is checked against
both 0.165.0+26.3 and 0.165.2+26.3; a future breaking API change may still need a
mod update.

## Death counter

The built-in counter tracks actual player deaths across runs and ordinary server
restarts. New players start at zero. It appears in the sidebar automatically if
that sidebar is free. Hiding it keeps counting; show/hide is saved by Minecraft.
These shortcuts require operator level 2 or higher, or the server console:

| Command | Action |
| --- | --- |
| `/scoreboard show` | Show the death counter in the sidebar |
| `/scoreboard hide` | Hide the death counter |
| `/scoreboard reset` | Set all tracked death counts to zero, including offline players |
| `/scoreboard reset <player>` | Reset one username's death count |
| `/scoreboard set <player> <count>` | Correct a username's count; nonnegative integer |
| `/scoreboard get <player>` | Read one player's count |
| `/scoreboard list` | List tracked death counts |
| `/scoreboard help` | Show shortcut help |

Player arguments are literal usernames, including offline usernames. These commands
manage only `hardcorereset_deaths`; other objectives remain intact. Vanilla
`/scoreboard objectives` and `/scoreboard players` commands still work. Showing the
counter selects it as the sidebar objective; hiding it leaves a different currently
displayed objective alone. The counter starts tracking when this version is installed;
it does not import earlier deaths.

## Choose the next map's seed

During an active run, an operator can use:

```text
/nextseed 123456789
/nextseed -987654321
/nextseed a forest seed
```

The next reset uses that seed for its Overworld, Nether, and End. The current map
stays unchanged. The following reset uses a random seed again unless an operator
runs `/nextseed` again. Running it more than once before a reset replaces the
queued seed; the last value wins. Zero and negative seeds are supported. Text
seeds use Minecraft's normal world-creation hashing.

Operators at permission level 1 or higher can use the command; ordinary players
cannot. The server console can also use `nextseed <seed>` without the slash.
Set it **before the reset countdown starts**, because generation begins during
that countdown. Changes while a reset is in progress are rejected. Generation
and transfer retries keep the seed selected for that map. The queued seed is kept
in memory; restarting the server before the reset clears it.

## What happens during a reset

- Successful totem saves do not end the run; an actual player death does.
- All online players become spectators at their current locations. The dying
  player is respawned automatically at their death position in the same dimension,
  preserving their facing direction, without clicking Respawn.
- Repeated deaths do not restart the countdown. New arrivals during a reset
  also become spectators.
- Next-run generation begins during the countdown. Players remain spectators
  if generation needs longer than ten seconds. Generation and world cleanup can
  pause server ticks, so countdown messages may skip numbers under load.
- Players move using normal dimension-transfer packets over their existing
  connections, then return to survival. Reset titles clear when the new map is
  active; no "New world ready!" message appears.
- Old dimensions are closed and deleted only after every player has left.
  Preparation/transfer errors retain the worlds and pause the reset for inspection.

The mod uses persistent runtime Overworld, Nether, and End dimensions, with
Nether/End portals routed into the current run. Minecraft 26.3 initializes and
saves the dragon fight from the End dimension type. The run's End portal returns
players to a valid current-run bed/anchor or the current-run spawn.

Version 1.0.7 fixes lighting Nether portals in the run's Overworld and Nether.
Use a normal obsidian frame and flint and steel or a fire charge. Travel keeps
Minecraft's normal coordinate scaling and returns to the same run. Existing
runs work after updating; relight any existing valid frame that failed to activate.

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
fixed seed. Spawn selection uses Minecraft's climate-biased origin and natural-ground search.
The mod does not construct an ocean platform. Each run gets an independent generator
and its actual noise-generation seed is checked against the saved run seed. Corrupt manifest data is reported rather than silently
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

Version 1.0.1 loads joining players through the permanent infrastructure world
during login configuration, then restores a valid current-run position or starts
them at the current spawn. This avoids referring to a world that retires while
they are connecting. Dead or stale spectator saves are recovered into a live player.


On a preparation or transfer failure, players remain connected as spectators.
Inspect the server log, fix the cause, then run **`hardcorereset retry` from the
server console**. Failed handoffs reuse the same prepared run so players are not
split between successive attempts. The recovery command is console-only.

Console-only `hardcorereset status` reports the active run, saved and generation
seeds, a terrain sample fingerprint, and player connection/loading invariants.

## Build and validation

Select JDK 25 using `JAVA_HOME`, then:

```sh
./gradlew build
```

Windows: `gradlew.bat build`. The installable artifact is
`build/libs/hardcorereset-1.0.7.jar`, not the sources JAR.

CI builds against both the minimum and current Fabric API versions.
The build compiles the Minecraft integration, runs 21 standalone controller/state
tests and four Minecraft command checks, and compiles the optional protocol smoke client. The standalone tests can
also run directly with `./tools/test-controller.sh`.

`./gradlew runPortalTest` runs the portal integration suite with Fabric mixins
on a disposable localhost server in `build/portal-test-server/`. It checks real
flint-and-steel and fire-charge ignition, both frame orientations and bottom/side
clicks, invalid frames, ordinary fire, dimension restrictions, and scaled entity
travel to the paired Nether and back. The test mod is excluded from release JARs.

Version 1.0.1 additionally passed three live resets, repeated deaths of the same
player, recovery of an actual legacy dead-player save and a reconstructed corrupt
save, persisted-health checks, and reconnecting during a countdown. Four inspected
runs had distinct generation seeds and terrain fingerprints.

The original release was smoke-tested on an actual dedicated Fabric server with two
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

GitHub Actions checks both supported Fabric API versions and uploads build
artifacts after a push. Version tags publish the tested files from `releases/`.

## Bundled library

Fantasy **0.8.4+26.3**, Nucleoid Contributors, is bundled unmodified under
LGPL-3.0. Its source at the exact release is available at
<https://github.com/NucleoidMC/fantasy/tree/7107b189e78848b062b7b53799161f6e494ae75d>.
The license is in `LICENSES/Fantasy-LGPL-3.0.txt` and the mod JAR's
`META-INF/licenses` directory. This mod supplies a separate resource-closure
mixin; the embedded Fantasy archive is unchanged. Fabric can load a separately
installed compatible Fantasy JAR instead of the nested copy.
