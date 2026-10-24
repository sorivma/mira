# Mira

Minecraft 1.7.10 / Forge foundation for collaborative tasks, workflows, and
boards, targeting GT New Horizons **2.9.0-RC-2**. Currently the mod only logs
initialization. Full-pack compatibility status is in [verification](docs/verification.md).

## Setup on Windows

Install Temurin JDK **25.0.4.1+1** for Gradle/core compilation and JDK
**8u504-b01** for Forge compilation and minimal development runs, plus JDK
**17.0.17+10** for RFG's decompiler. Full GTNH
smokes use Java **21.0.10+7**. Core is compiled with `--release 8`; platform
uses a Java 8 compiler. See [exact baseline and downloads](docs/build-baseline.md).

In PowerShell at the repository root, substitute your installation paths:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-25.0.4.1+1'
$env:MIRA_JAVA8_HOME = 'C:\Java\jdk8u504-b01'
$env:MIRA_JAVA17_HOME = 'C:\Java\jdk-17.0.17+10'
.\gradlew.bat --version
.\gradlew.bat :core:check
.\gradlew.bat check
.\gradlew.bat build
```

Only JDK 25 is needed for `:core:check`. The wrapper pins Gradle 9.5.0 and its
download checksum; do not install a global Gradle. Configuration on demand is
intentional: `:core:check` does not configure the game plugin, download game
artifacts, decompile Minecraft, fetch assets, or start a game. The full build
does prepare Forge's development workspace on its first run. No pack-specific
build conventions are required.

Install **only** `platform-forge-1.7.10\build\libs\mira-0.1.0.jar` in `mods\`.
The final jar embeds the core before reobfuscation. Do not install `-dev.jar`,
the standalone core jar, or anything in `build\fixtures\`.

## Conventional development instance

```powershell
.\gradlew.bat :platform-forge-1.7.10:setupDecompWorkspace
.\gradlew.bat :platform-forge-1.7.10:runClient
.\gradlew.bat :platform-forge-1.7.10:runServer
# Test reobfuscated output in a minimal Forge environment:
.\gradlew.bat :platform-forge-1.7.10:runObfClient
.\gradlew.bat :platform-forge-1.7.10:runObfServer
```

Setup is optional before a build: RFG wires its prerequisites automatically.
The development instance is `platform-forge-1.7.10\run\`, with the usual
`mods\`, `config\`, `logs\`, and `saves\` directories created as needed by
Minecraft. Mira itself loads from the development classpath. Client and server
use the standard shared instance root; obfuscated runs use
`run\obfuscated\`. Run one game process per instance at a time. All are ignored by
Git and preserved by `clean`. Review/accept the server's EULA prompt yourself
on first use. Development tasks run Java 8 and minimal Forge; the complete
modern-Java GTNH distribution is tested separately.

## Verification

`check` verifies core isolation, final mod contents, and a separately compiled
class-embedding fixture after reobfuscation. Production core currently has no
runtime classes. The fixture proves embedding works without inventing domain
behavior. [Architecture and ownership](docs/architecture.md) explain the seam.

```powershell
.\scripts\verify-core-boundary.ps1
```

The script temporarily exercises forbidden references, compiled constants,
project/module/file dependencies, inherited dependencies, and direct classpath
files. It removes controls in `finally` and reruns clean core checks. Logs go
to `.work\boundary-controls\`. CI runs independent core verification and full
assembly in separate jobs using the pinned JDK selections.

To repeat the independent check with an empty Gradle user home and clean core
outputs (this also downloads the checksum-verified wrapper distribution):

```powershell
$isolatedHome = Join-Path $PWD ('.work\gradle-core-' + [guid]::NewGuid())
.\gradlew.bat --gradle-user-home $isolatedHome :core:clean :core:check --info
```

## Initial smoke: plain Forge with only Mira

Use `runClient` / `runServer` above for ordinary development. Use
`runObfClient` / `runObfServer` to test the actual installable jar with Forge
1614 and Minecraft 1.7.10. No launcher, Prism instance, GTNH installation, or
other mod is required. RFG copies Mira to `run\obfuscated\mods\` automatically.
Wait for `Mira initialized` and FML's successful mod loading message. On a
server, wait for `Done (...)!` and enter `stop` to save and shut down. On a
client, create a disposable singleplayer world and quit normally. Use the
generated `run\...\logs\` for evidence. Server EULA prompts remain interactive.

Full-pack smokes are deferred; the first test baseline is plain Forge + Mira.

## Later GTNH smoke procedure

1. Download the exact client/server archives linked in the baseline. Verify
   `Get-FileHash -Algorithm SHA256 <archive>` against the recorded hashes.
2. Import the client ZIP into a **new disposable** Prism instance. Select Java
   21.0.10+7, give it the pack's recommended memory, and copy only the final
   Mira jar into that instance's `.minecraft\mods\`. Keep existing instances,
   accounts, and worlds intact.
3. Launch, confirm Mira appears in the mod list and `Mira initialized` appears
   in `logs\fml-client-latest.log`. Create/load a disposable world, wait for
   world entry, then quit normally. Check for missing classes and new
   Mira-related errors. Record the jar hash, runtime, pack, and log evidence.
4. Extract the matching server ZIP into a separate disposable directory. Copy
   the **same** mod jar into `mods\`. Select the same Java 21 executable in
   the provided start script, review the EULA, then start the server and wait
   for `Done (...)!`. Stop with `stop`. Confirm the initialization marker and
   absence of Mira-related exceptions or client-class loading failures.
5. Record evidence in `docs/verification.md`. Keep unavailable smokes incomplete.
   A successful build or minimal Forge run does not prove full GTNH compatibility.
