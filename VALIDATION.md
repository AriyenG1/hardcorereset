# Release validation

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
