# packages/demo 하위 데모 서버들을 일괄 중지한다.
#
# - PORTS 매핑의 각 포트를 리스닝 중인 프로세스를 종료한다(데모 서버 = 해당 포트의 java 프로세스).
#   start-all.ps1 이 Start-Process 로 띄운 독립 프로세스는 포트로 찾아 종료한다.
# - 과거 버전(Start-Job)으로 띄운 잔여 Job(demo:*)이 있으면 함께 정리한다(하위호환).
#
# 사용법 (PowerShell):
#   packages/demo 에서:  .\stop-all.ps1
#   특정 서버만:          .\stop-all.ps1 -Only demo-blog

param(
    [string[]]$Only = @()
)

# 콘솔 한글 출력이 깨지지 않도록 UTF-8 로 고정
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "SilentlyContinue"

$PORTS = @{
    "demo-shop"    = 9101
    "demo-blog"    = 9102
    "demo-booking" = 9103
    "demo-hr"      = 9104
    "demo-bank"    = 9105
    "demo-ticket"  = 9106
}

$names = $PORTS.Keys
if ($Only.Count -gt 0) {
    $names = $names | Where-Object { $Only -contains $_ }
}

foreach ($name in $names) {
    $port = $PORTS[$name]
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "[skip] $name : 포트 $port 리스닝 없음(이미 중지됨)." -ForegroundColor DarkGray
        continue
    }
    foreach ($c in $conns) {
        $procId = $c.OwningProcess
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "[stop] $name : 포트 $port (PID $procId) 종료." -ForegroundColor Green
    }
}

# start-all.ps1 이 만든 백그라운드 Job 정리
$jobs = Get-Job -Name "demo:*" -ErrorAction SilentlyContinue
if ($Only.Count -gt 0) {
    $jobs = $jobs | Where-Object { $Only -contains ($_.Name -replace '^demo:', '') }
}
if ($jobs) {
    $jobs | Stop-Job -ErrorAction SilentlyContinue
    $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
    Write-Host "[cleanup] 백그라운드 Job 정리 완료." -ForegroundColor Cyan
}
