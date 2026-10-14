param([ValidateSet('Ui','Start','Stop','Status','Open')][string]$Action='Ui', [switch]$NoPage)
$ErrorActionPreference='Stop'
$script:ProjectRoot=Split-Path -Parent $PSScriptRoot
$script:WorkRoot=Join-Path $script:ProjectRoot 'work\launcher'
$script:StatePath=Join-Path $script:WorkRoot 'state.json'
$script:JarPath=Join-Path $script:ProjectRoot 'backend\target\meeting-room-scheduler-0.0.1-SNAPSHOT.jar'
$script:VitePath=Join-Path $script:ProjectRoot 'frontend\node_modules\vite\bin\vite.js'
$script:DataPath=Join-Path $script:ProjectRoot 'work\mysql-runtime\data'
$script:CredentialPath=Join-Path $script:ProjectRoot 'work\mysql-runtime\local-credentials.clixml'
$script:BrowserProfile=Join-Path $script:WorkRoot 'browser-profile'
$script:PageUrl='http://127.0.0.1:5173'
$script:Busy=$false
$script:CancelRequested=$false
$script:Form=$null
New-Item -ItemType Directory -Path $script:WorkRoot -Force | Out-Null
$taskHash=[Security.Cryptography.SHA256]::Create()
$taskMutexName=[BitConverter]::ToString($taskHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($script:ProjectRoot.ToLowerInvariant()))).Replace('-','')
$script:Mutex=New-Object Threading.Mutex($false,"Local\MeetingLauncher-$taskMutexName")

