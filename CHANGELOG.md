# Changelog

Changes are grouped by released version. Versioning follows [SemVer](VERSIONING.md).

## [Unreleased]

## [0.1.1] - 2026-10-25

### Fixed

- Prevent duplicate input in single-line and multiline fields with lwjgl3ify 3.x
  (including GTNH 2.9.0-RC-2): physical key events retain navigation and shortcuts,
  while separate SDL committed-text events insert printable characters once.
- Route multiline text commits through the native text field so lwjgl3ify's IME
  buffer is consumed and cannot leak into another focused field.
- Preserve intentional repeated letters, Unicode text, legacy LWJGL input, and
  slash-menu actions; add event replay regressions for both real control types.

## [0.1.0] - 2026-10-25

### Added

- Server-owned workspaces, projects, tasks, roles, workflows, sprints, comments,
  archives, activity, durable storage, and synchronized updates.
- Board, backlog, issue list, document editor, templates, material tracking,
  optional NEI picker, task HUD, field-level conflicts, and guarded undo.
- Optional Agent Tools MCP provider with eleven scoped task tools and
  permission-checked asynchronous writes.
- Optional Harness live task cards with an Open Mira action.
- Release automation, checksummed distributions, provenance, pinned integration
  APIs, contribution guides, and repository security/community configuration.

### Fixed

- Remote task images no longer initiate network requests merely by rendering:
  HTTPS downloads require a click and confirmation; the download queue is bounded.
- Integration build defaults now use the released Agent Tools 0.1.0 API names
  and support an explicit license input for standalone CI checkouts.

### Compatibility

- Minecraft 1.7.10 / Forge 10.13.4.1614; production bytecode targets Java 8.
- GTNH 2.9.0-RC-2 is a target baseline, with full-pack acceptance still outstanding.

[Unreleased]: https://github.com/sorivma/mira/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/sorivma/mira/releases/tag/v0.1.0
