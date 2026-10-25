# Mira

Minecraft 1.7.10 / Forge foundation for collaborative tasks, workflows, and
boards, targeting GT New Horizons **2.9.0-RC-2**. Workspaces contain multiple
projects, with server-owned roles, workflows and a database stored in the world.
Saved changes synchronize immediately with connected players. Full-pack
compatibility status is in [verification](verification.md).

## Workspace UI

Enter a world and press **J** to open Mira. Rebind it under **Controls > Mira**.
The workspace has Board, Backlog, and Issues views, project switching, search,
assignee/sprint selectors, issue details, creation/editing, and comments.
Board cards support dragging between statuses and reordering. Narrow boards keep
their columns: use the bottom scrollbar or **Shift+wheel** to move sideways.
Click a card to open its details.
Assignees show a skin face beside their nickname. Use **C**/**Ctrl+N** for a new
issue, **Enter** to create it, **Ctrl+Enter** to create and keep adding, and the
column's **+** to start in that status. Optional fields expand on demand.
The description combines Markdown text with ordered checklist, materials,
location and blocker widgets in one block document. Click text or a widget to
edit; use a **+** insertion gap or **/** to insert anywhere. **Enter** splits a
paragraph, **Shift+Enter** inserts a line break. Drag a **::** gutter handle to
reorder blocks, or use **^/v** in the shared toolbar. **Save / Ctrl+Enter** commits
the whole description; **Cancel / Escape** discards it. Switching blocks retains
unfinished edits. The full issue form uses the same document editor. Materials track
your carried inventory, including supported backpacks, without consuming items.
With GTNH NEI installed, **Items** opens its picker on the right. Close it to
restore the full workspace width. Drag an item onto the body to add a requirement,
or onto a material to add an alternative.
NEI is optional and is not embedded. Without it, edit Materials and use **Held item**.
The window reuses Minecraft's inventory frames, creative tabs, buttons,
item icons, tooltips, and click sound; active resource packs supply the textures.
Descriptions and text blocks support Markdown source/preview, headings, formatted
text, lists, quotes, links, code, tables, task lists and images. Remote images
require HTTPS and a click/confirmation before downloading; local resource-pack
images load without network access. Formatting tools
work on selected text. Templates speed up common tasks. Use Undo/**Ctrl+Z** for
saved actions, and Pin for a HUD tracker. Cards show three title lines and progress.
Create a private workspace under **Settings > Workspaces**, then grant players
Viewer or Editor access under **People & access**. The creator is its Owner; ownership
can be transferred. Projects inherit workspace permissions. Settings also manage
projects, sprints, custom issue types, workflow graphs and schemes, board columns,
archives and activity. Owners configure workflows; Editors manage projects and
tasks. Server operators have explicit audited recovery commands.

Saved changes are persisted before acknowledgment and pushed to authorized players.
Different task fields or description blocks merge independently; conflicting edits
retain the local draft and offer **Keep my edits** or **Reload saved**. Comments
append independently. Undo checks for later edits before reversing your own changes.
Draft typing stays local. Tasks and projects are archived and can be restored.
Inventory observations remain a separate read-only service. Plain Forge needs no
extra runtime mod or external database. See the [backend guide](backend.md)
for storage, the synchronized API, workflow publication, recovery and verification.

The [UI guide](ui.md) explains the feature modules, fluent composition,
controls, and manual acceptance steps. Development checks and contribution requirements are described in the
[contribution guide](../CONTRIBUTING.md).

## Optional MCP and Harness integration

Two optional modules connect Mira to the neighboring Agent Tools and Harness
projects. `integration-agent-tools-1.7.10` adds MCP task reads, creation, field
edits, transitions and comments through Mira's permission-checked backend.
`integration-harness-1.7.10` adds live task cards with **Open Mira** actions to
Harness chat. The base mod remains independent of both integrations.

See [integration setup](integrations.md) for prerequisite builds, the two
addon JARs, installation and verification. The MCP addon is one
artifact copied into both `mods/` and the MCP host's `plugins/` directory.
[Research notes](integration-research.md) document the neighboring source APIs.

## Setup on Windows

Install Temurin JDK **25.0.4.1+1** for Gradle/core compilation and JDK
**8u504-b01** for Forge compilation and minimal development runs, plus JDK
**17.0.17+10** for RFG's decompiler. Full GTNH
smokes use Java **21.0.10+7**. Core is compiled with `--release 8`; platform
uses a Java 8 compiler. See [exact baseline and downloads](build-baseline.md).

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

For standalone Mira, install **only** `platform-forge-1.7.10\build\libs\mira-0.1.0.jar` in `mods\`.
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
class-embedding fixture after reobfuscation. Production core contains workspace/task
rules, permissions, workflows, transactions, typed task bodies, matching and inventory allocation. The independent
fixture also exercises embedding. [Architecture and ownership](architecture.md)
explain the seam.

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
