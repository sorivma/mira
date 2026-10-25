# 0.1.0 release review

This review focused on release readiness, documented module boundaries,
authorization/persistence paths, Markdown network access, optional integrations,
and distribution contents. It is not a claim that every gameplay path was audited.

## Findings addressed

| Finding | Change | Evidence |
| --- | --- | --- |
| Remote Markdown images started arbitrary HTTP requests when task text rendered; the single-worker queue was unbounded | HTTPS images wait for explicit click/confirmation, local destinations are rejected at resolution, and the pending queue is bounded | `MarkdownImagesTest`, platform compilation/tests |
| Optional integrations were absent from hosted build coverage and depended on neighboring SNAPSHOT files/license paths | Version-pinned public API fetcher verifies release checksums; Build and Release test both addons; explicit license input supports isolated checkouts | Local integration tests and reobfuscated-jar inspections; hosted workflow |
| Releases had no enforced version, complete artifact set, or checksummed installation manifest | Tag/version validation, package validation, immutable release policy, all installable/developer jars, ZIP, checksums, provenance | `package-release.py --tag v0.1.0` and release workflow |
| Forge `@Mod` annotations hardcoded 0.1.0 independently of the Gradle version | One generated compile-time platform constant feeds all three annotations; packaging checks final annotation bytecode as well as `mcmod.info` | Alternate-version build and final 0.1.0 packaging validation |
| Base mod metadata still described a bootstrap-only implementation | Metadata now describes the implemented workspace and links the project | Resource expansion and jar metadata verification |
| Machine-specific agent/IDE files were tracked | Removed from the publication tree and added ignores while retaining local files | Git index review |

## Standards and specification checks

The JDK-only core remains separate from Forge and UI. Production classes target
Java 8. The base mod and both optional addons retain dedicated-server-safe common
entry points. Optional integrations route mutations through Mira's existing
permission/version-checked backend; they do not add another database.

Core tests exercise permissions, transactions, workflow rules, conflicts, and
durability. Platform tests cover synchronization helpers, document drafts,
drag-and-drop, task bodies, and Markdown. Integration tests cover scoped task
operations and Harness card behavior. See [verification.md](verification.md)
for exact counts and command evidence.

## Remaining acceptance

Full GTNH client/server smoke, visual UI inspection in-game, and multiplayer
reconnect/conflict scenarios remain manual. Existing foundation runtime logs do
not establish current full-pack compatibility. No game or live agent backend was
started during release preparation.
