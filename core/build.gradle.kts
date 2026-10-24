plugins { `java-library` }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Deliberately outside main/test: used only to prove class embedding in a jar.
sourceSets.create("packagingFixture")

apply(from = rootProject.file("gradle/core-isolation.gradle.kts"))
