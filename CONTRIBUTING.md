# Contributing to Mira

Start with a reproducible bug report or a concrete user workflow. Keep changes
focused and explain the resulting behavior in the pull request.

## Development setup

Use the wrapper and JDK setup in [README](README.md#build).
The exact baseline is in [docs/build-baseline.md](docs/build-baseline.md).
Run from the repository root:

```powershell
.\gradlew.bat :core:check
.\gradlew.bat check build
.\scripts\verify-core-boundary.ps1
```

For integration changes, fetch the version-pinned public APIs and run
`check checkIntegrations assembleIntegrations` with the generated arguments
shown in [README](README.md#build). A fresh checkout does not need sibling
repositories. Do not commit downloaded APIs, game files, logs, saves, tokens,
or local IDE/agent settings.

## Code boundaries

- Keep `core` JDK-only and Java 8 compatible.
- Keep common mod loading safe on a dedicated server.
- Execute Minecraft operations on its game thread; network I/O stays off it.
- Keep screen lifecycle and input forwarding thin. Share drawing, clipping,
  layout, and hit testing through the fluent UI module.
- Domain mutations must enforce permissions and observed versions on the server,
  persist before acknowledgment, and reject stale world/session references.
- Optional addons must not bundle the base mod, Harness, or MCP runtime/SPI.
- Treat task text, Markdown links, and tool output as untrusted content.

Use four-space indentation and the surrounding style. Add targeted tests for
behavior changes, especially authorization, concurrency, persistence, and
packaging. Tests that require a game remain manual and must be described as such.

## Pull requests and releases

Use concise Conventional Commit titles such as `fix: retain drafts after conflict`
or `feat: add task filters`. Update [CHANGELOG.md](CHANGELOG.md) and documentation
for user-visible changes. Note exactly which checks ran. The maintainer publishes
versioned releases under [VERSIONING.md](VERSIONING.md).

Contributions are distributed under the repository's MIT license.
