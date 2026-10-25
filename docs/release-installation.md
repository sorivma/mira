# Installing Mira 0.1.0

## Base mod

Install `mira-0.1.0.jar` in `mods/` on client and server running
Minecraft 1.7.10 / Forge 10.13.4.1614. Press **J** in a world.
No external database or optional addon is required.

## Optional client addons

| File | Installation |
| --- | --- |
| `mira-agent-tools-1.7.10-0.1.0.jar` | Copy to both `mods/` and `config/minecraftagenttools/plugins/`; requires the Minecraft Agent Tools 0.1.0 MCP host |
| `mira-harness-1.7.10-0.1.0.jar` | Copy to `mods/`; requires Minecraft Harness 0.1.0 |
| `*-dev.jar` | Developer compile API; do not install in the game |
| `mira-core-0.1.0.jar` | Standalone domain library; already embedded in base Mira |

Install the same Mira version everywhere. The two addons are independent, client
integrations; the server needs only the base mod. Remove older copies of a mod
or provider before upgrading and restart Minecraft. Back up the world first.

The ZIP contains all three mods plus developer jars and notices; it is **not**
a modpack and should not be copied wholesale into `mods/`.
Download prerequisites from:

- https://github.com/sorivma/minecraft-agent-tools/releases/tag/v0.1.0
- https://github.com/sorivma/minecraft-harness/releases/tag/v0.1.0

Verify each asset against `SHA256SUMS` (`Get-FileHash -Algorithm SHA256 <file>`
on PowerShell, or `sha256sum -c SHA256SUMS` after downloading all listed assets).
For public release provenance use `gh attestation verify <file> --repo sorivma/mira`.

Full-pack GTNH acceptance remains outstanding. See the repository's verification
record and integration guide for behavior, controls, limitations, and manual checks.
