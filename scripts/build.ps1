$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw '未找到 JDK 21。请安装 JDK 21，并确保 java、javac、jpackage 已加入 PATH。'
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw '未找到 Maven。请安装 Maven 3.9+ 并加入 PATH。'
}

mvn clean package
if (Test-Path -LiteralPath "$projectRoot\dist\YukinoCounter") {
    Remove-Item -LiteralPath "$projectRoot\dist\YukinoCounter" -Recurse -Force
}
New-Item -ItemType Directory -Path "$projectRoot\dist" -Force | Out-Null
$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'YukinoCounter',
    '--app-version', '1.1.1',
    '--input', "$projectRoot\target",
    '--main-jar', 'YukinoCounter.jar',
    '--main-class', 'com.yukino.counter.Main',
    '--dest', "$projectRoot\dist",
    '--icon', "$projectRoot\assets\YukinoCounter.ico",
    '--java-options', '-Xms16m',
    '--java-options', '-Xmx128m',
    '--java-options', '-XX:+UseSerialGC'
)
jpackage $jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw 'jpackage 构建失败。'
}
Compress-Archive -Path "$projectRoot\dist\YukinoCounter" -DestinationPath "$projectRoot\dist\YukinoCounter-Windows-x64.zip" -Force
Write-Host "构建完成：$projectRoot\dist\YukinoCounter-Windows-x64.zip"
