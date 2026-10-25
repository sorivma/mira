# Verification record

## Release preparation — 2026-10-25

The current workspace, UI, and both integration addons passed the local release
build with Temurin 25.0.4.1+1 (Gradle/core), Java 8u504 (Forge), and Java 17.0.17
(decompiler):

```powershell
.\gradlew.bat -PmiraIntegrations=true check build checkIntegrations assembleIntegrations --no-daemon --console=plain --max-workers=2
.\scripts\verify-core-boundary.ps1
python scripts/package-release.py --tag v0.1.0
```

- **118 tests passed:** core 23, platform 81, MCP addon 10, Harness addon 4;
  zero failures, errors, or skips.
- Final mod/addon metadata and jar contents passed verification, including the
  separately compiled core embedding fixture and exclusion of forbidden classes.
- All 14 negative core-boundary controls failed for the intended isolation reason;
  allowed-test-dependency and final clean core checks passed.
- The release packager validated seven jars and produced a distribution ZIP,
  notices, installation instructions, and SHA-256 manifest.
- A separate build with `'-Pversion=0.1.1-verify'` passed checks and produced
  matching `@Mod` annotation bytecode and `mcmod.info` in all six mod/dev jars.
  The final 0.1.0 build and packaging then passed again. This exercises version
  propagation, not merely the currently matching default value.
- The Markdown image regression tests cover consent before network scheduling
  and rejection of insecure/file URL schemes.
