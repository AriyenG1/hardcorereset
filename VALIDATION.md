# Release validation

## 1.0.5 One-shot next-map seeds

- Java 25 Gradle builds passed with Fabric API 0.165.0+26.3 and 0.165.2+26.3.
- All 21 controller/state tests passed. Added checks cover current-map preservation,
  one-shot consumption followed by random selection, replacement and seed zero,
  rejecting changes during a reset, generation retries retaining the seed,
  transfer retries reusing the prepared map, exact signed seeds with unique map
  IDs, and silent completion on initial activation and subsequent resets.
- Four command integration checks use Minecraft 26.3's actual Brigadier dispatcher,
  permission sets, and `WorldOptions.parseSeed`. Non-operators are denied; all four
  operator levels are accepted. Numeric zero, negative and signed-long limits,
  text seed hashing, missing/blank arguments, and busy-reset rejection passed.
- The queued seed is intentionally held only in memory. It clears when the next
  reset begins or when the server restarts; the seed selected for an in-progress
  reset is retained across retries within that server session.
- Production code removes the completion announcement and sends vanilla's clear-title
  packet after successful activation. No new live server or graphical-client session
  was run; title rendering and actual world terrain remain outside these new checks.

## 1.0.4 Fabric compatibility

- Fabric metadata endpoints reported Loader 0.20.1 as the latest stable Loader
  for Minecraft 26.3 and Fabric API 0.165.2+26.3 as the newest API for that game
  version when checked on 2026-11-03.
- Java 25 Gradle builds succeeded with Fabric API 0.165.0+26.3 and 0.165.2+26.3.
  All 14 controller/state tests passed, and the protocol helper compiled.
- Checked the packaged version requirements with Fabric Loader 0.20.1's own
  `VersionPredicate`: both released API versions are accepted, 0.164.0+26.3 is
  rejected, and Minecraft 26.2, 26.3.1, and 26.4 are rejected. Synthetic newer API
  and Loader version numbers confirm the open minimum semantics only.
- Packaged metadata has no unexpanded variables. JAR integrity passed and the
  nested Fantasy JAR is byte-identical to the one in release 1.0.3.
- This update changes dependency metadata and build configuration. No new live
  server or graphical-client session was run; older live checks below describe
  their original releases. Future breaking Fabric API changes remain possible.

## 1.0.1 regression fixes

The original live checks below missed the connection-owned player reference and
the respawn-wait flag. A later inspection of the old test server actually found
ResetAlice saved with health 0, spectator mode, and a retired dimension. That
legacy save reproduced the reported failure and was used for recovery testing.

Version 1.0.1 uses the full vanilla client-command respawn path and loads login
configuration through the permanent hub. The final build was exercised with:

- Recovery of the legacy dead save and a reconstructed health-0/spectator save
  referring to a nonexistent run; both joined alive in survival.
- Two consecutive deaths of ResetAlice, then a death of ResetBob: three completed
  resets. Status checks showed `bound=true`, `health=20`, `removed=false`,
  `loaded=true`, and `changing=false` after transfers.
- A deliberate logout followed by a gated reconnect during the third countdown.
  The player joined as spectator before the timer expired, then transferred with
  the remaining client into the new run.
- Both saved NBT files inspected after saving/logout: health 20, survival mode,
  current run dimension. No duplicate-UUID warnings in the final server log.
- Four distinct runs with each manifest seed equal to its RandomState seed, and
  different 25-point OCEAN_FLOOR_WG terrain fingerprints:

| Seed | Terrain fingerprint |
| --- | --- |
| 2108076949663673380 | 2969818447972890688 |
| 5441955137901048646 | -4323943276203657613 |
| -6128879437619173060 | 4504039883093815073 |
| -1938303269046862097 | 2317486583262144755 |

Natural spawns were `(224,79,-128)`, `(48,69,32)`, `(0,137,0)`, and `(16,63,2)`.
The new spawn path only searches terrain and never places platform blocks.
Distinct finite samples support the observed terrain changes; they are not a
proof that every location differs between every possible pair of seeds.

An early observer bot was kicked for floating at a legacy saved location because
it does not simulate client gravity. Reconnecting it after that run retired
recovered it at the new natural spawn. The final two-client regression run had
no such kick during its reset checks. Graphical rendering remains untested.

