# 데모 서버들의 기동 상태 + 에이전트 서버(8080) 스펙 등록 여부를 표로 보여준다.
#
# - 각 포트의 리스닝 여부로 "기동" 상태를 판정한다.
# - 에이전트 서버(8080)의 GET /api/v1/specs 를 (가능하면) 조회해 등록된 서비스명과 대조한다.
#   인증이 필요해 401 이면 등록 여부는 "?"로 표시(로그인 세션이 없어도 기동 상태는 확인 가능).
#
# 사용법 (PowerShell):
#   packages/demo 에서:  .\status.ps1

# 콘솔 한글 출력이 깨지지 않도록 UTF-8 로 고정
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$PORTS = [ordered]@{
    "demo-shop"    = 9101
    "demo-blog"    = 9102
    "demo-booking" = 9103
    "demo-hr"      = 9104
    "demo-bank"    = 9105
    "demo-ticket"  = 9106
}

function Test-PortListening([int]$port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

# 에이전트 서버 상태
$beUp = Test-PortListening 8080
Write-Host ""
Write-Host ("에이전트 서버(8080): " + $(if ($beUp) { "UP" } else { "DOWN" })) -ForegroundColor $(if ($beUp) { "Green" } else { "Red" })

# 등록된 서비스명 목록 조회(인증 없이 시도, 실패하면 null)
$registered = $null
if ($beUp) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/specs" -Method GET -TimeoutSec 5 -ErrorAction Stop
        $registered = @($resp | ForEach-Object { $_.name })
    } catch {
        $registered = $null  # 401 등: 등록 여부 확인 불가
    }
}

Write-Host ""
Write-Host ("{0,-14} {1,-6} {2,-8} {3}" -f "SERVER", "PORT", "기동", "스펙등록") -ForegroundColor Cyan
Write-Host ("-" * 44)

foreach ($name in $PORTS.Keys) {
    $port = $PORTS[$name]
    $up = Test-PortListening $port
    $upText = if ($up) { "UP" } else { "DOWN" }

    if ($null -eq $registered) {
        $regText = "?"
    } elseif ($registered -contains $name) {
        $regText = "O"
    } else {
        $regText = "X"
    }

    $color = if ($up) { "White" } else { "DarkGray" }
    Write-Host ("{0,-14} {1,-6} {2,-8} {3}" -f $name, $port, $upText, $regText) -ForegroundColor $color
}

Write-Host ""
if ($null -eq $registered -and $beUp) {
    Write-Host "스펙등록 열이 '?' 인 것은 /api/v1/specs 조회에 인증이 필요하기 때문입니다(기동 상태는 정확)." -ForegroundColor DarkGray
}
