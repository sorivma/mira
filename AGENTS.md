# Mira working agreements

## Verification ownership

- The user performs all verification: in-game and runtime checks, builds,
  compilation checks, automated tests, linting, and other validation commands.
- Do not run those checks, launch game instances, or automate Minecraft unless
  the user explicitly asks you to do so.
- Continue implementation and refactoring through source inspection and edits.
  You may add or update tests, but leave their execution to the user.
- In handoffs, state which checks were not run and give the user the relevant
  commands or manual steps. Do not claim unexecuted checks passed.

## UI organization

- Keep Minecraft screen lifecycle and input forwarding thin.
- Organize workspace UI by feature: shell, board, backlog, issue list, issue
  details, and editor. Keep navigation and form actions outside rendering.
- Compose views using the shared fluent UI module; keep drawing, clipping,
  layout, and hit testing out of feature views.
- Keep disposable demo state in the platform client. Authoritative task and
  workflow rules belong in `core` when the real domain is implemented.
- Preserve dedicated-server-safe loading and the JDK-only core described in
  `docs/architecture.md`.
