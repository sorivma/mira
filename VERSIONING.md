# Versioning and releases

Mira uses [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).
The single source of truth is `version` in `gradle.properties`.
All Mira modules share that version; Git tags are `vMAJOR.MINOR.PATCH`.
The first published release is **0.1.0**.

The platform generates a compile-time `BuildVersion.VERSION` from the Gradle
version. All three `@Mod` annotations use that constant; resource metadata uses
the same Gradle value. Release packaging inspects the final annotation bytecode
and `mcmod.info` so a hardcoded or stale version cannot silently ship.

## Compatibility contract

During `0.x`, a minor release may change Java APIs, MCP schemas, saved data,
network messages, or addon contracts. A patch release preserves those contracts
and contains compatible fixes. After `1.0.0`, incompatible changes require a
major version. Prereleases use `-alpha.N`, `-beta.N`, or `-rc.N`.

The version covers the base mod, domain library, and both optional addons.
Use matching Mira versions on client and server and for its addons.
Minecraft/Forge compatibility is explicit in release notes; it is not inferred
from the project's SemVer number. Harness and Agent Tools are independently
versioned; supported API releases are pinned in `scripts/fetch-integration-apis.py`.

Changes to world persistence must document migrations and rollback limitations.
Back up worlds before upgrading. Release tags and published assets are immutable:
correct a bad release with a new version, never replace a jar under an old tag.

## Maintainer procedure

1. Update `gradle.properties`, installation examples, the changelog, and
   `docs/release-notes/vVERSION.md`. Review any changed sibling API pins.
2. Ensure the exact commit passes **Build** and **Security** on GitHub.
   Build validates core isolation, standalone packaging, and both integrations.
3. Create an annotated tag on that commit and push it:
   `git tag -a vVERSION -m "Mira VERSION"`, then `git push origin vVERSION`.
4. **Release** checks tag/version equality, rebuilds/tests every artifact, verifies
   embedded metadata/licenses, and prepares jars, a ZIP, installation instructions,
   and `SHA256SUMS`. Public artifacts receive GitHub provenance attestations.
5. The workflow uploads all assets to a draft, then publishes it. For a failed
   draft upload, inspect/remove the incomplete draft before retrying. Published
   releases are never overwritten.
6. Inspect the release assets and run the relevant manual acceptance checklist.
   Never describe CI as proof of in-game or full-modpack compatibility.

Only tag-triggered release jobs receive write permissions. Ordinary builds use
read-only repository access. Actions are pinned to commit SHAs; Dependabot opens
reviewable updates.