function Read-State {
    if (Test-Path -LiteralPath $script:StatePath) {
        $script:State=Get-Content -LiteralPath $script:StatePath -Encoding UTF8 -Raw | ConvertFrom-Json
        if ($script:State.version -ne 1 -or $script:State.root -ne $script:ProjectRoot) {throw '启动记录不属于当前项目，请保留记录并检查。'}
    } else {$script:State=[pscustomobject]@{version=1;root=$script:ProjectRoot;mode='';logDirectory='';processes=@()}}
}
function Write-State {$script:State | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $script:StatePath -Encoding UTF8}
function Get-OwnedProcess($entry) {
    $taskMarkers=@{backend=$script:JarPath;frontend=$script:VitePath;mysql=$script:DataPath;browser=$script:BrowserProfile}
    if (-not $taskMarkers.ContainsKey([string]$entry.role) -or $entry.marker -ne $taskMarkers[[string]$entry.role]) {throw '进程记录校验失败，未关闭任何不明进程。'}
    $taskProcess=Get-Process -Id ([int]$entry.pid) -ErrorAction SilentlyContinue
    if (-not $taskProcess) {return}
    if ($taskProcess.StartTime.ToUniversalTime().Ticks.ToString() -ne $entry.started -or $taskProcess.Path -ne $entry.executable) {return}
    $taskInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.pid)"
    if (-not $taskInfo.CommandLine -or $taskInfo.CommandLine.IndexOf($entry.marker,[StringComparison]::OrdinalIgnoreCase) -lt 0) {throw '无法确认进程归属，请保留启动记录后重试。'}
    return $taskProcess
}
function Test-Port([int]$port) {
    $taskClient=New-Object Net.Sockets.TcpClient
    try {return $taskClient.ConnectAsync('127.0.0.1',$port).Wait(250) -and $taskClient.Connected}
    catch {return $false} finally {$taskClient.Dispose()}
}
function Set-Status([string]$text) {
    if ($script:Form) {$script:StatusLabel.Text=$text; [Windows.Forms.Application]::DoEvents()}
    else {Write-Host $text}
}
function Wait-Ready([scriptblock]$check,[int]$seconds,[string]$message) {
    Set-Status $message
    $taskDeadline=(Get-Date).AddSeconds($seconds)
    do {
        if ($script:CancelRequested) {throw '已取消启动。'}
        if (& $check) {return}
        if ($script:Form) {[Windows.Forms.Application]::DoEvents()}
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $taskDeadline)
    throw "$message 超时，请查看运行日志。"
}
function Test-Http([string]$url) {
    try {return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200}
    catch {return $false}
}
function Find-Executable([string]$name) {
    $taskCommand=Get-Command $name -ErrorAction SilentlyContinue
    if (-not $taskCommand) {throw "缺少 $name。请先按 README 准备运行环境。"}
    return $taskCommand.Source
}
function Find-MySql {
    $taskCandidates=@()
    if ($env:MEETING_MYSQL_HOME) {$taskCandidates+=$env:MEETING_MYSQL_HOME}
    $taskInstallation=Join-Path $env:ProgramFiles 'MySQL'
    if (Test-Path -LiteralPath $taskInstallation) {$taskCandidates+=@(Get-ChildItem -LiteralPath $taskInstallation -Directory -Filter 'MySQL Server 8*' | Sort-Object Name -Descending | ForEach-Object {$_.FullName})}
    foreach ($taskCandidate in $taskCandidates) {
        if ((Test-Path -LiteralPath (Join-Path $taskCandidate 'bin\mysqld.exe')) -and (Test-Path -LiteralPath (Join-Path $taskCandidate 'bin\mysqladmin.exe'))) {return $taskCandidate}
    }
    throw '没有找到现有 MySQL 8。请准备环境或设置 MEETING_MYSQL_HOME，工具不会自动安装。'
}
function Find-Browser {
    $taskCandidates=@((Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'),(Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'),(Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'),(Join-Path $env:LOCALAPPDATA 'Google\Chrome\Application\chrome.exe'))
    foreach ($taskCandidate in $taskCandidates) {if (Test-Path -LiteralPath $taskCandidate) {return $taskCandidate}}
    throw '打开独立页面需要已安装的 Chrome 或 Edge。也可手动访问 http://127.0.0.1:5173。'
}
function Start-Hidden([string]$executable,[string[]]$arguments,[string]$directory,[hashtable]$environment,[string]$logName) {
    # Passwords reach only the child environment, never arguments or state files.
    $taskPrevious=@{}
    try {
        foreach ($taskKey in $environment.Keys) {
            $taskPrevious[$taskKey]=[Environment]::GetEnvironmentVariable($taskKey,'Process')
            [Environment]::SetEnvironmentVariable($taskKey,$environment[$taskKey],'Process')
        }
        $taskQuoted=@($arguments | ForEach-Object {
            if ($_.Contains('"') -or $_.EndsWith('\')) {throw '启动参数包含不支持的引号或结尾斜线。'}
            '"'+$_+'"'
        }) -join ' '
        $taskProcess=Start-Process -FilePath $executable -ArgumentList $taskQuoted -WorkingDirectory $directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $script:State.logDirectory "$logName.out.log") -RedirectStandardError (Join-Path $script:State.logDirectory "$logName.err.log")
        $taskHandle=$taskProcess.Handle
        return $taskProcess
    } finally {foreach ($taskKey in $taskPrevious.Keys) {[Environment]::SetEnvironmentVariable($taskKey,$taskPrevious[$taskKey],'Process')}}
}
function Remember-Process([string]$role,$process,[string]$marker) {
    if ($process.HasExited) {throw "$role 未能启动，请查看运行日志。"}
    # Refresh metadata through an independent process handle after creation.
    $taskLive=Get-Process -Id $process.Id -ErrorAction Stop
    $taskEntry=[pscustomobject]@{role=$role;pid=$taskLive.Id;started=$taskLive.StartTime.ToUniversalTime().Ticks.ToString();executable=$taskLive.Path;marker=$marker}
    $script:State.processes=@($script:State.processes)+$taskEntry
    Write-State
}
function Stop-ApplicationProcess($process,[bool]$closeWindow) {
    # Capture verified descendants, preserving unrelated processes and reused PIDs.
    $taskSnapshot=@(Get-CimInstance Win32_Process)
    $taskIds=@($process.Id)
    do {
        $taskNew=@($taskSnapshot | Where-Object {$_.ParentProcessId -in $taskIds -and $_.ProcessId -notin $taskIds -and $_.CreationDate -ge $process.StartTime})
        $taskIds+=@($taskNew | ForEach-Object {[int]$_.ProcessId})
    } while ($taskNew.Count -gt 0)
    $taskOwned=@(foreach ($taskId in $taskIds) {
        $taskChild=Get-Process -Id $taskId -ErrorAction SilentlyContinue
        if ($taskChild) {
            try {if ($taskChild.StartTime) {[pscustomobject]@{pid=$taskChild.Id;started=$taskChild.StartTime.ToUniversalTime().Ticks}}}
            catch {if (-not $taskChild.HasExited) {throw}}
        }
    })
    if ($closeWindow) {$process.CloseMainWindow() | Out-Null; $process.WaitForExit(2500) | Out-Null}
    foreach ($taskItem in $taskOwned) {
        $taskCurrent=Get-Process -Id $taskItem.pid -ErrorAction SilentlyContinue
        if ($taskCurrent) {
            try {
                if ($taskCurrent.StartTime -and $taskCurrent.StartTime.ToUniversalTime().Ticks -eq $taskItem.started) {
                    Stop-Process -Id $taskCurrent.Id -ErrorAction Stop
                    $taskCurrent.WaitForExit(5000) | Out-Null
                }
            } catch {if (-not $taskCurrent.HasExited) {throw}}
        }
    }
    $taskRemaining=Get-Process -Id $process.Id -ErrorAction SilentlyContinue
    if ($taskRemaining -and $taskRemaining.StartTime -and $taskRemaining.StartTime -eq $process.StartTime) {throw '进程未完成关闭，已保留启动记录，请重试。'}
}
function Stop-AllCore {
    $taskErrors=@()
    foreach ($taskRole in @('browser','frontend','backend','mysql')) {
        foreach ($taskEntry in @($script:State.processes | Where-Object {$_.role -eq $taskRole})) {
            try {
                $taskProcess=Get-OwnedProcess $taskEntry
                if ($taskProcess) {
                    $taskNames=@{browser='项目页面';frontend='前端';backend='后端';mysql='数据库'}
                    Set-Status ("正在关闭"+$taskNames[$taskRole]+'…')
                    if ($taskRole -eq 'mysql') {
                        $taskCredentials=Import-Clixml -LiteralPath $script:CredentialPath
                        $taskAdmin=Join-Path (Split-Path -Parent $taskEntry.executable) 'mysqladmin.exe'
                        $taskAdminProcess=Start-Hidden $taskAdmin @('--no-defaults','--host=127.0.0.1','--port=3306','--user=root','shutdown') $script:ProjectRoot @{MYSQL_PWD=[Net.NetworkCredential]::new('', $taskCredentials.RootPassword).Password} ('mysql-stop-'+[Guid]::NewGuid().ToString('N'))
                        if (-not $taskAdminProcess.WaitForExit(15000)) {$taskAdminProcess.Kill(); throw '数据库关闭超时，已保留数据库进程。'}
                        if ($taskAdminProcess.ExitCode -ne 0 -or -not $taskProcess.WaitForExit(15000)) {throw '数据库未完成正常关闭，请重试；不会强制终止数据库。'}
                    } else {Stop-ApplicationProcess $taskProcess ($taskRole -eq 'browser')}
                }
                $script:State.processes=@($script:State.processes | Where-Object {$_.pid -ne $taskEntry.pid})
                Write-State
            } catch {$taskErrors+=$_.Exception.Message}
        }
    }
    if ($taskErrors.Count) {throw ($taskErrors -join "`n")}
    Set-Status '已关闭，可以再次启动。'
}
function Open-PageCore {
    if (-not (Test-Http "$script:PageUrl/api/v1/auth/csrf")) {throw '前后端尚未就绪，请先启动。'}
    foreach ($taskEntry in @($script:State.processes | Where-Object {$_.role -eq 'browser'})) {
        if (Get-OwnedProcess $taskEntry) {Set-Status '项目页面已打开。'; return}
    }
    $script:State.processes=@($script:State.processes | Where-Object {$_.role -ne 'browser'})
    $taskBrowser=Find-Browser
    $taskProcess=Start-Hidden $taskBrowser @("--user-data-dir=$script:BrowserProfile",'--no-first-run','--no-default-browser-check','--disable-background-mode',"--app=$script:PageUrl") $script:ProjectRoot @{} ('browser-'+[Guid]::NewGuid().ToString('N'))
    Start-Sleep -Milliseconds 300
    if ($taskProcess.HasExited) {
        $taskCandidate=Get-CimInstance Win32_Process | Where-Object {$_.ExecutablePath -eq $taskBrowser -and $_.CommandLine -and $_.CommandLine.Contains("--user-data-dir=$script:BrowserProfile") -and $_.CommandLine -notmatch '--type='} | Select-Object -First 1
        if (-not $taskCandidate) {throw '项目页面打开失败，请查看运行日志。'}
        $taskProcess=Get-Process -Id $taskCandidate.ProcessId
    }
    Remember-Process 'browser' $taskProcess $script:BrowserProfile
    Set-Status '运行中，项目页面已打开。'
}
function Start-AllCore {
    $taskAlive=@($script:State.processes | ForEach-Object {Get-OwnedProcess $_})
    if ($taskAlive.Count -gt 0) {
        if (-not (Test-Http "$script:PageUrl/api/v1/auth/csrf")) {throw '已有启动记录但服务未就绪，请先关闭全部再启动。'}
        if (-not $NoPage) {Open-PageCore}
        Set-Status '项目已运行，无需重复启动。'; return
    }
    foreach ($taskPort in @(8080,5173)) {if (Test-Port $taskPort) {throw "端口 $taskPort 已被其他启动方式占用。请先关闭原项目终端；工具不会接管已有进程。"}}
    if (-not (Test-Path -LiteralPath $script:JarPath)) {throw '后端尚未构建，请先在 backend 目录执行 mvn package。'}
    if (-not (Test-Path -LiteralPath $script:VitePath)) {throw '前端依赖未准备，请先在 frontend 目录执行 npm ci。'}
    $taskJava=Find-Executable 'java.exe'; $taskNode=Find-Executable 'node.exe'
    if (-not $NoPage) {Find-Browser | Out-Null}
    $script:State.processes=@()
    $script:State.logDirectory=Join-Path $script:WorkRoot ('run-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path $script:State.logDirectory | Out-Null
    $taskEnvironment=@{}; $taskProfile='demo'; $script:State.mode='H2 演示'
    if ($env:MEETING_DB_USER -and $env:MEETING_DB_PASSWORD) {
        $taskProfile='mysql'; $script:State.mode='已配置的 MySQL'
        $taskEnvironment=@{MEETING_DB_INIT_MODE='never';MEETING_DEMO_DATA='false'}
    } elseif (Test-Path -LiteralPath $script:CredentialPath) {
        if (-not (Test-Path -LiteralPath (Join-Path $script:DataPath 'mysql'))) {throw '现有 MySQL 数据目录缺失，工具不会自动初始化。'}
        $taskCredentials=Import-Clixml -LiteralPath $script:CredentialPath
        $taskMySql=Find-MySql; $taskProfile='mysql'; $script:State.mode='本机 MySQL 演示库'
        $taskEnvironment=@{MEETING_DB_URL='jdbc:mysql://127.0.0.1:3306/meeting_scheduler?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true';MEETING_DB_USER='meeting_app';MEETING_DB_PASSWORD=[Net.NetworkCredential]::new('', $taskCredentials.ApplicationPassword).Password;MEETING_DB_INIT_MODE='never';MEETING_DEMO_DATA='false'}
        if (Test-Port 3306) {
            $taskExisting=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.IndexOf($script:DataPath,[StringComparison]::OrdinalIgnoreCase) -ge 0})
            if ($taskExisting.Count -ne 1) {throw '3306 被其他数据库占用，工具不会接管。'}
            $taskListeners=@(Get-NetTCPConnection -LocalPort 3306 -State Listen)
            if (-not $taskListeners.Count -or @($taskListeners | Where-Object {$_.OwningProcess -ne $taskExisting[0].ProcessId}).Count) {throw '无法确认3306属于项目数据库，工具不会接管。'}
            Set-Status '复用项目数据库，关闭时会保留它。'
        } else {
            $taskMySqlProcess=Start-Hidden (Join-Path $taskMySql 'bin\mysqld.exe') @('--no-defaults',"--basedir=$taskMySql","--datadir=$script:DataPath",'--port=3306','--bind-address=127.0.0.1','--mysqlx-bind-address=127.0.0.1','--character-set-server=utf8mb4','--collation-server=utf8mb4_unicode_ci','--console') $script:ProjectRoot @{} 'mysql'
            Remember-Process 'mysql' $taskMySqlProcess $script:DataPath
            Wait-Ready {if ($taskMySqlProcess.HasExited) {throw '数据库启动失败，请查看日志。'}; Test-Port 3306} 30 '正在启动数据库…'
        }
    }
    Write-State
    $taskBackend=Start-Hidden $taskJava @('-jar',$script:JarPath,"--spring.profiles.active=$taskProfile") (Join-Path $script:ProjectRoot 'backend') $taskEnvironment 'backend'
    Remember-Process 'backend' $taskBackend $script:JarPath
    Wait-Ready {if ($taskBackend.HasExited) {throw '后端启动失败，请查看日志。'}; Test-Http 'http://127.0.0.1:8080/api/v1/auth/csrf'} 60 '正在启动后端…'
    $taskFrontend=Start-Hidden $taskNode @($script:VitePath,'--host','127.0.0.1','--port','5173','--strictPort') (Join-Path $script:ProjectRoot 'frontend') @{} 'frontend'
    Remember-Process 'frontend' $taskFrontend $script:VitePath
    Wait-Ready {if ($taskFrontend.HasExited) {throw '前端启动失败，请查看日志。'}; Test-Http "$script:PageUrl/api/v1/auth/csrf"} 30 '正在启动前端…'
    if (-not $NoPage) {Open-PageCore}
    Set-Status "运行中 · $($script:State.mode)"
}
function Invoke-Operation([string]$operation) {
    $taskLocked=$false
    try {
        try {$taskLocked=$script:Mutex.WaitOne(0)} catch [Threading.AbandonedMutexException] {$taskLocked=$true}
        if (-not $taskLocked) {throw '另一个启动工具正在操作，请稍后重试。'}
        Read-State
        switch ($operation) {
            'Start' {
                try {Start-AllCore} catch {
                    $taskFailure=$_.Exception.Message
                    try {Stop-AllCore} catch {$taskFailure+="`n关闭未完成："+$_.Exception.Message}
                    throw $taskFailure
                }
            }
            'Stop' {Stop-AllCore}
            'Open' {Open-PageCore}
            'Status' {
                $taskRoles=@($script:State.processes | Where-Object {Get-OwnedProcess $_} | ForEach-Object {$_.role})
                [pscustomobject]@{mode=$script:State.mode;running=$taskRoles;backend=(Test-Port 8080);frontend=(Test-Port 5173)} | ConvertTo-Json
            }
        }
    } finally {if ($taskLocked) {$script:Mutex.ReleaseMutex()}}
}
if ($Action -ne 'Ui') {
    try {Invoke-Operation $Action} catch {Write-Error $_.Exception.Message; exit 1}
    finally {$script:Mutex.Dispose()}
    exit 0
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class MeetingLauncherWindow {
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr handle, int command);
}
"@
[Windows.Forms.Application]::EnableVisualStyles()
$script:Form=New-Object Windows.Forms.Form
$script:Form.Text='会议室项目 · 启动工具'
$script:Form.ClientSize=New-Object Drawing.Size(470,245)
$script:Form.StartPosition='CenterScreen'
$script:Form.FormBorderStyle='FixedDialog'; $script:Form.MaximizeBox=$false
$script:Form.Font=New-Object Drawing.Font('Microsoft YaHei UI',10)
$script:Form.BackColor=[Drawing.Color]::FromArgb(246,248,248)
$taskTitle=New-Object Windows.Forms.Label
$taskTitle.Text='会议室预约系统'
$taskTitle.Font=New-Object Drawing.Font('Microsoft YaHei UI',16,[Drawing.FontStyle]::Bold)
$taskTitle.SetBounds(24,20,420,36)
$script:StatusLabel=New-Object Windows.Forms.Label
$script:StatusLabel.Text='点击启动，稍等片刻即可使用。'
$script:StatusLabel.SetBounds(26,64,420,34)
$script:StartButton=New-Object Windows.Forms.Button
$script:StartButton.Text='启动并打开页面'; $script:StartButton.SetBounds(26,108,250,44)
$script:StartButton.BackColor=[Drawing.Color]::FromArgb(17,128,116)
$script:StartButton.ForeColor=[Drawing.Color]::White; $script:StartButton.FlatStyle='Flat'
$script:StopButton=New-Object Windows.Forms.Button
$script:StopButton.Text='关闭全部'; $script:StopButton.SetBounds(290,108,154,44)
$script:PageButton=New-Object Windows.Forms.Button
$script:PageButton.Text='重新打开页面'; $script:PageButton.SetBounds(26,168,160,32)
$taskLogs=New-Object Windows.Forms.LinkLabel
$taskLogs.Text='查看运行日志'; $taskLogs.SetBounds(306,176,140,26)
$taskTip=New-Object Windows.Forms.Label
$taskTip.Text='关闭此工具也会停止它启动的服务和独立页面。'
$taskTip.ForeColor=[Drawing.Color]::DimGray
$taskTip.Font=New-Object Drawing.Font('Microsoft YaHei UI',9); $taskTip.SetBounds(26,213,420,22)
$script:Form.Controls.AddRange(@($taskTitle,$script:StatusLabel,$script:StartButton,$script:StopButton,$script:PageButton,$taskLogs,$taskTip))
function Invoke-UiOperation([string]$operation) {
    if ($script:Busy) {return}
    $script:Busy=$true
    $script:StartButton.Enabled=$false; $script:StopButton.Enabled=$false; $script:PageButton.Enabled=$false
    try {Invoke-Operation $operation} catch {
        $script:StatusLabel.Text='操作未完成，请查看提示或运行日志。'
        if (-not $script:CancelRequested) {[Windows.Forms.MessageBox]::Show($script:Form,$_.Exception.Message,'启动工具','OK','Warning') | Out-Null}
    } finally {
        $script:Busy=$false
        $script:StartButton.Enabled=$true; $script:StopButton.Enabled=$true; $script:PageButton.Enabled=$true
        if ($script:CancelRequested) {$script:Form.Close()}
    }
}
$script:StartButton.Add_Click({Invoke-UiOperation 'Start'})
$script:StopButton.Add_Click({Invoke-UiOperation 'Stop'})
$script:PageButton.Add_Click({Invoke-UiOperation 'Open'})
$script:Form.Add_Shown({
    # The interactive form must remain visible even when its console host is hidden.
    [MeetingLauncherWindow]::ShowWindow($script:Form.Handle,5) | Out-Null
    $script:Form.Activate()
    try {
        Read-State
        $taskRoles=@($script:State.processes | Where-Object {Get-OwnedProcess $_} | ForEach-Object {$_.role})
        if ('backend' -in $taskRoles -and 'frontend' -in $taskRoles) {$script:StatusLabel.Text="运行中 · $($script:State.mode)"}
        elseif ($taskRoles.Count) {$script:StatusLabel.Text='服务未完整启动，可先关闭全部再启动。'}
    } catch {$script:StatusLabel.Text='无法读取启动状态，请保留记录并检查。'}
})
$taskLogs.Add_LinkClicked({Start-Process -FilePath 'explorer.exe' -ArgumentList ('"'+$script:WorkRoot+'"')})
$script:Form.Add_FormClosing({
    param($sender,$eventArgs)
    if ($script:Busy) {$script:CancelRequested=$true; $eventArgs.Cancel=$true; return}
    try {Invoke-Operation 'Stop'} catch {
        $eventArgs.Cancel=$true
        [Windows.Forms.MessageBox]::Show($script:Form,$_.Exception.Message,'关闭未完成','OK','Warning') | Out-Null
    }
})
try {[Windows.Forms.Application]::Run($script:Form)} finally {$script:Mutex.Dispose(); $script:Form.Dispose()}
