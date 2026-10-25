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

val generatedVersionDirectory = layout.buildDirectory.dir("generated/sources/miraVersion")
val generateBuildVersion by tasks.registering {
    inputs.property("version", project.version)
    outputs.dir(generatedVersionDirectory)
    doLast {
        val releaseVersion = project.version.toString()
        require(Regex("[0-9A-Za-z.+-]+").matches(releaseVersion)) { "Unsafe Java version literal" }
        val output = generatedVersionDirectory.get().file("mira/platform/forge1710/BuildVersion.java").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            package mira.platform.forge1710;

            /** Generated from Gradle's project version; never edit this file. */
            public final class BuildVersion {
                public static final String VERSION = "$releaseVersion";
                private BuildVersion() {}
            }
        """.trimIndent() + "\n")
    }
}
sourceSets.main { java.srcDir(generateBuildVersion) }

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
repositories {
    mavenCentral()
    maven("https://nexus.gtnewhorizons.com/repository/public/") {
        content { includeGroup("com.github.GTNewHorizons") }
    }
}

// Embed a Java 8-compatible Markdown stack in the platform, never in core.
val markdownLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations.implementation.get().extendsFrom(markdownLibraries)

dependencies {
    markdownLibraries("org.commonmark:commonmark:0.21.0")
    markdownLibraries("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    markdownLibraries("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    markdownLibraries("org.commonmark:commonmark-ext-task-list-items:0.21.0")
    markdownLibraries("org.commonmark:commonmark-ext-autolink:0.21.0")
    testImplementation("junit:junit:4.13.2")
    compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.155-GTNH:dev") { isTransitive = false }
    compileOnly("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev") { isTransitive = false }
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
fun Jar.embedMarkdown() {
    from({ markdownLibraries.map { zipTree(it) } }) {
        include("org/commonmark/**", "org/nibor/autolink/**")
    }
    from("third-party") { into("META-INF/licenses/mira") }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar {
    archiveBaseName.set("mira")
    // Embed classes BEFORE RFG's reobfJar transformation.
    embedCore(coreOutput)
    embedMarkdown()
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
    if (providers.gradleProperty("miraIntegrations").map { it.toBoolean() }.getOrElse(false)) {
        when (name) {
            "runClient" -> dependsOn(":prepareLocalIntegrationClient")
            "runObfClient" -> dependsOn(":prepareLocalObfuscatedIntegrationClient")
        }
    }
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
            val markdownEntries = markdownLibraries.flatMap { dependency ->
                ZipFile(dependency).use { library ->
                    library.entries().asSequence().filterNot { it.isDirectory }.map { it.name }
                        .filter { it.startsWith("org/commonmark/") || it.startsWith("org/nibor/autolink/") }.toList()
                }
            }.toSet()
            check(markdownEntries.all { it in entries }) { "Missing embedded Markdown classes/resources" }
            check("META-INF/licenses/mira/commonmark.txt" in entries && "META-INF/licenses/mira/autolink.txt" in entries) {
                "Missing Markdown library licenses"
            }
            coreOutput.files.filter { it.isDirectory }.forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val path = file.relativeTo(dir).invariantSeparatorsPath
                    check(path in entries) { "Missing core output: $path" }
                }
            }
            // Own content plus the exact resolved Markdown stack; reject unrelated libraries/tests.
            val unexpected = entries.filterNot {
                it.startsWith("mira/") || it.startsWith("assets/mira/") ||
                    it == "mcmod.info" || it == "META-INF/MANIFEST.MF" || it == "META-INF/LICENSE-mira" || it in markdownEntries ||
                    it == "META-INF/licenses/mira/commonmark.txt" || it == "META-INF/licenses/mira/autolink.txt"
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
    embedMarkdown()
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
