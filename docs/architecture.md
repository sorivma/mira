# Mira architecture

```text
platform-forge-1.7.10  --->  core  --->  JDK
Forge / Minecraft             no game classpath
```

`core` owns task-body rules and values under `mira.core.taskbody`, and the workspace,
task, role, sprint, board and workflow domain under `mira.core.jira`.
It is an ordinary Java library, compiled with `--release 8`. Its production
compile and runtime classpaths are JDK-only. Build tooling and test dependencies
are outside that restriction. The ordered `TaskBody` contains independently
typed `TaskWidget` instances with detached copying and optional progress.
Material matching and shared inventory allocation operate on portable values.

`platform-forge-1.7.10` owns `mira.platform.forge1710`: Forge lifecycle,
player lookup, tick scheduling, packets, world saves/NBT, game effects, and
conversion between Minecraft objects and core values. The mod id is `mira`;
the display name is **Mira**. Client rendering and GUI code live in
`mira.platform.forge1710.client`, reached through a sided proxy.
`CommonProxy` does not reference client classes. `MiraClient` registers the
workspace keybinding and applies Jira replies on the client tick thread. No gameplay
blocks or items are registered. `InventoryNetwork` registers a request/reply
channel: only the sending player's carried inventory is observed, on the server
tick thread, at a throttled rate. Replies contain registry identity, metadata,
tag fingerprints, ore groups, counts, and availability warnings, without raw
item NBT. Task state uses the independent `jira/JiraNetwork` channel.

`JiraDatabase` is a synchronized, JDK-only transaction API. It owns detached database
snapshots, field revisions, workspace-wide permissions, immutable published workflow
generations, issue numbering, activity and conflict-aware Undo. Transactions check
the sender's current role, compare only the fields they write (plus explicit read
guards), apply domain rules, and call a persistence seam before publishing state.
The platform supplies the player's server UUID; client identity is never trusted.
See [backend.md](backend.md) for the entity schema and command contracts.

`jira/WorldDatabase` stores a versioned, checksummed database at
`<overworld>/data/mira-workspaces.dat`, shared by all dimensions. Each commit writes
and syncs a temporary file, preserves the preceding database as `.bak`, then replaces
the main file. Corruption fails closed and requires explicit recovery. The common
Forge adapter queues bounded packet fragments for server-thread processing. It pushes
permission-filtered snapshots after commits, including when membership is revoked.
The client applies snapshots and acknowledgment callbacks on its own tick thread.

The workspace screen is a thin lifecycle/input adapter. Feature views compose
the shared UI module, while `WorkspaceSession` owns navigation, filters, draft
handling without importing Minecraft. `RemoteWorkspace` translates server snapshots
into stable `WorkspaceData` presentation objects and builds field/block patches.
`IssueDraft` keeps edit values and their original versions separate until saved;
remote updates cannot silently rebase a draft over concurrent edits. `WorkspaceSettings`
owns administration form actions, and its view composes shared controls. `Ui.graph`
owns graph layout, painting, hit testing and drag forwarding. `DemoWorkspace` now lives
only in platform test sources. See the
[UI guide](ui.md) for ownership and extension points.

Body rendering is registered in the client `WidgetCatalog` by model class.
`DocumentView` composes one block document without dispatching on every widget type.
`WidgetContext` supplies shared input state, inventory observations, actions,
and undo-aware mutations. `DocumentDraft` owns a detached ordered body, selection,
paragraph splitting, insertion and drag ordering. Save commits the complete
description with one session checkpoint; Cancel discards it. Legacy opening
description text is presented as the first Note block, which can move anywhere.
On document save all content lives in the ordered body and the legacy string
is cleared, preventing duplicate rendering. `IssueDraft.document()` uses the
same model for creation/full edits. Read-only checkbox actions still use session
checkpoints; while a document is being edited, all block actions affect its draft.
Optional portable storage readers are registered in
`CarriedInventory`, separately from widget types. Known adapters read explicit
inventory schemas or storage APIs; arbitrary nested NBT is never counted as
items. The optional `NeiBridge` is loaded only on clients with NEI. Its compile
dependencies are not bundled or required for plain Forge.

Core interfaces accept ordinary Java values and core-owned types. A player
identifier may cross this boundary; a game player, world, tile entity, packet,
or NBT object may not. The server owns authoritative task state. The adapter
performs game effects and owns wire/storage formats; client rendering does not
own rules. Add a small interface when an actual use case requires it. Do not
introduce speculative repositories, platform wrappers, or placeholder behavior.

## Build and development instance

