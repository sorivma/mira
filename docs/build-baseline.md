# Pinned build and compatibility baseline

Recorded on 2026-10-25. This is a compatibility target; see
[verification evidence](verification.md) for tests actually performed.

| Input | Selection |
| --- | --- |
| Gradle wrapper | 9.5.0, binary distribution |
| Distribution SHA-256 | `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746` |
| Forge development plugin | `com.gtnewhorizons.retrofuturagradle:2.0.6` |
| Gradle daemon / core compiler | Temurin JDK `25.0.4.1+1` |
| Core language, API, classfile | Java 8 via `javac --release 8`, classfile 52 |
| Platform compiler / minimal Forge runtime | Temurin JDK `8u504-b01` |
| RFG's decompiler JVM (needed with an empty cache) | Temurin JDK `17.0.17+10` |
| Minecraft | `1.7.10` |
| Forge SDK / universal | `1.7.10-10.13.4.1614-1.7.10` |
| MCP parameters | `stable-12`, Forge embedded field/method mappings |
| Full-pack runtime | Temurin Java `21.0.10+7` |
| GTNH target | `2.9.0-RC-2`, modern Java 17–26 distribution |

The [RFG 2.0.6 source](https://github.com/GTNewHorizons/RetroFuturaGradle/tree/2.0.6)
uses Java 25 for the plugin and its example wrapper selects Gradle 9.5.0.
Its README's older Gradle range is stale: Gradle 8.8 fails on classfile major
69 in the published plugin. The platform-subproject configuration and actual
build are the compatibility checks for the selected combination. RFG itself
pins the Forge version above for Minecraft 1.7.10; the build explicitly pins
Minecraft, mapping channel/version, and Java selections. No extra mod library
is required. Plugin-internal tools and game libraries come from pinned RFG and
the Forge/Minecraft manifests; they are not declared as core dependencies.

The [official GTNH history](https://www.gtnewhorizons.com/version-history/)
still lists RC-2 as the newest 2.9.0-line download. No final 2.9.0 distribution
has been verified. The following client archive was available locally:

- [Client: GT_New_Horizons_2.9.0-RC-2_Java_17-26.zip](https://downloads.gtnewhorizons.com/Multi_mc_downloads/betas/GT_New_Horizons_2.9.0-RC-2_Java_17-26.zip)
- Size: `729259090` bytes.
- SHA-256: `0c7da79f4ac1982c8a7dbfdcc1f1af6cf96ac5cb345a1c3387fb090d974b0a8d`.
- [Matching server archive](https://downloads.gtnewhorizons.com/ServerPacks/betas/GT_New_Horizons_2.9.0-RC-2_Server_Java_17-26.zip).
- Server size: `552970952` bytes; SHA-256:
  `e005a0d050ec06073b3b0d11b149b5ab9355cacf4b8c4b837c50b8a89946dbf9`.

The matching installed client manifest identifies Minecraft 1.7.10, Forge
10.13.4.1614, LWJGL3 3.4.3, and LWJGL3ify 3.0.37. Its Forge patch still selects
the official 1614 universal jar (SHA-1
`25fd97f72beca728112256938e03e8105b1b78cc`); the modern-Java launch adds
LWJGL3ify early classpath/launch patches. The plain 1614 SDK is the compile
baseline; the patched pack is the smoke baseline. Java 8 bytecode alone does
not prove compatibility with the separate Java 8 pack distribution.