- GitHub Build runs separate core controls plus standalone and integration builds.
  Hosted results are available from [Actions](https://github.com/sorivma/mira/actions);
  only a green run for the published commit is evidence for that commit.

No game, live MCP backend, GUI, or full GTNH smoke was launched during this
release preparation. The earlier foundation runtime evidence below applies only
to that earlier minimal bootstrap. Current multiplayer behavior, visual interaction,
and full-pack client/server compatibility still require the manual checklists in
[backend.md](backend.md), [ui.md](ui.md), and [integrations.md](integrations.md).

## Historical foundation evidence — 2026-10-25

The initial runtime baseline is **plain Minecraft 1.7.10 + Forge 1614 + Mira
only**, per the user's implementation direction. No Prism or desktop automation
was used for these game tests. Full GTNH client/server tests are deferred and
their OpenSpec checkboxes remain incomplete.

## Build and artifact

- Gradle 9.5.0 / RFG 2.0.6. Daemon/core compiler: Temurin 25.0.4.1+1.
- Core: `--release 8`; platform compiler/game runtime: Temurin 8u504-b01.
- Empty-cache RFG decompilation: Temurin 17.0.17+10. The first cold full build
  exposed that extra requirement; it is now documented and configured in CI.
- `:core:check` passed from clean core output and a newly created Gradle user
  home, `.work/gradle-core-isolated`. Log: `.work/isolated-core.log`.
  Only root/core were configured; tasks were core Java/verification tasks.
  Before the full build, cache roots were `9.5.0`, `jars-9`, `journal-1`, and
  `modules-2`; no Minecraft/RFG cache or game setup/download was created.
  The wrapper distribution and Gradle tooling were downloaded as expected.
- Root `check build` passed in that same isolated home after providing Java 17;
  `.work/isolated-full-build-retry.log` records the full workspace preparation.
- Final `check build :platform-forge-1.7.10:prepareObfModsFolder` passed after
  selecting core's jar variant and excluding embedded group `mira` from RFG's
  external runtime remapping. Log: `.work/final-build.log`.
- Windows `clean` initially encountered a jar handle held by a Gradle daemon.
  `gradlew.bat --stop`, then `:clean --no-daemon`, succeeded. No IDE metadata
  was edited by this implementation. Existing/concurrent IDE changes remain.
- Final artifact: `platform-forge-1.7.10/build/libs/mira-0.1.0.jar`, **1717 bytes**.
- SHA-256: `f0290d7ef6f054c5529b09ecfbc8759325a5def85c735ff26cb82dd02559c22b`.
- Inspected non-directory entries: `META-INF/MANIFEST.MF`,
  `mira/platform/forge1710/MiraMod.class`, and `mcmod.info`. Bootstrap classfile
  major is **52**; metadata has mod id `mira`, version `0.1.0`, Minecraft `1.7.10`.
  No game/runtime/test jars or fixture classes are included.
- Production core has no emitted runtime classes. The isolated
  `mira-packaging-fixture-0.1.0.jar` did contain the independently compiled core
  probe after reobfuscation; the verification task loaded/instantiated it with
  an isolated class loader. This proves the embedding mechanism, not the
  existence of production core behavior.

## Architecture controls

`scripts/verify-core-boundary.ps1` passed. Each forbidden control failed with
an explicit `Core isolation:` diagnostic, rather than an unrelated build error:

- Fully qualified platform, Minecraft, FML, GTNH, ModularUI, LWJGL, and Netty
  production references; Unicode-escaped Minecraft reference.
- A constant-folded forbidden name found only in compiled bytecode.
- Module/project/file dependencies, inherited file dependencies, and direct
  source-set classpath file additions. The JDK-only policy rejects every
  production dependency root, so transitive game libraries cannot enter either.
- A test-only JUnit declaration was allowed; clean core verification passed
  both with and without it. No test dependency or negative-control source was
  retained. Logs: `.work/boundary-controls/`.

## Plain Forge runtime smoke

Commands used the pinned wrapper with `:platform-forge-1.7.10:runObfServer`
and `:platform-forge-1.7.10:runObfClient`. The latter received repeated
`--mcArgs` values `--server`, `127.0.0.1`, `--port`, `25576` to enter the
disposable server world without controlling the desktop. Java was **8u504-b01**.
The conventional `run/obfuscated/mods/` contained exactly the final Mira jar.
FML's four reported entries were MCP, FML, Forge, and Mira; no other mod.

Server evidence (local time, 2026-10-25):

```text
01:05:06 [mira]: Mira initialized
01:05:06 [FML]: Forge Mod Loader has successfully loaded 4 mods
01:05:07 Done (0,486s)! For help, type "help" or "?"
01:05:49 Client attempting to join with 4 mods: FML, mira, Forge, mcp
01:05:49 Server side modded connection established
01:05:49 Developer joined the game
01:06:10 Stopping server; Saving players; Saving worlds
```

The server was bound to loopback port 25576, with an offline development player,
and generated `mira-smoke`, a disposable flat world. `stop` saved and shut it
down; Gradle reported `BUILD SUCCESSFUL`. Common bootstrap loaded without a
client-class failure.

Client evidence:

```text
01:05:47 [mira]: Mira initialized
01:05:47 [FML]: Forge Mod Loader has successfully loaded 4 mods
01:05:49 [FML]: Client side modded connection established
```

After the server saved, the client logged `Stopping!` at 01:06:19 and exited
with code 0; Gradle reported `BUILD SUCCESSFUL` for the client run as well.
Client rendering/singleplayer creation was not inspected through desktop
automation. The verified world entry was to the local dedicated server.
Logs are preserved under `.work/smoke-evidence/` and
`.work/plain-client-console.log`; the generated instance/world stays ignored.

## Limits and remaining acceptance

- Logs contain FML missing-signature diagnostics, legacy Forge version-check
  JSON errors, first-run server list-file warnings, and a client OpenAL context
  initialization error. Loading and client/server connection succeeded despite
  these. No missing class or exception naming Mira was observed. Audio is not
  validated; no claim of an entirely error-free vanilla log is made.
- At the time of the foundation checks, no hosted CI run had been triggered.
  Current release CI evidence is linked above.
- Minimal Forge on Java 8 does **not** establish GTNH compatibility. GTNH
  2.9.0-RC-2 client/server smokes on Java 21 remain unrun/deferred. The exact
  archives and hashes are recorded in [the baseline](build-baseline.md).
- RFG uses deprecated Gradle APIs; the pinned build passes on Gradle 9.5.0,
  and Gradle warns about future Gradle 10 incompatibility. Do not upgrade the
  wrapper without checking the plugin.
