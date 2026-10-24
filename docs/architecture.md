# Mira architecture

```text
platform-forge-1.7.10  --->  core  --->  JDK
Forge / Minecraft             no game classpath
```

`core` owns future task, workflow, and board rules and results under `mira.core`.
It is an ordinary Java library, compiled with `--release 8`. Its production
compile and runtime classpaths are JDK-only. Build tooling and test dependencies
are outside that restriction. No domain operations are implemented yet.

`platform-forge-1.7.10` owns `mira.platform.forge1710`: Forge lifecycle,
player lookup, tick scheduling, packets, world saves/NBT, game effects, and
conversion between Minecraft objects and core values. The mod id is `mira`;
the display name is **Mira**. Future client rendering and GUI code belong in
`mira.platform.forge1710.client`, reached only through client-side wiring.
Common bootstrap must remain loadable on a dedicated server. The current
bootstrap only logs initialization; it registers no gameplay content or data.

Core interfaces accept ordinary Java values and core-owned types. A player
identifier may cross this boundary; a game player, world, tile entity, packet,
or NBT object may not. The server owns authoritative task state. The adapter
performs game effects and owns wire/storage formats; client rendering does not
own rules. Add a small interface when an actual use case requires it. Do not
introduce speculative repositories, platform wrappers, or placeholder behavior.

## Build and development instance

The root aggregates checks and assembly. Only the platform applies
RetroFuturaGradle (RFG), the low-level Forge development plugin. No GTNHGradle,
GTNH convention plugins, Blowdryer, or project template is used.
Configuration on demand keeps `:core:check` from configuring/resolving RFG or
preparing Minecraft. Keep root aggregation dependencies as task paths; do not
add eager cross-project platform configuration to the root or core.

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

`verifyModJar` checks metadata, bootstrap, every current core output entry,
and an allowlist of owned content. It rejects bundled runtime/test jars and
fixture classes. Core presently has only package documentation, which javac
does not emit as a runtime class. This is explicitly not evidence of class
embedding. `verifyPackagingFixture` uses a separate core source set containing
one empty probe, the same embedding helper, and RFG reobfuscation. It checks the
transformed fixture jar and loads/instantiates the probe with an isolated class
loader. That proves the mechanism; production-output verification automatically
checks actual core classes when they are introduced. Fixture artifacts live in
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
