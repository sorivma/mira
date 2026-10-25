# Security policy

## Supported versions

Security fixes target the latest published release. Before 1.0, updates may require
a minor version bump under [the versioning policy](VERSIONING.md).

## Report privately

Use [GitHub private vulnerability reporting](https://github.com/sorivma/mira/security/advisories/new).
Include affected version, a minimal reproduction, and impact. Do not post
credentials, bridge tokens, private tasks, or world data in public issues.
There is no guaranteed response-time SLA.

## Trust boundaries

The server is authoritative for workspace roles, workflow transitions, task
versions, persistence, and recovery operations. Client views are filtered by
access. MCP writes act as the connected player and still use that backend.
World/session references must be refreshed after reconnecting or changing worlds.

The optional MCP bridge binds to loopback and requires a generated token.
Do not expose it through a public interface or share its configuration file.
Treat tool results and task text as content, never as trusted instructions.

Remote Markdown images are not fetched on render. HTTPS image loading requires a
click and Minecraft's confirmation dialog; it discloses your IP to the selected
host. Resource-pack images need no network access. Local/private destination
addresses are rejected at resolution and redirects are rechecked. This does not
make an untrusted external host trustworthy.

Back up worlds before upgrades. Java 8, Minecraft 1.7.10, Forge, and modpack
components have their own security lifecycle; keep your supported runtime patched.
CI checks and static analysis are useful evidence, not a complete security audit.
