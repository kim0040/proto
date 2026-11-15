# 일정 관리 앱을 Windows에 한 번에 설치(또는 최신 버전으로 업데이트)하는 스크립트.
# PowerShell 창에 아래 한 줄을 붙여 넣으면 된다:
#   irm https://raw.githubusercontent.com/kim0040/proto/main/scripts/install-windows.ps1 | iex
# 하는 일: 최신 릴리즈의 Windows zip을 받아 내 사용자 폴더(%LOCALAPPDATA%\Programs\ScheduleApp)에 풀고,
# 시작 메뉴와 바탕 화면에 "일정 관리" 바로 가기를 만든 뒤 실행한다. 관리자 권한은 필요 없다.
# 브라우저가 아닌 PowerShell로 받으므로 "Windows의 PC 보호"(SmartScreen) 창이 뜨지 않는다.
# 데이터(%APPDATA%\ScheduleApp)는 건드리지 않는다.
# 지우기: $env:SCHEDULEAPP_UNINSTALL = '1' 을 먼저 실행한 뒤 같은 한 줄을 실행한다.

# 사용자의 PowerShell 창 설정과 변수를 바꾸지 않도록 전체를 한 블록 안에서 실행한다
& {
    $ErrorActionPreference = 'Stop'
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    $Repo = 'kim0040/proto'
    $AppName = 'ScheduleApp'
    $ShortcutName = '일정 관리'
    $InstallDir = if ($env:SCHEDULEAPP_INSTALL_DIR) { $env:SCHEDULEAPP_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA "Programs\$AppName" }
    # 릴리즈 파일 이름에는 버전이 없어서 이 주소가 늘 최신 버전을 가리킨다
    $ZipUrl = if ($env:SCHEDULEAPP_ZIP_URL) { $env:SCHEDULEAPP_ZIP_URL } else { "https://github.com/$Repo/releases/latest/download/$AppName-windows-x64.zip" }
    $StartMenuLink = Join-Path ([Environment]::GetFolderPath('Programs')) "$ShortcutName.lnk"
    $DesktopLink = Join-Path ([Environment]::GetFolderPath('Desktop')) "$ShortcutName.lnk"

    function Say([string]$Text) { Write-Host $Text -ForegroundColor Cyan }
    function Fail([string]$Text) { throw $Text }

    # 켜져 있는 앱을 닫는다 (일정은 바꿀 때마다 저장되므로 잃는 것은 없다)
    function Stop-RunningApp {
        $running = Get-Process -Name $AppName -ErrorAction SilentlyContinue
        if (-not $running) { return }
        Say '켜져 있는 일정 관리 앱을 닫을게요...'
        $running | ForEach-Object { [void]$_.CloseMainWindow() }
        for ($i = 0; $i -lt 10; $i++) {
            Start-Sleep -Seconds 1
            if (-not (Get-Process -Name $AppName -ErrorAction SilentlyContinue)) { return }
        }
        Fail '앱이 닫히지 않았어요. 앱을 직접 끈 다음 다시 실행해 주세요.'
    }

    # 바로 가기를 만든다. 예전 COM(WScript.Shell)은 영어 Windows에서 한글 파일 이름을 "??"로 바꿔 저장에 실패하므로,
    # 영어 임시 이름으로 저장한 뒤 PowerShell(유니코드)로 한글 이름으로 옮긴다
    function New-Shortcut([string]$Path, [string]$Target) {
        $temp = Join-Path ([IO.Path]::GetTempPath()) ("$AppName-" + [Guid]::NewGuid().ToString('N') + '.lnk')
        $shell = New-Object -ComObject WScript.Shell
        $link = $shell.CreateShortcut($temp)
        $link.TargetPath = $Target
        $link.WorkingDirectory = Split-Path $Target
        $link.IconLocation = "$Target,0"
        $link.Save()
        New-Item -ItemType Directory -Path (Split-Path $Path) -Force | Out-Null
        Move-Item -Path $temp -Destination $Path -Force
    }

    # 새 앱을 먼저 설치 폴더 옆에 다 옮겨 둔 뒤 예전 폴더와 바꿔치기한다. 중간에 실패하면 예전 앱을 그대로 되살린다
    function Install-Staged([string]$NewApp) {
        $staged = "$InstallDir.new"
        $backup = "$InstallDir.old"
        New-Item -ItemType Directory -Path (Split-Path $InstallDir) -Force | Out-Null
        foreach ($p in @($staged, $backup)) { if (Test-Path $p) { Remove-Item -Recurse -Force $p } }
        try { Copy-Item -Path $NewApp -Destination $staged -Recurse }
        catch {
            Remove-Item -Recurse -Force $staged -ErrorAction SilentlyContinue
            Fail '앱을 복사하지 못했어요. 디스크 공간을 확인해 주세요. 예전 앱은 그대로예요.'
        }
        if (Test-Path $InstallDir) { Rename-Item -Path $InstallDir -NewName (Split-Path $backup -Leaf) }
        try { Rename-Item -Path $staged -NewName (Split-Path $InstallDir -Leaf) }
        catch {
            if (Test-Path $backup) { Rename-Item -Path $backup -NewName (Split-Path $InstallDir -Leaf) }
            Fail '새 앱으로 바꾸지 못했어요. 예전 앱을 되살렸어요.'
        }
        if (Test-Path $backup) { Remove-Item -Recurse -Force $backup -ErrorAction SilentlyContinue }
    }

    function Install-App {
        if (-not [Environment]::Is64BitOperatingSystem) { Fail '이 앱은 64비트 Windows 10/11 전용이에요.' }
        $tmp = Join-Path ([IO.Path]::GetTempPath()) ("$AppName-" + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $tmp | Out-Null
        try {
            Say '최신 버전을 내려받는 중이에요...'
            Invoke-WebRequest -Uri $ZipUrl -OutFile "$tmp\app.zip" -UseBasicParsing
            Expand-Archive -Path "$tmp\app.zip" -DestinationPath "$tmp\unzipped" -Force
            $newApp = Join-Path $tmp "unzipped\$AppName"
            if (-not (Test-Path "$newApp\$AppName.exe")) { Fail '받은 파일 안에 앱이 없어요. 잠시 뒤 다시 해 주세요.' }
            Stop-RunningApp
            Install-Staged $newApp
        } finally {
            Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
        }
        Get-ChildItem -Path $InstallDir -Recurse -File | Unblock-File
        $exe = Join-Path $InstallDir "$AppName.exe"
        New-Shortcut $StartMenuLink $exe
        New-Shortcut $DesktopLink $exe
        Say "설치했어요: $InstallDir"
        if (-not $env:SCHEDULEAPP_NO_OPEN) {
            Start-Process -FilePath $exe
            Say "앱을 열었어요. 다음부터는 시작 메뉴나 바탕 화면의 '$ShortcutName'을 누르면 돼요."
        }
    }

    function Uninstall-App {
        Stop-RunningApp
        foreach ($p in @($InstallDir, $StartMenuLink, $DesktopLink)) {
            if (Test-Path $p) { Remove-Item -Recurse -Force $p }
        }
        Say '일정 관리 앱을 지웠어요. 일정 데이터는 %APPDATA%\ScheduleApp 에 남아 있어요.'
    }

    if ($env:SCHEDULEAPP_UNINSTALL) { Uninstall-App } else { Install-App }
}
