# packages/demo 하위의 모든 demo-* 데모 서버를 백그라운드로 일괄 기동한다.
#
# - demo-* 폴더를 자동 탐색하므로 새 데모 서버가 추가돼도 스크립트 수정 불필요.
# - 각 서버는 별도 백그라운드 프로세스로 bootRun 되고, 로그는 logs/{name}.log 에 남는다.
# - 이미 해당 포트가 리스닝 중이면 스킵한다(중복 기동 방지).
# - 데모 서버는 기동 시 스펙을 에이전트 서버(8080)에 자동 등록하므로, 8080 이 떠 있어야
#   등록이 성공한다. 8080 이 감지되지 않으면 경고만 하고 계속 진행한다(등록은 재기동으로 복구 가능).
#
# 사용법 (PowerShell):
#   packages/demo 에서:  .\start-all.ps1
#   특정 서버만:          .\start-all.ps1 -Only demo-blog,demo-shop
#
# 참고: 포트는 각 서버의 폴더명 기준 아래 PORTS 매핑을 따른다(신규 서버는 매핑에 추가).

param(
    # 기동할 서버 이름 목록(폴더명). 비우면 demo-* 전체.
    [string[]]$Only = @()
)

# 콘솔 한글 출력이 깨지지 않도록 UTF-8 로 고정
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"

# 폴더명 -> 포트 매핑 (신규 데모 서버 추가 시 여기에 한 줄 추가)
$PORTS = @{
    "demo-shop"    = 9101
    "demo-blog"    = 9102
    "demo-booking" = 9103
    "demo-hr"      = 9104
    "demo-bank"    = 9105
    "demo-ticket"  = 9106
}

$demoRoot = $PSScriptRoot
$logDir = Join-Path $demoRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# 로컬 빌드 환경(비ASCII 홈 경로 우회). 이미 설정돼 있으면 유지.
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = "C:\gradle-home" }
$asciiBuildDir = "C:\tf-build"

function Test-PortListening([int]$port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

# 에이전트 서버(8080) 감지 — 안 떠 있으면 스펙 등록이 실패하므로 경고.
if (-not (Test-PortListening 8080)) {
    Write-Host "[warn] 에이전트 서버(8080)가 감지되지 않습니다. 데모 서버는 뜨지만 스펙 등록이 실패할 수 있어요." -ForegroundColor Yellow
    Write-Host "[warn] 8080을 먼저 띄운 뒤 이 스크립트를 실행하거나, 등록 실패 시 해당 데모 서버만 재기동하세요." -ForegroundColor Yellow
}

# 대상 서버 폴더 결정
$targets = Get-ChildItem -Path $demoRoot -Directory | Where-Object { $_.Name -like "demo-*" }
if ($Only.Count -gt 0) {
    $targets = $targets | Where-Object { $Only -contains $_.Name }
}

if (-not $targets) {
    Write-Host "기동할 demo-* 서버가 없습니다." -ForegroundColor Red
    exit 1
}

foreach ($dir in $targets) {
    $name = $dir.Name
    $port = $PORTS[$name]

    if (-not $port) {
        Write-Host "[skip] $name : PORTS 매핑에 포트가 없습니다. 스크립트 상단 PORTS 에 추가하세요." -ForegroundColor Yellow
        continue
    }

    if (Test-PortListening $port) {
        Write-Host "[skip] $name : 포트 $port 가 이미 사용 중(이미 기동됨)." -ForegroundColor DarkGray
        continue
    }

    $logFile = Join-Path $logDir "$name.log"
    Write-Host "[start] $name (port $port) -> $logFile" -ForegroundColor Green

    # 독립 백그라운드 프로세스로 bootRun (Start-Process). 이 스크립트를 실행한 터미널을
    # 닫아도 서버는 계속 실행된다(Start-Job 과 달리 세션에 종속되지 않는다).
    # gradlew.bat 실행 전 GRADLE_USER_HOME 을 설정하고, stdout/stderr 를 로그 파일로 리다이렉트한다.
    # -PasciiBuildDir 는 비ASCII 홈 경로 우회. -WindowStyle Hidden 으로 창을 띄우지 않는다.
    $inner = "`$env:GRADLE_USER_HOME='$($env:GRADLE_USER_HOME)'; " +
             ".\gradlew.bat bootRun `"-PasciiBuildDir=$asciiBuildDir`" --console=plain " +
             "*> `"$logFile`""
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $inner) `
        -WorkingDirectory $dir.FullName `
        -WindowStyle Hidden | Out-Null
}

Write-Host ""
Write-Host "기동 요청 완료. 각 서버는 독립 백그라운드 프로세스에서 빌드/기동 중입니다(수십 초 소요)." -ForegroundColor Cyan
Write-Host "이 터미널을 닫아도 서버는 계속 실행됩니다. 중지하려면 .\stop-all.ps1 을 사용하세요." -ForegroundColor Cyan
Write-Host "상태 확인:  .\status.ps1"
Write-Host "로그 보기:  Get-Content .\logs\demo-blog.log -Tail 30 -Wait"
Write-Host "중지:       .\stop-all.ps1"