Optional `integration-agent-tools-1.7.10` and `integration-harness-1.7.10` modules
depend on the base platform; no dependency points from core/base Mira into either
addon. `MiraClientAccess` is a client-thread seam for independent session tokens,
detached permission-filtered snapshots, lightweight cached task cards, asynchronous
commands and explicit task navigation. It reuses `JiraClient` and its server channel.
`WorkspaceSession` owns navigation across workspaces/projects; the screen only forwards
the request. The existing screen/session is reused so integration clicks retain local
drafts and do not accumulate backend listeners.

The Agent Tools addon embeds the neighbor's GameBridge/protocol and supplies a
portable ToolProvider loaded from the host's plugin directory. Socket I/O remains
off the game thread; bridge callbacks run on client ticks. Queued writes report
PENDING until a server reply, with stable request IDs and expected field versions.
The Harness addon uses its public WidgetCanvas and widget registry, reads live
Mira projections and performs navigation only on a user click. It depends directly
on Mira, independently of the MCP addon. Both have sided bootstraps. See
[integration setup](integrations.md) and [source research](integration-research.md).

The root aggregates base checks and assembly; optional addons have explicit
`assembleIntegrations` / `checkIntegrations` tasks. The platform and optional
Forge integrations apply RetroFuturaGradle (RFG), the low-level Forge development plugin. No GTNHGradle,
GTNH convention plugins, Blowdryer, or project template is used.
Configuration on demand keeps `:core:check` from configuring/resolving RFG or
preparing Minecraft. Keep root aggregation dependencies as task paths; do not
add eager cross-project platform configuration to the root or core.

`-PmiraIntegrations=true` includes the optional projects for their builds or IDE
import. Without it, unqualified lifecycle selectors such as `build` and `check`
cannot select addon tasks and require neighboring API artifacts. Aggregation
tasks fail with an enablement message if invoked without the flag.

RFG uses the conventional `platform-forge-1.7.10/run/` development instance:
mods, config, logs, saves, and native libraries live there. The dedicated
server uses the same conventional instance root; obfuscated client/server
runs use `run/obfuscated/`. These are disposable
local data and ignored by Git. `clean` removes build outputs, not worlds.
Development runs use a minimal Forge environment. Full GTNH compatibility
requires separate pack smoke tests on Java 21.

## Packaging and verification

The platform has a one-way Gradle project dependency on core. Its `jar` task
explicitly embeds core compiled classes/resources before RFG's `reobfJar`
transformation. The installable artifact is
`platform-forge-1.7.10/build/libs/mira-0.1.0.jar`. The `-dev.jar` is for
development. Only the final jar goes in a pack's `mods/`; no separate core jar,
nested loader, or shaded game dependencies are required.

The platform embeds CommonMark Java 0.21.0, its GFM tables/strikethrough/task-list
and autolink extensions, and autolink-java, with their licenses. This release
line retains Java 8 compatibility; [later CommonMark versions require Java 11](https://github.com/commonmark/commonmark-java/blob/main/CHANGELOG.md).
Only the platform depends on them. Markdown rendering, link confirmation,
asynchronous bounded image loading, wrapping and pointer handling live in the
client UI module. Dedicated-server entry points do not reach those classes.

`verifyModJar` checks metadata, bootstrap, every current core output entry,
the exact resolved Markdown entries/licenses, and an allowlist of owned content.
It rejects unrelated runtime/test libraries, nested jars and
fixture classes. `verifyPackagingFixture` uses a separate core source set containing
one empty probe, the same embedding helper, and RFG reobfuscation. It checks the
transformed fixture jar and loads/instantiates the probe with an isolated class
loader. That proves the mechanism; production-output verification automatically
checks the actual core classes. Fixture artifacts live in
`build/fixtures/`, never the installable mod.

`core:verifyCoreDependencies` rejects all extra production dependencies and
classpath files, including project dependencies, configuration inheritance,
transitive dependency roots, and renamed/local jars. This intentionally enforces
the stricter JDK-only foundation rule. Revisit it explicitly if a concrete use
case needs an ordinary Java production library. `verifyCoreReferences` scans
production sources, including fully qualified and Unicode-escaped names;
`verifyCoreBytecode` scans compiled constants after compilation. Both reject
platform, Minecraft, Forge/FML, GTNH, UI, LWJGL, and Netty package references.
Dependency rejection also blocks unknown game libraries without relying on a
complete package-name list. These are development guards, not a Java sandbox.

`check` runs boundary and artifact checks. `scripts/verify-core-boundary.ps1`
exercises temporary forbidden controls and removes them in `finally` blocks.
No dummy business logic is added to production.
