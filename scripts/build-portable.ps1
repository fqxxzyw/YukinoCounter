param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenCommand = "mvn",
    [string]$SevenZipCommand = "7zr",
    [string]$GccCommand = "gcc",
    [string]$WindresCommand = "windres"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workRoot = Join-Path $projectRoot ".tools\portable-package"
$imageRoot = Join-Path $workRoot "image"
$archivePath = Join-Path $workRoot "payload.7z"
$launcherPath = Join-Path $workRoot "launcher.exe"
$resourcePath = Join-Path $workRoot "launcher-resource.o"
$outputDir = Join-Path $projectRoot "dist"
$outputPath = Join-Path $outputDir "YukinoCounter-Portable-1.1.1.exe"

if (-not $JavaHome) {
    throw "请通过 JAVA_HOME 或 -JavaHome 指定 JDK 21。"
}
$env:JAVA_HOME = $JavaHome

New-Item -ItemType Directory -Force -Path $workRoot, $outputDir | Out-Null

Push-Location $projectRoot
try {
    $mavenPath = (Get-Command $MavenCommand -ErrorAction Stop).Source
    $sevenZipPath = (Get-Command $SevenZipCommand -ErrorAction Stop).Source
    $gccPath = (Get-Command $GccCommand -ErrorAction Stop).Source
    $windresPath = (Get-Command $WindresCommand -ErrorAction Stop).Source

    & $mavenPath -q clean test package
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败。" }

    if (Test-Path -LiteralPath $imageRoot) {
        Remove-Item -LiteralPath $imageRoot -Recurse -Force
    }

    $jpackage = Join-Path $JavaHome "bin\jpackage.exe"
    $jpackageArgs = @(
        "--type", "app-image",
        "--name", "YukinoCounter",
        "--app-version", "1.1.1",
        "--input", "target",
        "--main-jar", "YukinoCounter.jar",
        "--main-class", "com.yukino.counter.Main",
        "--dest", $imageRoot,
        "--icon", "assets\YukinoCounter.ico",
        "--java-options", "-Xms16m",
        "--java-options", "-Xmx128m",
        "--java-options", "-XX:+UseSerialGC"
    )
    & $jpackage $jpackageArgs
    if ($LASTEXITCODE -ne 0) { throw "jpackage 构建失败。" }

    if (Test-Path -LiteralPath $archivePath) {
        Remove-Item -LiteralPath $archivePath -Force
    }
    Push-Location $imageRoot
    try {
        & $sevenZipPath a -t7z -mx=9 $archivePath ".\YukinoCounter"
        if ($LASTEXITCODE -ne 0) { throw "7-Zip 压缩失败。" }
    } finally {
        Pop-Location
    }

    & $windresPath "packaging\portable-launcher.rc" -O coff -o $resourcePath
    if ($LASTEXITCODE -ne 0) { throw "资源编译失败。" }
    & $gccPath -Os -s -municode -mwindows -finput-charset=UTF-8 "packaging\portable-launcher.c" $resourcePath -o $launcherPath
    if ($LASTEXITCODE -ne 0) { throw "便携版启动器编译失败。" }

    $extractorPath = $sevenZipPath
    $launcherBytes = [IO.File]::ReadAllBytes($launcherPath)
    $extractorBytes = [IO.File]::ReadAllBytes($extractorPath)
    $archiveBytes = [IO.File]::ReadAllBytes($archivePath)
    $footer = New-Object byte[] 32
    [Text.Encoding]::ASCII.GetBytes("YUKINO_PORT_V1").CopyTo($footer, 0)
    [BitConverter]::GetBytes([UInt64]$extractorBytes.LongLength).CopyTo($footer, 16)
    [BitConverter]::GetBytes([UInt64]$archiveBytes.LongLength).CopyTo($footer, 24)

    $output = [IO.File]::Create($outputPath)
    try {
        $output.Write($launcherBytes, 0, $launcherBytes.Length)
        $output.Write($extractorBytes, 0, $extractorBytes.Length)
        $output.Write($archiveBytes, 0, $archiveBytes.Length)
        $output.Write($footer, 0, $footer.Length)
    } finally {
        $output.Dispose()
    }

    Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath
} finally {
    Pop-Location
}