The final Java 25 build and all 14 existing controller tests passed. Protocol
helper code is compiled by the build. The old first-release observations below
are retained as history and do not establish saved-player correctness in 1.0.0.

## Original 1.0.0 validation

Target: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3,
Fantasy 0.8.4+26.3, Java 25. The server was disposable, bound to 127.0.0.1,
and used offline mode solely to permit the local test clients. EULA acceptance
was explicitly supplied by the user for this test.

## Build and component tests

- `gradle build`: compiles the production mod, its mixins, and optional protocol client.
- 14 controller/state tests passed: deadline, duplicate deaths, asynchronous waits,
  spectator enforcement, transfer-before-retirement, preparation/transfer/cleanup
  failures, retries, subsequent rounds, durable ID/seed storage, and corrupt state.
- The real server startup exposed a field-descriptor typo missed by compilation.
  The descriptors were corrected and the corrected packaged JAR was used below.
- The food-state integration was updated to the actual 26.3 API. Recipe unlocks
  and absorption are also cleared. The bundled Fantasy library loaded correctly.

## Live protocol/server observations

Two standalone clients, ResetAlice and ResetBob, used the vanilla Minecraft 26.3
Connection and protocol codecs. They acknowledged teleports, configuration,
chunk batches, and keepalives. They had no automatic reconnect behavior.

First death reset, server log times in UTC:

- 23:52:59: ResetAlice died and the ten-second reset began.
- Both clients received spectator game-mode packets; Alice received an automatic
  death respawn without sending a respawn-button command.
- 23:53:09: both clients transferred to the new run and received survival mode.
- The original run ID was `1c317769-9aaa-49e0-aa5f-8956d9876a0f` with seed
  `1873945389858434391`; the new ID was `da38baaf-fa0c-4d7f-be47-0df758cedf7e`
  with seed `-829174231889478969`.
- Bob had been given 32 inventory diamonds, 16 ender-chest diamonds, and 12 XP
  levels. Console NBT queries after reset returned empty Inventory, empty
  EnderItems, zero XpLevel, and survival game type for both players.
- The old run's three dimension directories were removed, leaving an empty parent.

Portal and dragon checks in that run:

- Bob entered its runtime End through a placed End portal, without disconnecting.
- The server logged the initial dragon-fight scan and confirmed an ender dragon
  existed in that run's End dimension.
- Bob returned through an End portal to the same run's Overworld.
- An Overworld Nether portal routed Bob into that run's Nether.

Second death reset:

- 23:54:56: Bob was killed in the runtime Nether.
- 23:55:06: both clients transferred into a third run, ID
  `cc70a3ad-1626-48a4-aac9-67947e5c5f89`.
- Before deliberately stopping the test server, assertions over both client logs
  confirmed exactly one authenticated login and one play login per client,
  two completed new-run messages, spectator/survival transitions, and no reset
  disconnect/failure packets. No process restart occurred between those resets.
- A third client joined the active run successfully after the second reset.

The server was then deliberately stopped by console to test ordinary persistence.
This operator stop is separate from the successful no-shutdown death resets.
The server was then started again and successfully reopened the same active
run ID `cc70a3ad-1626-48a4-aac9-67947e5c5f89` and seed
`-6418342621378781079`. The parsed manifest matched the pre-stop manifest.
The temporary server was stopped after this persistence check.

The final distributed mod JAR was compared by SHA-256 with the JAR installed
on the smoke-test server and matched exactly. Its archive integrity, mod metadata,
Java 25 class version, and nested Fantasy archive were checked.

## Limits

- Protocol clients were used; graphical rendering and a human-controlled client
  were not tested in this environment.
- Creation and cleanup caused noticeable tick stalls under the shared test load,
  including roughly 13 seconds after retiring the End that had just been explored.
  Both original connections survived the measured resets. This does not establish
  a universal latency/timeout bound for arbitrary large worlds or mod packs.
- A full dragon kill/respawn cycle, Nether return portal, totem save, hardcore-mode
  playthrough, offline-player return after a reset, and a real join *during* the
  countdown were not exercised. Countdown joins are covered by controller logic
  and the registered player-join event, not claimed as a completed live test here.
- Other mods' world generation, custom portals, and separate progression data
  were not tested.
