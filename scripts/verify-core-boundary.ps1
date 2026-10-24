param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $repo '.work/boundary-controls'
$probe = Join-Path $repo 'core/src/main/java/mira/core/BoundaryControl.java'
$init = Join-Path $work 'control.init.gradle'
New-Item -ItemType Directory -Force -Path $work | Out-Null
if (Test-Path -LiteralPath $probe) { throw "Refusing to replace existing $probe" }
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Invoke-Control([string]$Name, [string[]]$GradleArgs, [bool]$MustFail) {
    $log = Join-Path $work "$Name.log"
    # cmd keeps native stderr as ordinary log text on Windows PowerShell 5.1.
    & $env:ComSpec /d /c "gradlew.bat $($GradleArgs -join ' ') --console=plain > `"$log`" 2>&1"
    $code = $LASTEXITCODE
    $content = [IO.File]::ReadAllText($log)
    if ($MustFail) {
        if ($code -eq 0 -or $content -notmatch 'Core isolation:') {
            throw "Control $Name did not fail with a core-isolation diagnostic; inspect $log"
        }
    } elseif ($code -ne 0) { throw "Clean check failed; inspect $log" }
    Write-Host "$Name`: exit $code (expected)"
}

Push-Location $repo
try {
    foreach ($prefix in @('mira.platform.forge1710.MiraMod', 'net.minecraft.world.World',
        'cpw.mods.fml.common.Mod', 'com.gtnewhorizons.example.Type',
        'com.cleanroommc.modularui.api.Type', 'org.lwjgl.opengl.GL11', 'io.netty.buffer.ByteBuf')) {
        try {
            [IO.File]::WriteAllText($probe, "package mira.core; final class BoundaryControl { $prefix forbidden; }", $utf8)
            Invoke-Control "reference-$($prefix.Replace('.', '-'))" @(':core:verifyCoreReferences') $true
        } finally { Remove-Item -LiteralPath $probe -ErrorAction SilentlyContinue }
    }
    try {
        [IO.File]::WriteAllText($probe, 'package mira.core; final class BoundaryControl { net.\u006dinecraft.world.World forbidden; }', $utf8)
        Invoke-Control 'unicode-reference' @(':core:verifyCoreReferences') $true
    } finally { Remove-Item -LiteralPath $probe -ErrorAction SilentlyContinue }
    try {
        # Constant folding creates a forbidden name only in the classfile.
        [IO.File]::WriteAllText($probe, 'package mira.core; final class BoundaryControl { static final String TYPE = "net." + "minecraft.world.World"; }', $utf8)
        Invoke-Control 'compiled-reference' @(':core:verifyCoreBytecode') $true
    } finally { Remove-Item -LiteralPath $probe -ErrorAction SilentlyContinue }

    $mutations = @{
        'module-dependency' = 'p.dependencies.add("implementation", "io.netty:netty-all:4.1.0.Final")'
        'project-dependency' = 'p.dependencies.add("implementation", p.project(":platform-forge-1.7.10"))'
        'file-dependency' = 'p.dependencies.add("runtimeOnly", p.files("renamed-game.jar"))'
        'inherited-dependency' = 'def hidden = p.configurations.create("hiddenGame"); p.dependencies.add(hidden.name, p.files("renamed-game.jar")); p.configurations.compileClasspath.extendsFrom(hidden)'
        'direct-classpath-file' = 'p.sourceSets.main.compileClasspath += p.files("renamed-game.jar")'
    }
    foreach ($entry in $mutations.GetEnumerator()) {
        [IO.File]::WriteAllText($init, "gradle.beforeProject { p -> if (p.path == ':core') { p.afterEvaluate { $($entry.Value) } } }", $utf8)
        Invoke-Control $entry.Key @('-I', '".work/boundary-controls/control.init.gradle"', ':core:verifyCoreDependencies') $true
    }
    [IO.File]::WriteAllText($init, 'gradle.beforeProject { p -> if (p.path == ":core") { p.afterEvaluate { p.dependencies.add("testImplementation", "junit:junit:4.13.2") } } }', $utf8)
    Invoke-Control 'allowed-test-dependency' @('-I', '".work/boundary-controls/control.init.gradle"', ':core:clean', ':core:check') $false
    Invoke-Control 'clean-core' @(':core:clean', ':core:check') $false
} finally {
    Remove-Item -LiteralPath $probe -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $init -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $repo 'core/build/classes/java/main/mira/core/BoundaryControl.class') -ErrorAction SilentlyContinue
    Pop-Location
}
