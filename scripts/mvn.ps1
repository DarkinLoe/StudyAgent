param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MvnArgs
)

# 项目内本地 Maven：发行版放在 .m2/tools，依赖仓库在 .m2/repository。
# 说明：沙箱/代理环境下 PowerShell 的 TLS 不可用，Maven 发行版由 JVM 下载，
#       因此这里直接调用本地 mvn.cmd，而不是走 mvnw 的 PowerShell 下载逻辑。
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:MAVEN_USER_HOME = Join-Path $root '.m2'
$repoLocal = Join-Path $env:MAVEN_USER_HOME 'repository'
$mavenCmd = Join-Path $root '.m2\tools\apache-maven-3.9.16\bin\mvn.cmd'

if (-not (Test-Path $mavenCmd)) {
    $mavenCmd = Join-Path $root 'mvnw.cmd'
}

Push-Location $root
try {
    $argList = @("-Dmaven.repo.local=$repoLocal") + $MvnArgs
    & $mavenCmd @argList
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
