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
