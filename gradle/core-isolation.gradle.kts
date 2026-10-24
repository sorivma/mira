import java.nio.charset.StandardCharsets
import org.gradle.api.tasks.SourceSetContainer

val main = extensions.getByType<SourceSetContainer>()["main"]
// The foundation's production core is JDK-only. Rejecting every extra entry also
// catches unrecognized, renamed, shaded, transitive and local-file game jars.
val verifyCoreDependencies = tasks.register("verifyCoreDependencies") {
    group = "verification"
    description = "Enforce a JDK-only core production classpath."
    doLast {
        listOf("compileClasspath", "runtimeClasspath").forEach { name ->
            val configuration = configurations.getByName(name)
            check(configuration.allDependencies.isEmpty()) {
                "Core isolation: $name contains production dependencies: ${configuration.allDependencies}"
            }
            check(configuration.files.isEmpty()) { "Core isolation: unexpected artifacts in $name" }
        }
        check(main.compileClasspath.files.isEmpty()) { "Core isolation: extra production compile files" }
        val extraRuntime = main.runtimeClasspath.files - main.output.files
        check(extraRuntime.isEmpty()) { "Core isolation: extra production runtime files: $extraRuntime" }
        check(plugins.none { it.javaClass.name.startsWith("com.gtnewhorizons.") }) {
            "Core isolation: game plugin applied to core"
        }
    }
}

val forbidden = listOf(
    "mira.platform", "net.minecraft", "net.minecraftforge", "cpw.mods.fml",
    "com.gtnewhorizons", "gregtech", "gregapi", "gtPlusPlus", "tectech",
    "bartworks", "goodgenerator", "com.github.technus", "com.github.bartimaeusnek",
    "com.cleanroommc", "com.gtnewhorizon", "org.lwjgl", "io.netty",
    "codechicken", "com.enderio", "crazypants.enderio", "ic2", "mezz.jei",
    "com.github.GTNewHorizons"
)
fun verifyReferences(files: Collection<File>) {
        files.forEach { file ->
            var content = file.readText(if (file.extension == "class") StandardCharsets.ISO_8859_1 else StandardCharsets.UTF_8)
            content = Regex("\\\\u+([0-9a-fA-F]{4})").replace(content) { it.groupValues[1].toInt(16).toChar().toString() }
            content = content.replace('/', '.').replace(Regex("\\s*\\.\\s*"), ".")
            forbidden.forEach { prefix ->
                val pattern = if (file.extension == "class") Regex.escape(prefix) else "(?<![A-Za-z0-9_$])${Regex.escape(prefix)}(?=[.$;/<\\s]|$)"
                check(!Regex(pattern).containsMatchIn(content)) {
                    "Core isolation: forbidden reference $prefix in ${file.relativeTo(projectDir)}"
                }
            }
        }
}
val verifyCoreReferences = tasks.register("verifyCoreReferences") {
    group = "verification"
    description = "Reject platform references in core production sources."
    inputs.files(main.allJava)
    doLast { verifyReferences(main.allJava.files.filter { it.isFile }) }
}
val verifyCoreBytecode = tasks.register("verifyCoreBytecode") {
    group = "verification"
    description = "Reject platform references in compiled core production constants."
    dependsOn(tasks.named("classes"))
    inputs.files(main.output.classesDirs)
    doLast {
        verifyReferences(main.output.classesDirs.files.flatMap { dir ->
            if (dir.exists()) dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() else emptyList()
        })
    }
}
tasks.named("compileJava") { dependsOn(verifyCoreDependencies, verifyCoreReferences) }
tasks.named("check") { dependsOn(verifyCoreDependencies, verifyCoreReferences, verifyCoreBytecode) }
