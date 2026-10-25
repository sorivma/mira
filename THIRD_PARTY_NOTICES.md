# Third-party notices

Mira's own code is MIT licensed; see [LICENSE](LICENSE).

| Component | Version | Use | License / notice |
| --- | --- | --- | --- |
| commonmark-java and extensions | 0.21.0 | Embedded Markdown parser | BSD 2-Clause; [notice](platform-forge-1.7.10/third-party/commonmark.txt) |
| autolink-java | 0.10.0 (via commonmark) | Embedded URL recognition | MIT; [notice](platform-forge-1.7.10/third-party/autolink.txt) |
| Minecraft Agent Tools GameBridge/protocol | 0.1.0 | Embedded only in the optional MCP addon | MIT; its license is included in that addon's `META-INF/licenses/mira-agent-tools/LICENSE` |

The base jar retains the Markdown notices under `META-INF/licenses/mira/`.
All Mira jars include `META-INF/LICENSE-mira`.

Minecraft, Forge, NEI, CodeChickenCore, Harness, the MCP SPI/runtime, and GTNH
are not redistributed by the release bundle. They are separately installed or
compile-only dependencies with their own licenses. Gradle and its wrapper,
RetroFuturaGradle, JUnit, and development tooling retain their respective licenses.
