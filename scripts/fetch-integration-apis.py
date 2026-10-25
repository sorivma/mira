"""Fetch exact released compile APIs, verify SHA-256, and emit Gradle arguments.

Requires GitHub CLI. No token with access to other repositories is needed for
public releases. Credentials are never written to the generated file.
"""
import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / ".work" / "integration-apis"
VERSION = "0.1.0"
ASSETS = {
    "minecraft-agent-tools": [f"mcp-api-{VERSION}.jar", f"bridge-protocol-{VERSION}.jar",
                              f"game-bridge-{VERSION}.jar", "LICENSE"],
    "minecraft-harness": [f"minecraft-harness-forge-1.7.10-{VERSION}-dev.jar"],
}

for repository, names in ASSETS.items():
    folder = DEST / repository
    folder.mkdir(parents=True, exist_ok=True)
    command = ["gh", "release", "download", f"v{VERSION}", "--repo", f"sorivma/{repository}",
               "--dir", str(folder), "--clobber"]
    for name in [*names, "SHA256SUMS"]:
        command.extend(["--pattern", name])
    subprocess.run(command, check=True)
    checksums = {}
    for line in (folder / "SHA256SUMS").read_text(encoding="utf-8-sig").splitlines():
        digest, filename = line.split(maxsplit=1)
        checksums[filename.lstrip("*")] = digest.lower()
    for name in names:
        actual = hashlib.sha256((folder / name).read_bytes()).hexdigest()
        if checksums.get(name) != actual:
            raise SystemExit(f"Checksum mismatch or missing checksum: {repository}/{name}")

args = ["-PmiraIntegrations=true"]
for prop, repository, name in [
    ("mcpApiJar", "minecraft-agent-tools", f"mcp-api-{VERSION}.jar"),
    ("bridgeProtocolJar", "minecraft-agent-tools", f"bridge-protocol-{VERSION}.jar"),
    ("gameBridgeJar", "minecraft-agent-tools", f"game-bridge-{VERSION}.jar"),
    ("agentToolsLicense", "minecraft-agent-tools", "LICENSE"),
    ("harnessApiJar", "minecraft-harness", f"minecraft-harness-forge-1.7.10-{VERSION}-dev.jar"),
]:
    args.append(f"-P{prop}={(DEST / repository / name).as_posix()}")
(DEST / "gradle-args.txt").write_text("\n".join(args) + "\n", encoding="utf-8")
print("Verified integration APIs; arguments: .work/integration-apis/gradle-args.txt")
