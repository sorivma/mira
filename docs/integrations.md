# Optional Agent Tools and Harness integrations

Two optional Java 8 / Forge 1.7.10 modules connect Mira to Agent Tools and Harness.
GitHub Actions builds and tests both against the pinned 0.1.0 API releases.
Runtime acceptance remains a separate manual checklist below. Source/API research
is recorded in [integration-research.md](integration-research.md).

| Module | Installable output under its `build/libs/` | Purpose |
| --- | --- | --- |
| `integration-agent-tools-1.7.10` | `mira-agent-tools-1.7.10-0.1.1.jar` | MCP provider, client bridge, task tools and scoped task-card references |
| `integration-harness-1.7.10` | `mira-harness-1.7.10-0.1.1.jar` | Live `mira:tasks-v1` cards in Harness chat |

Both require the **rebuilt base Mira mod from this checkout**. The Harness module
depends on Mira and Harness, independently of the MCP module. The MCP module also
supports Agent Tools' external stdio host without Harness. Base Mira embeds
neither addon and has no MCP/Harness dependency. Ordinary `build`, `check` and
`:core:check` keep their existing standalone scope.

## Build inputs and commands

For a standalone checkout, fetch released APIs with GitHub CLI and Python 3:

```powershell
python scripts/fetch-integration-apis.py
$integrationArgs = Get-Content .work/integration-apis/gradle-args.txt
.\gradlew.bat @integrationArgs check checkIntegrations assembleIntegrations
```

The script pins both sibling projects to `v0.1.0`, verifies every downloaded file
against its release `SHA256SUMS`, and writes only file paths to the argument file.
No cross-repository credential or checked-out sibling source is required.

For local cross-project development, use the following alternative:

Build API inputs with each neighboring repository's own wrapper and documented
JDK setup. Mira never launches sibling builds automatically.

From `../minecraft-agent-tools`:

```powershell
.\gradlew.bat :mcp-api:jar :bridge-protocol:jar :game-bridge:jar :forge-mcp-1.7.10:assemble
```

From `../minecraft-harness`:

```powershell
.\gradlew.bat :forge-1.7.10:assemble
```

