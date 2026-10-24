import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import java.util.zip.ZipFile
import java.net.URLClassLoader

plugins {
    `java-library`
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}
minecraft {
    mcVersion.set("1.7.10") // RFG 2.0.6 pins Forge 10.13.4.1614 for this version.
    mcpMappingChannel.set("stable")
    mcpMappingVersion.set("12")
    useForgeEmbeddedMappings.set(true)
    javaCompatibilityVersion.set(8)
    jvmLanguageVersion.set(JavaLanguageVersion.of(8))
    username.set("MiraDeveloper")
    // Core is embedded; do not turn the project into an external runtime mod.
    groupsToExcludeFromAutoReobfMapping.add("mira")
}
dependencies {
    implementation(project(":core")) {
        // RFG's obfuscated-run resolver needs the jar, not Gradle 9's directory variants.
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
    }
}

evaluationDependsOn(":core")
val coreSourceSets = project(":core").extensions.getByType<SourceSetContainer>()
val coreOutput = coreSourceSets["main"].output
val fixtureOutput = coreSourceSets["packagingFixture"].output

fun Jar.embedCore(output: Any) { from(output) }

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar {
    archiveBaseName.set("mira")
    // Embed classes BEFORE RFG's reobfJar transformation.
    embedCore(coreOutput)
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}

// Keep RFG's standard run/ and run/obfuscated/ instance directories.
tasks.withType<RunMinecraftTask>().configureEach {
    minHeapSize = "256M"
    maxHeapSize = "2G"
}

val finalJar = tasks.named<ReobfuscatedJar>("reobfJar")
val verifyModJar by tasks.registering {
    group = "verification"
    description = "Inspect the final installable mod, including all current core output."
    dependsOn(finalJar)
    inputs.file(finalJar.flatMap { it.archiveFile })
    inputs.files(coreOutput)
    doLast {
        ZipFile(finalJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
            check("mcmod.info" in entries) { "Missing mod metadata" }
            check("mira/platform/forge1710/MiraMod.class" in entries) { "Missing bootstrap" }
            val metadata = zip.getInputStream(zip.getEntry("mcmod.info")).bufferedReader().readText()
            check(metadata.contains("\"modid\": \"mira\"")) { "Wrong mod id" }
            check(metadata.contains("\"version\": \"${project.version}\"")) { "Unexpanded mod version" }
            coreOutput.files.filter { it.isDirectory }.forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val path = file.relativeTo(dir).invariantSeparatorsPath
                    check(path in entries) { "Missing core output: $path" }
                }
            }
            // This foundation only owns mira/** plus mod metadata and the manifest.
            val unexpected = entries.filterNot {
                it.startsWith("mira/") || it == "mcmod.info" || it == "META-INF/MANIFEST.MF"
            }
            check(unexpected.isEmpty()) { "Unexpected bundled runtime/test content: $unexpected" }
            check(entries.none { it.endsWith(".jar") }) { "Nested jar in mod" }
            check(entries.none { it.startsWith("mira/core/fixture/") }) { "Fixture leaked into production mod" }
            logger.lifecycle("Verified installable mod: {} ({} entries)", finalJar.get().archiveFile.get(), entries.size)
        }
    }
}

val packagingFixtureJar by tasks.registering(Jar::class) {
    archiveBaseName.set("mira-packaging-fixture")
    archiveClassifier.set("dev")
    destinationDirectory.set(layout.buildDirectory.dir("fixtures"))
    from(sourceSets.main.get().output)
    // Use the production embedding mechanism with a real, independently compiled class.
    embedCore(files(coreOutput, fixtureOutput))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
val reobfPackagingFixtureJar = tasks.named<ReobfuscatedJar>("reobfPackagingFixtureJar")
val verifyPackagingFixture by tasks.registering {
    group = "verification"
    description = "Prove a core class survives embedding and reobfuscation; fixture is not installable."
    dependsOn(reobfPackagingFixtureJar)
    inputs.file(reobfPackagingFixtureJar.flatMap { it.archiveFile })
    doLast {
        val jar = reobfPackagingFixtureJar.get().archiveFile.get().asFile
        ZipFile(jar).use { zip ->
            check(zip.getEntry("mira/core/fixture/EmbeddingProbe.class") != null) { "Core fixture missing after reobfuscation" }
        }
        URLClassLoader(arrayOf(jar.toURI().toURL()), null).use { loader ->
            loader.loadClass("mira.core.fixture.EmbeddingProbe").getDeclaredConstructor().newInstance()
        }
        logger.lifecycle("Core embedding fixture survived reobfuscation and loaded from {}", jar)
    }
}
tasks.check { dependsOn(verifyModJar, verifyPackagingFixture) }
