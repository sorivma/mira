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

val harnessApi = files(rootProject.file(providers.gradleProperty("harnessApiJar")
    .getOrElse("../minecraft-harness/forge-1.7.10/build/libs/minecraft-harness-forge-1.7.10-0.1.0-dev.jar")))
dependencies {
    compileOnly(project(":core"))
    compileOnly(project(":platform-forge-1.7.10")) { isTransitive = false }
    compileOnly(harnessApi)
    testImplementation(harnessApi)
    testImplementation("com.google.code.gson:gson:2.2.4")
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar {
    archiveBaseName.set("mira-harness-1.7.10")
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}

val finalJar = tasks.named<ReobfuscatedJar>("reobfJar")
val verifyIntegrationJar by tasks.registering {
    group = "verification"
    description = "Inspect the optional Harness artifact after reobfuscation."
    dependsOn(finalJar)
    inputs.file(finalJar.flatMap { it.archiveFile })
    doLast {
        ZipFile(finalJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
            check("mira/integration/harness/MiraHarnessMod.class" in entries)
            check("mira/integration/harness/client/TasksWidget.class" in entries)
            check(entries.all {
                it.startsWith("mira/integration/harness/") || it == "mcmod.info" || it == "META-INF/MANIFEST.MF" || it == "META-INF/LICENSE-mira"
            }) { "Harness addon must not bundle Mira, Harness, MCP, game or test classes" }
            val metadata = zip.getEntry("mcmod.info")
            check(metadata != null && zip.getInputStream(metadata).bufferedReader().readText().contains("\"version\": \"${project.version}\""))
        }
    }
}
tasks.check { dependsOn(verifyIntegrationJar) }
