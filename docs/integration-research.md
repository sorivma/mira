# Mira integration research

> **Historical design record — 2026-10-25.** This document preserves the
> source findings and implementation recommendations made before release
> preparation, including the original `0.1.0-SNAPSHOT` artifact names and
> unexecuted acceptance steps. For current installation/build commands use
> [integrations.md](integrations.md); for completed checks and remaining runtime
> acceptance use [verification.md](verification.md).

The original inspection used Agent Tools commit
[`a0e2af0`](https://github.com/sorivma/minecraft-agent-tools/commit/a0e2af04aaa8b763fee47cd731e84da60a01e536)
and Harness commit
[`80cd73a`](https://github.com/sorivma/minecraft-harness/commit/80cd73aa1c25060e12231d766acb65c3b9b97dec).
Cross-repository source links below are pinned to those snapshots, not current
releases. During that original inspection, no builds, compilation, tests,
linting, Minecraft launches, or runtime probes were performed.

## Recommended module boundary

Use two optional Forge integration artifacts owned by Mira:

1. `integration-agent-tools-1.7.10`: the Mira tool contract/provider and the
   client adapter that serves the local player's synchronized Mira tasks.
2. `integration-harness-1.7.10`: a registered task-card renderer and explicit
   navigation actions.

Both depend on the base Mira mod rather than embed another copy of it. The
Harness integration does not require the MCP integration.
Keep the shared client task-reading/navigation seam in Mira's platform module;
neither integration needs to own task persistence or workflow rules. Keep
`core` JDK-only. This recommendation follows Mira's existing
[architecture](architecture.md) and [UI ownership](ui.md), where the server owns
the domain and the platform owns transport, projection, and rendering.

The first artifact can be a **dual-purpose JAR**: install the same artifact in
`mods/` for Forge and in `config/minecraftagenttools/plugins/` for MCP provider
discovery. These are two copies of one build artifact, not two separate module
outputs. The existing host does not discover providers from the normal mod
classpath. If one-copy installation is desired, a deliberate startup provider
installation mechanism would be required; do not silently assume the host scans
`mods/`. The concrete source behavior is described below.

## Agent Tools extension seam

The Java 8 SPI is
[`ToolProvider`](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/mcp-api/src/main/java/dev/minecraftagenttools/api/ToolProvider.java):

```java
String id();
List<Map<String, Object>> tools();
void validate(String tool, Map<String, Object> arguments);
Map<String, Object> call(String tool, Map<String, Object> arguments,
                         ToolContext context) throws Exception;
default String instructions();
```

Providers require a public no-argument constructor. Descriptors and results use
JSON-compatible maps, lists, and scalar values. Calls run off the game thread.
The service descriptor is
`META-INF/services/dev.minecraftagenttools.api.ToolProvider`, containing the
provider's fully qualified class name. The existing
[BetterQuesting provider](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/tools/betterquesting-tools/src/main/java/dev/minecraftagenttools/betterquesting/provider/BetterQuestingToolProvider.java)
delegates validation to its portable contract and calls
`context.callBridge("betterquesting", tool, arguments)`; Mira can use the named
connection `mira`.

[`PluginLoader`](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/mcp-runtime/src/main/java/dev/minecraftagenttools/mcp/plugins/PluginLoader.java)
loads JARs from one startup directory and deliberately uses `findResources` for
the service descriptor, excluding services on the parent classpath. Therefore a
service descriptor inside a Forge mod in `mods/` is insufficient on its own.
There is no live provider refresh. The
[registry](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/mcp-runtime/src/main/java/dev/minecraftagenttools/mcp/plugins/ToolRegistry.java)
rejects duplicate provider IDs and tool names. Do not leave multiple versioned
copies of a Mira provider in the plugin directory.

The embedded
[`McpHostMod`](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-mcp-1.7.10/src/main/java/dev/minecraftagenttools/forge/mcp/McpHostMod.java)
has mod ID `minecraftagenttoolsmcp`; it loads providers in post-init. Its default
plugin directory is `config/minecraftagenttools/plugins/`, configurable through
the host's `http.plugins` property. It owns the authenticated MCP HTTP endpoint
and already advertises it to Harness through Forge IMC. Mira does not need a
second HTTP endpoint or an MCP SDK.

### Bridge configuration and scheduling

The host obtains named bridge credentials from files matching
`minecraftagenttools([a-z0-9_]*).cfg`, with `bridge.port` and `bridge.token`.
For `context.callBridge("mira", ...)`, the corresponding file must therefore be
`config/minecraftagenttoolsmira.cfg`. An external launcher uses
`MAT_MIRA_PORT` and `MAT_MIRA_TOKEN` instead. Tokens need at least 32 characters,
and the configured port must be 1024–65535. These constraints come from
[host configuration discovery](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-mcp-1.7.10/src/main/java/dev/minecraftagenttools/forge/mcp/McpHostMod.java)
and
[`BridgeConnections`](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/mcp-runtime/src/main/java/dev/minecraftagenttools/mcp/bridge/BridgeConnections.java).

[`GameBridge`](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/game-bridge/src/main/java/dev/minecraftagenttools/gamebridge/GameBridge.java)
is a reusable loopback transport with this constructor:

```java
GameBridge(int port, String token,
    BiConsumer<String, Map<String, Object>> validate,
    BiFunction<String, Map<String, Object>, Map<String, Object>> reader)
```

Socket workers authenticate and validate detached JSON, then enqueue work.
Calling `tick()` executes queued readers on the caller's thread; the Mira
client adapter must call it on the client tick thread. The queue contains at
most 32 jobs, the bridge processes at most four jobs within a five-millisecond
tick budget, and requests expire after five seconds. Readers return the data
object; the bridge wraps it in `{ "ok": true, "data": ... }`, or returns an
error envelope on failure. The
[frame contract](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/bridge-protocol/src/main/java/dev/minecraftagenttools/protocol/transport/Frames.java)
uses newline-delimited JSON with a 4 Mi-character limit. Page and bound Mira
responses well below that limit.

The MCP host embeds runtime, SPI, and bridge-protocol classes, but **does not
embed `GameBridge`**. Existing adapters embed the game-bridge implementation
and protocol themselves; its Gson dependency is compile-only version 2.2.4,
supplied by the game. If Mira reuses this implementation, account for its
runtime classes explicitly and avoid bundling unrelated host/Harness classes.
See the [host build](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-mcp-1.7.10/build.gradle.kts),
[game-bridge build](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/core/game-bridge/build.gradle.kts),
and [BetterQuesting adapter build](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-betterquesting-1.7.10/build.gradle.kts).

### Session and authority

The existing
[BetterQuesting reader](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-betterquesting-1.7.10/src/main/java/dev/minecraftagenttools/forge/betterquesting/BqReader.java)
creates a fresh session identity when world, dimension, player object, or player
identity changes. It rechecks scope when queued reads execute. This is a useful
precedent for Mira: begin with a status tool that returns readiness and scope;
require that scope for subsequent reads and actions. An old card or queued
request must not attach itself to a different world just because a task ID
happens to exist there.

Mira already supplies permission-filtered snapshots and authenticated writes:
[`JiraNetwork`](../platform-forge-1.7.10/src/main/java/mira/platform/forge1710/jira/JiraNetwork.java)
derives the actor from the sending player's server UUID and returns
`JiraDatabase.view(actor)`.
[`JiraClient`](../platform-forge-1.7.10/src/main/java/mira/platform/forge1710/client/workspace/JiraClient.java)
applies deliveries and completion callbacks on the client tick thread.
Its existing `backend()` accessor and `WorkspaceBackend` are package-private,
so extensions need a small public platform facade rather than access to the
workspace internals. For writes, reuse command submission and server-side
revision/role checks. Never mutate the client snapshot or open a second world
database from an MCP worker.

The synchronous `GameBridge` reader cannot block the client thread waiting for
a Mira packet acknowledgment. The implementation plan uses asynchronous
mutation submission that returns a request ID and a `get_operation` tool to
obtain the server acknowledgment. A queued command is not evidence of a
successful saved change. Read tools and explicit UI navigation can use the
existing synchronous bridge directly.

## Harness widget seam

The public client entry point is
[`HarnessWidgets`](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/forge-1.7.10/src/main/java/dev/minecraftharness/forge/HarnessWidgets.java):

```java
HarnessWidgets.register(String id, String instructions,
                        Function<String, Widget> parser);
```

Register during client initialization before a world opens. The registry is
snapshotted for each world session and used for both rendering and agent
instructions. Use a namespaced, versioned ID such as `mira:tasks-v1`; duplicate
and unnamespaced IDs are rejected. Compile against the Harness dev JAR with
`compileOnly` and declare `required-after:minecraftharness`. Do not embed Harness
classes. These requirements and lifecycle details are documented in the
[Harness widget API](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/docs/widget-api.md).

[`Widget`](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/chat-ui/src/main/java/dev/minecraftharness/chat/widget/Widget.java)
provides `title()`, `plainText()`, `height(int width)`, and
`render(WidgetCanvas)`. Optional methods supply metric-aware height and declare
whether rendering includes the fallback text.
[`WidgetCanvas`](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/chat-ui/src/main/java/dev/minecraftharness/chat/widget/WidgetCanvas.java)
provides text, rectangles, item icons, clipped native drawing, and explicit
`button(..., Runnable callback)` actions. The host owns clipping and hit
registration. A callback executes only after user activation on the client/UI
thread; parsing and rendering must not execute actions or block.

Only complete top-level assistant fences matching the registered widget ID are
parsed. User messages and tool output are not interpreted as widgets. The
backend persists the original assistant text, so unknown formats remain
readable after an extension is removed. Parser failures retain literal text.
The widget must return immutable, useful fallback text because the host
captures it once. Titles are limited to 256 characters; fallback text to 32,768;
registration instructions to 8,192; visual height to 4096 pixels; at most 16
successful widgets are parsed per message. See the
[widget API contract](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/docs/widget-api.md).

For a Mira task card, a scope plus workspace/task identifiers is preferable to
agent-authored progress or workflow data. Resolve the visible task from Mira's
synchronized snapshot. Recheck the current session, task existence, and access
before every live read and explicit Open action. The adjacent
[quest widget](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/integrations/harness/betterquesting/src/main/java/dev/minecraftagenttools/harness/betterquesting/QuestWidget.java)
and its
[client facade](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-betterquesting-1.7.10/src/main/java/dev/minecraftagenttools/forge/betterquesting/BqClient.java)
demonstrate this pattern, including expiration of cards from earlier worlds.

Harness already consumes the host's data-only MCP advertisements; the Mira
widget module should not configure the agent backend or copy MCP credentials.
The full lifecycle is in
[Agent Tools discovery](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/docs/mcp-discovery.md)
and [Harness discovery](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/docs/mcp-discovery.md): discover
during init, advertise after successful endpoint startup in post-init, collect
advertisements at load-complete.

## Packaging and compatibility conclusions

- The neighboring Forge adapters and widget integrations target Java 8 and
  Forge 1.7.10 using RFG 1.4.9. Mira uses RFG 2.0.6 and explicitly pins its
  mapping settings. Keep Mira's own platform/build conventions rather than
  including the neighboring builds wholesale. Sources:
  [Mira platform build](../platform-forge-1.7.10/build.gradle.kts),
  [Agent Tools adapter build](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/adapters/forge-mcp-1.7.10/build.gradle.kts),
  [Harness platform build](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/forge-1.7.10/build.gradle.kts).
- Agent Tools already makes Harness integrations optional via an explicit
  `harnessApiJar` property. Without it the base build works and the integration
  reports that its JAR was not built. This is an appropriate model for optional
  Mira integration inputs, with clear commands and no hidden sibling builds.
  Sources: [integration build](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/integrations/harness/betterquesting/build.gradle.kts),
  [integration guide](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/integrations/harness/README.md).
- Keep common Forge bootstraps free of eager Minecraft client/Harness widget
  references; load clients through sided proxies. Client optional modules must
  not make the base Mira JAR require Harness or MCP. Source:
  [Mira dedicated-server boundary](architecture.md).
- Mira's `WidgetCatalog.register` supports a new **renderer**, but its persisted
  task-body codec currently recognizes only Note, Checklist, Materials,
  Location, and Links. Adding a new persisted body-widget type requires a core
  representation and codec/rule changes; registering a client renderer alone
  cannot persist it. A Harness chat task card is a separate concept and needs
  no new persisted body type. Sources:
  [WidgetCatalog](../platform-forge-1.7.10/src/main/java/mira/platform/forge1710/client/widgets/WidgetCatalog.java),
  [BodyValues](../core/src/main/java/mira/core/jira/BodyValues.java).

## User-owned acceptance work

The sibling development inputs can be produced by the user from their
respective repository roots:

```powershell
# In ../minecraft-agent-tools:
.\gradlew.bat :mcp-api:jar :bridge-protocol:jar :game-bridge:jar :forge-mcp-1.7.10:assemble

# In ../minecraft-harness:
.\gradlew.bat :forge-1.7.10:assemble
```

The source build configurations name these input artifacts:

| Input | Path relative to the sibling repository |
| --- | --- |
| Agent Tools SPI | `core/mcp-api/build/libs/mcp-api-0.1.0-SNAPSHOT.jar` |
| Bridge protocol | `core/bridge-protocol/build/libs/bridge-protocol-0.1.0-SNAPSHOT.jar` |
| Game bridge implementation | `core/game-bridge/build/libs/game-bridge-0.1.0-SNAPSHOT.jar` |
| Embedded MCP host, installable | `adapters/forge-mcp-1.7.10/build/libs/minecraft-agent-tools-mcp-forge-1.7.10-0.1.0-SNAPSHOT.jar` |
| Harness development API | `forge-1.7.10/build/libs/minecraft-harness-forge-1.7.10-0.1.0-dev.jar` |

These paths are derived from the [Agent Tools root version](https://github.com/sorivma/minecraft-agent-tools/blob/a0e2af04aaa8b763fee47cd731e84da60a01e536/build.gradle.kts),
its module builds linked above, and the [Harness root version](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/build.gradle.kts)
and [platform build](https://github.com/sorivma/minecraft-harness/blob/80cd73aa1c25060e12231d766acb65c3b9b97dec/forge-1.7.10/build.gradle.kts). Artifact
existence and contents have not been checked.

Once implementation is present, the user should build the optional modules with
the documented development-JAR properties, inspect the produced installable
artifacts, and exercise these flows:

1. Base Mira without optional JARs, including dedicated-server loading.
2. MCP provider discovery only when its artifact is installed in the configured
   plugin directory; no duplicate provider versions.
3. Status, paged task reads, permission filtering, unavailable worlds, and stale
   scope after disconnect/reconnect or a dimension change.
4. Harness card parsing, readable fallback, live updates, and Open actions;
   malformed/unknown blocks must remain text.
5. If mutation tools are implemented: server acknowledgment, conflicts, revoked
   membership, world changes during pending work, and no success response before
   persistence succeeds.

This document records source findings and implementation recommendations; none
of these acceptance steps has been run.

## Implementation handoff

The two modules and shared `MiraClientAccess` facade are now present. Concrete
API-input defaults, Gradle commands, installation locations, tool behavior and
manual acceptance are documented in [integrations.md](integrations.md).
Tool schemas and behavior annotations follow the upstream
[MCP tools specification](https://modelcontextprotocol.io/specification/2025-06-18/server/tools).
The existing Agent Tools host continues to own MCP transport/result serialization;
Mira implements its provider SPI rather than a new MCP server.

Mira includes its addon projects only with `-PmiraIntegrations=true`. Unlike
fully qualified task paths, unqualified selectors execute matching tasks across
subprojects, as documented by [Gradle's CLI guide](https://docs.gradle.org/current/userguide/command_line_interface.html#sec:executing_tasks_in_multi_project_builds).
Explicit inclusion therefore preserves existing standalone `build`/`check`
commands without requiring sibling artifacts; enable the same property for IDE
import of the addons.
