"""Stage only verified deliverables; keep fixtures and third-party APIs out."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--tag")
args = parser.parse_args()
match = re.search(r"^version=(.+)$", (ROOT / "gradle.properties").read_text(), re.MULTILINE)
version = match.group(1).strip()
semver = r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*)?"
if not re.fullmatch(semver, version):
    raise SystemExit("Release requires a SemVer version in gradle.properties")
if args.tag and args.tag != "v" + version:
    raise SystemExit(f"Tag {args.tag} does not match project version {version}")

artifacts = {
    f"mira-{version}.jar": "platform-forge-1.7.10",
    f"mira-{version}-dev.jar": "platform-forge-1.7.10",
    f"mira-agent-tools-1.7.10-{version}.jar": "integration-agent-tools-1.7.10",
    f"mira-agent-tools-1.7.10-{version}-dev.jar": "integration-agent-tools-1.7.10",
    f"mira-harness-1.7.10-{version}.jar": "integration-harness-1.7.10",
    f"mira-harness-1.7.10-{version}-dev.jar": "integration-harness-1.7.10",
    f"core-{version}.jar": "core",
}
bootstraps = {
    "platform-forge-1.7.10": "mira.platform.forge1710.MiraMod",
    "integration-agent-tools-1.7.10": "mira.integration.agenttools.forge.MiraAgentToolsMod",
    "integration-harness-1.7.10": "mira.integration.harness.MiraHarnessMod",
}
javap = str(Path(os.environ["JAVA_HOME"]) / "bin" / "javap") if os.environ.get("JAVA_HOME") else "javap"
for name, module in artifacts.items():
    path = ROOT / module / "build" / "libs" / name
    if not path.is_file():
        raise SystemExit(f"Missing release artifact: {path}; run all integration checks/builds first")
    with zipfile.ZipFile(path) as jar:
        if "META-INF/LICENSE-mira" not in jar.namelist():
            raise SystemExit(f"Missing Mira license: {name}")
        if any("/fixture/" in item for item in jar.namelist()):
            raise SystemExit(f"Test fixture in release artifact: {name}")
        if module != "core":
            metadata = json.loads(jar.read("mcmod.info"))
            if metadata[0]["version"] != version:
                raise SystemExit(f"Wrong embedded version: {name}")
    if module in bootstraps:
        bytecode = subprocess.check_output([javap, "-J-Duser.language=en", "-v", "-classpath", str(path), bootstraps[module]],
                                           text=True, encoding="utf-8", errors="replace")
        annotation = re.search(r"cpw\.mods\.fml\.common\.Mod\(\s*(.*?)\n\s*\)", bytecode, re.DOTALL)
        annotation_version = re.search(r'^\s*version="([^"]+)"\s*$', annotation.group(1), re.MULTILINE) if annotation else None
        if not annotation_version or annotation_version.group(1) != version:
            raise SystemExit(f"Wrong compiled @Mod version: {name}")

dest = ROOT / "build" / "release"
dest.mkdir(parents=True, exist_ok=True)
# Remove only files from this generated directory; never follow directories or links.
for stale in dest.iterdir():
    if stale.is_file() and not stale.is_symlink():
        stale.unlink()
for name, module in artifacts.items():
    output = f"mira-core-{version}.jar" if module == "core" else name
    shutil.copyfile(ROOT / module / "build" / "libs" / name, dest / output)
for source in ["LICENSE", "THIRD_PARTY_NOTICES.md"]:
    shutil.copyfile(ROOT / source, dest / source)
shutil.copyfile(ROOT / "docs" / "release-installation.md", dest / "INSTALLATION.md")
archive = dest / f"mira-{version}.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
    for path in sorted(dest.iterdir()):
        if path == archive or path.name == "SHA256SUMS":
            continue
        prefix = "developer/" if path.name.endswith("-dev.jar") or path.name.startswith("mira-core-") else ""
        entry = zipfile.ZipInfo(prefix + path.name, date_time=(1980, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o644 << 16
        bundle.writestr(entry, path.read_bytes())
checksums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}"
             for path in sorted(dest.iterdir()) if path.is_file() and path.name != "SHA256SUMS"]
(dest / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")
print(f"Prepared {len(checksums)} release assets for v{version} in {dest}")