Back in Mira, using the [Mira JDK setup](../README.md#build):

```powershell
# Produce the updated base mod and both addons:
.\gradlew.bat -PmiraIntegrations=true assembleIntegrations

# Unit, boundary and artifact checks:
.\gradlew.bat -PmiraIntegrations=true check checkIntegrations
```

Pass `-PmiraIntegrations=true` to include the two optional projects. For IDE
import, set `miraIntegrations=true` in local/user Gradle properties and refresh
the Gradle model. Without that flag the projects are excluded, so Gradle's
unqualified lifecycle task selection cannot pull them into standalone builds.

With the same flag, individual addon tasks are `:integration-agent-tools-1.7.10:assemble` and
`:integration-harness-1.7.10:assemble`; also run `:platform-forge-1.7.10:assemble`
for the updated base mod. Each addon's `check` includes unit tests and a
`verifyIntegrationJar` task inspecting its reobfuscated artifact. These checks run in both Build and Release workflows.

Default inputs, overridable with `-Pproperty=path`:

| Property | Default path relative to Mira |
| --- | --- |
| `agentToolsRoot` | `../minecraft-agent-tools` (also supplies the bridge license) |
| `agentToolsLicense` | `<agentToolsRoot>/LICENSE`; override when using downloaded API files |
| `mcpApiJar` | `<agentToolsRoot>/core/mcp-api/build/libs/mcp-api-0.1.0.jar` |
| `bridgeProtocolJar` | `<agentToolsRoot>/core/bridge-protocol/build/libs/bridge-protocol-0.1.0.jar` |
| `gameBridgeJar` | `<agentToolsRoot>/core/game-bridge/build/libs/game-bridge-0.1.0.jar` |
| `harnessApiJar` | `../minecraft-harness/forge-1.7.10/build/libs/minecraft-harness-forge-1.7.10-0.1.0-dev.jar` |

Use Harness's **dev** JAR for compilation. Inputs are required when building an
addon; missing inputs do not produce a placeholder artifact. Agent Tools' SPI
and Harness are compile-only. Only the Java 8 GameBridge/protocol and their
license are embedded in the MCP addon; neither addon bundles base Mira/core,
the MCP host/runtime, Harness, Gson or game classes.

## Installation

For the embedded MCP host plus Harness, install on the **client**:

```text
mods/
  mira-0.1.1.jar
  minecraft-agent-tools-mcp-forge-1.7.10-0.1.0.jar
  mira-agent-tools-1.7.10-0.1.1.jar
  minecraft-harness-forge-1.7.10-0.1.0.jar
  mira-harness-1.7.10-0.1.1.jar
config/minecraftagenttools/plugins/
  mira-agent-tools-1.7.10-0.1.1.jar   # Second copy of the same artifact
```

Install base Mira on the server as usual; these addons are client integrations.
Common sided proxies do not load client UI classes or start a bridge on a
dedicated server. Do not install dev, standalone API/core or fixture JARs.

Agent Tools discovers providers only in its configured plugin directory at
startup, so the second MCP-addon copy is necessary. Use `http.plugins` if the
host's directory differs from the default. Keep exactly one provider version
there, use identical copies in both locations, and restart after changes.
Replace old base Mira with the newly built JAR for the client API.

The addon creates `config/minecraftagenttoolsmira.cfg` during pre-init, with
`bridge.port` (loopback-only, default **25585**) and a generated private
`bridge.token`. Choose another port for another concurrent client instance.
The embedded host reads these automatically and advertises its existing HTTP
endpoint to Harness through its existing discovery mechanism. Mira starts no
second MCP HTTP endpoint and changes no agent settings.

For an external stdio host, copy the same addon into that launcher's plugin
directory and set `MAT_MIRA_PORT` / `MAT_MIRA_TOKEN` from the client configuration.
The client still needs the mod copy. Tokens must contain at least 32 characters.

## Local client instance

The current workspace has these existing binaries copied into both local client
instances, using dev JARs in `platform-forge-1.7.10/run/mods/` and installable JARs
in `platform-forge-1.7.10/run/obfuscated/mods/`:

- Harness `0.1.0` and Agent Tools MCP host `0.1.0`, from the neighboring
  builds.
- NEI `2.8.155-GTNH` and CodeChickenCore `1.4.22`, from the Gradle cache (dev)
  and the neighboring Harness instance (installable). This CodeChickenCore
  includes CodeChickenLib.

These are ignored local files, not committed binaries. New checkouts need the
same prerequisite JARs installed. No game or build was launched while staging
them. NEI's MCP adapter/provider is separate and is not needed for Mira's NEI UI.

With API inputs built as above, launch the complete integration setup yourself:

```powershell
.\gradlew.bat -PmiraIntegrations=true :platform-forge-1.7.10:runClient
# Or test installable, reobfuscated output:
.\gradlew.bat -PmiraIntegrations=true :platform-forge-1.7.10:runObfClient
```

The flag makes each client run build and stage both Mira addons in that
instance's `mods/` plus the MCP-provider copy in
`config/minecraftagenttools/plugins/`. The prerequisite Harness/MCP-host/NEI
JARs are already staged locally; these tasks do not rebuild neighboring repos.
Use the host's default plugin directory for this local setup, and keep one
version of each mod/provider. The two instances use the same default MCP/bridge
ports, so run one at a time or give them different configured ports. These
instances now contain client addons; use a separate plain Forge instance for
dedicated-server-only verification.

To stage the new addon JARs without launching Minecraft, use
`-PmiraIntegrations=true prepareLocalIntegrationClient` or
`-PmiraIntegrations=true prepareLocalObfuscatedIntegrationClient`.

## Tools and asynchronous writes

Create a workspace/project in Mira first. `mira_status` returns the independent
Mira session UUID, dimension, current player, synchronization readiness and
revision. Every other tool requires that session/dimension. An unready or
disconnected backend rejects task operations.

| Tool | Use |
| --- | --- |
| `mira_list_workspaces` | Visible active workspaces and player roles |
| `mira_list_projects` | Active projects in a workspace |
| `mira_list_options` | Project issue types, assignable sprints or known players |
| `mira_list_tasks` | Paged active task search/filter and live card references |
| `mira_get_task` | Fields/versions plus `description`, `body`, `comments` or allowed `transitions` |
| `mira_create_task` | Create in a project with `fields.title` and `fields.type` (type UUID) |
| `mira_update_task` | Change title, legacy description, priority, assignee, sprint or parent epic |
| `mira_transition_task` | Follow a logical transition ID with the observed status version |
| `mira_add_comment` | Append a comment as the connected player |
| `mira_get_operation` | Read the outcome of a queued write |

Lists default to 20 entries, maximum 50, with `nextOffset`. Text pages contain
up to 8192 UTF-16 code units with `nextTextOffset`. Supply the returned `revision`
when paging to detect changes. Read **both** description and body: the current
editor keeps most content in ordered body blocks. Body text covers Notes,
Checklists, Materials, Locations and Blockers. Material values are configured
requirements, not inventory observations. Task search matches key/title/legacy
description, not text inside body blocks.

Every mutation needs a fresh UUID `requestId`. Updates need `expected` versions
for exactly the supplied fields, taken from `mira_get_task.fieldVersions`
(an absent field has version zero). The server continues to enforce roles,
workflow requirements, conflicts, persistence and synchronization.

`PENDING` means queued, not saved. Poll `mira_get_operation`; only `OK` means
server acknowledgment. `CONFLICT` requires rereading and resolving the edit;
never silently replace expected versions. `ERROR` carries a rejection.
`UNKNOWN` or disconnect is not proof of failure: re-read state before retrying.
Reuse the request ID only for an identical uncertain retry. Server activity can
confirm a committed request after reconnect. There are at most eight pending
integration writes and 256 in-memory receipts per observed scope.

Workspace administration, workflow configuration, archives, issue-type changes
and body-block editing remain in Mira's UI in this version. Editing the legacy
description does not replace body blocks. No tool opens a GUI or impersonates
another player. Task text and comments remain untrusted content.

## Harness task cards

The addon registers `mira:tasks-v1`, its parser and agent instructions during
client initialization. Task reads include a `widget` object with its ID and
ready-to-use `payload`. Emit that payload in an assistant-message fence:

````text
```mira:tasks-v1
{"session":"<Mira session UUID>","dimension":0,"taskIds":["<task UUID>"]}
```
````

Replace placeholders with actual tool results. One widget accepts 1–8 distinct
task UUIDs. It reads live titles, status, priority, assignee and saved checklist
counts. Rendering/parsing performs no mutation or network I/O. **Open Mira**
rechecks scope/access and opens the task's workspace/project using the existing
Mira UI session. An unfinished edit is retained and must be finished first.
Cards refresh when the synchronized revision changes. Archive, revoked access,
disconnect, dimension/world/player replacement make references unavailable.
Malformed payloads stay literal; narrow cards retain a text fallback.

Harness cards can load without the MCP addon, though automatic discovery of task
IDs through agents requires MCP tools. No persisted Mira body type or second
task database is introduced.

## Manual acceptance for the user

1. Run the build/check commands and inspect the three installable Mira artifacts.
   Confirm both `verifyIntegrationJar` tasks and base Mira without addons.
2. Start client/server and confirm all eleven `mira_*` tools appear only with the
   provider copy installed. `mira_status` becomes ready after synchronization.
3. Browse long/paged task content, create/edit/transition/comment, and observe
   `PENDING` followed by acknowledgment and updates on another player's client.
   Retry the same request ID without duplicate tasks/comments.
4. Exercise Viewer/Editor access, role revocation after enqueue, concurrent edits
   and a persistence failure in a disposable world. Rejections must not appear
   as saved changes.
5. Render/update a card and open a task in another workspace. Repeat with a local
   draft, archived tasks and revoked access. Parsing/rendering must never act.
6. Disconnect/reconnect, switch dimensions/worlds with pending writes and old
   cards, then obtain fresh scope. Old references must not act on the new session.
   Confirm base Mira loads on a dedicated server without addons.
