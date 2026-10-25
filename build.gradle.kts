plugins { base }

subprojects {
    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE-mira" }
        }
    }
}

val integrationsEnabled = providers.gradleProperty("miraIntegrations").map { it.toBoolean() }.getOrElse(false)

tasks.named("check") {
    dependsOn(":core:check", ":platform-forge-1.7.10:check")
}
tasks.named("assemble") {
    dependsOn(":platform-forge-1.7.10:assemble")
}
tasks.named("clean") {
    dependsOn(":core:clean", ":platform-forge-1.7.10:clean")
    if (integrationsEnabled) dependsOn(":integration-agent-tools-1.7.10:clean", ":integration-harness-1.7.10:clean")
}

tasks.register("assembleIntegrations") {
    group = "build"
    description = "Assemble Mira and both optional integration jars (requires neighboring API jars)."
    if (integrationsEnabled) dependsOn(":platform-forge-1.7.10:assemble", ":integration-agent-tools-1.7.10:assemble",
        ":integration-harness-1.7.10:assemble")
    else doFirst { throw GradleException("Enable optional modules with -PmiraIntegrations=true; see docs/integrations.md") }
}
tasks.register("checkIntegrations") {
    group = "verification"
    description = "Run the optional integrations' checks; ordinary check remains standalone."
    if (integrationsEnabled) dependsOn(":integration-agent-tools-1.7.10:check", ":integration-harness-1.7.10:check")
    else doFirst { throw GradleException("Enable optional modules with -PmiraIntegrations=true; see docs/integrations.md") }
}

if (integrationsEnabled) {
    fun localIntegrationClient(taskName: String, obfuscated: Boolean) {
        val classifier = if (obfuscated) "" else "-dev"
        val jarTask = if (obfuscated) "reobfJar" else "jar"
        val agentToolsJar = file("integration-agent-tools-1.7.10/build/libs/mira-agent-tools-1.7.10-${project.version}$classifier.jar")
        val harnessJar = file("integration-harness-1.7.10/build/libs/mira-harness-1.7.10-${project.version}$classifier.jar")
        tasks.register<Copy>(taskName) {
            group = "development"
            description = "Stage Mira addons and the MCP provider for the ${if (obfuscated) "obfuscated" else "development"} local client."
            dependsOn(":integration-agent-tools-1.7.10:$jarTask", ":integration-harness-1.7.10:$jarTask")
            into(file(if (obfuscated) "platform-forge-1.7.10/run/obfuscated" else "platform-forge-1.7.10/run"))
            from(files(agentToolsJar, harnessJar)) { into("mods") }
            from(agentToolsJar) { into("config/minecraftagenttools/plugins") }
            doFirst {
                require(agentToolsJar.isFile && harnessJar.isFile) { "Expected addon JARs were not produced; see docs/integrations.md" }
            }
        }
    }
    localIntegrationClient("prepareLocalIntegrationClient", false)
    localIntegrationClient("prepareLocalObfuscatedIntegrationClient", true)
}

tasks.wrapper {
    gradleVersion = "9.5.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
}
