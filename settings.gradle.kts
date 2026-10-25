pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            content { includeGroupByRegex("com\\.gtnewhorizons(\\..*)?") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "mira"
include("core", "platform-forge-1.7.10")

// Unqualified build/check select matching tasks across included projects. Opt in explicitly
// so standalone Mira commands and IDE imports do not require the neighboring API jars.
if (providers.gradleProperty("miraIntegrations").map { it.toBoolean() }.getOrElse(false)) {
    include("integration-agent-tools-1.7.10", "integration-harness-1.7.10")
}
