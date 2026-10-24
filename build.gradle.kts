plugins { base }

tasks.named("check") {
    dependsOn(":core:check", ":platform-forge-1.7.10:check")
}
tasks.named("assemble") {
    dependsOn(":platform-forge-1.7.10:assemble")
}
tasks.named("clean") {
    dependsOn(":core:clean", ":platform-forge-1.7.10:clean")
}

tasks.wrapper {
    gradleVersion = "9.5.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
}
