import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import java.util.zip.ZipFile

plugins {
    `java-library`
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }
minecraft {
    mcVersion.set("1.7.10")
    mcpMappingChannel.set("stable")
    mcpMappingVersion.set("12")
    useForgeEmbeddedMappings.set(true)
    javaCompatibilityVersion.set(8)
    jvmLanguageVersion.set(JavaLanguageVersion.of(8))
    groupsToExcludeFromAutoReobfMapping.add("mira")
}
repositories { mavenCentral() }

val agentToolsRoot = rootProject.file(providers.gradleProperty("agentToolsRoot").getOrElse("../minecraft-agent-tools"))
fun apiJar(property: String, relative: String) = files(
    providers.gradleProperty(property).map { rootProject.file(it) }.getOrElse(agentToolsRoot.resolve(relative))
)
val mcpApi = apiJar("mcpApiJar", "core/mcp-api/build/libs/mcp-api-0.1.0.jar")
val bridgeProtocol = apiJar("bridgeProtocolJar", "core/bridge-protocol/build/libs/bridge-protocol-0.1.0.jar")
val gameBridge = apiJar("gameBridgeJar", "core/game-bridge/build/libs/game-bridge-0.1.0.jar")
val agentToolsLicense = providers.gradleProperty("agentToolsLicense").map { rootProject.file(it) }
    .getOrElse(agentToolsRoot.resolve("LICENSE"))

dependencies {
    compileOnly(project(":core"))
    compileOnly(project(":platform-forge-1.7.10")) { isTransitive = false }
    compileOnly(mcpApi)
    compileOnly(bridgeProtocol)
    compileOnly(gameBridge)
    testImplementation(project(":core"))
    testImplementation(mcpApi)
    testImplementation(bridgeProtocol)
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar {
    archiveBaseName.set("mira-agent-tools-1.7.10")
    // The same artifact is a Forge mod and an MCP provider. Never embed Mira or the MCP SPI.
    from({ (bridgeProtocol + gameBridge).map { zipTree(it) } }) {
        include("dev/minecraftagenttools/protocol/**", "dev/minecraftagenttools/gamebridge/**")
    }
    from(agentToolsLicense) { into("META-INF/licenses/mira-agent-tools"); rename { "LICENSE" } }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}

val finalJar = tasks.named<ReobfuscatedJar>("reobfJar")
val verifyIntegrationJar by tasks.registering {
    group = "verification"
    description = "Inspect the optional MCP artifact and service descriptor after reobfuscation."
    dependsOn(finalJar)
    inputs.file(finalJar.flatMap { it.archiveFile })
    doLast {
        ZipFile(finalJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
            check("mira/integration/agenttools/forge/MiraAgentToolsMod.class" in entries)
            check("dev/minecraftagenttools/gamebridge/GameBridge.class" in entries)
            check("dev/minecraftagenttools/protocol/data/Data.class" in entries)
            check("META-INF/licenses/mira-agent-tools/LICENSE" in entries)
            val service = zip.getEntry("META-INF/services/dev.minecraftagenttools.api.ToolProvider")
            check(service != null) { "Missing MCP provider service descriptor" }
            check(zip.getInputStream(service).bufferedReader().readText().trim() ==
                "mira.integration.agenttools.provider.MiraToolProvider")
            check(entries.all {
                it.startsWith("mira/integration/agenttools/") || it.startsWith("dev/minecraftagenttools/protocol/") ||
                    it.startsWith("dev/minecraftagenttools/gamebridge/") || it.startsWith("META-INF/licenses/mira-agent-tools/") ||
                    it == "META-INF/services/dev.minecraftagenttools.api.ToolProvider" || it == "mcmod.info" || it == "META-INF/MANIFEST.MF" || it == "META-INF/LICENSE-mira"
            }) { "Unexpected bundled Mira, MCP SPI/runtime, Harness, game or test classes" }
            val metadata = zip.getEntry("mcmod.info")
            check(metadata != null && zip.getInputStream(metadata).bufferedReader().readText().contains("\"version\": \"${project.version}\""))
        }
    }
}
tasks.check { dependsOn(verifyIntegrationJar) }
