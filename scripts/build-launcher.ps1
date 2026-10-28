param([string]$OutputPath)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
if (-not $OutputPath) {$OutputPath=Join-Path $taskRoot '会议室预约系统.exe'}
$taskOutput=[IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $taskOutput) {throw '目标文件已存在，请指定一个新的 OutputPath；构建不会覆盖现有文件。'}
$taskParent=Split-Path -Parent $taskOutput
if (-not (Test-Path -LiteralPath $taskParent -PathType Container)) {throw '输出目录不存在，请指定项目内已有的目录。'}
$taskSource=Join-Path $PSScriptRoot 'launcher\MeetingLauncher.cs'
$taskCompiler=@(
    (Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'),
    (Join-Path $env:SystemRoot 'Microsoft.NET\Framework\v4.0.30319\csc.exe')
) | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
if (-not $taskCompiler) {throw '没有找到本机 .NET Framework 编译器，构建未执行；工具不会自动下载安装。'}
$taskBuild=Join-Path $taskRoot ('work\launcher-build\'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null
Add-Type -AssemblyName System.Drawing
$taskBitmap=New-Object Drawing.Bitmap(48,48)
$taskGraphics=[Drawing.Graphics]::FromImage($taskBitmap)
$taskBackground=New-Object Drawing.SolidBrush([Drawing.Color]::FromArgb(17,128,116))
$taskWhite=New-Object Drawing.Pen([Drawing.Color]::White,3)
$taskHandle=[IntPtr]::Zero
try {
    $taskGraphics.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $taskGraphics.Clear([Drawing.Color]::Transparent)
    $taskGraphics.FillRectangle($taskBackground,2,2,44,44)
    $taskGraphics.DrawRectangle($taskWhite,11,13,26,25)
    $taskGraphics.DrawLine($taskWhite,11,21,37,21)
    $taskGraphics.DrawLine($taskWhite,18,9,18,17)
    $taskGraphics.DrawLine($taskWhite,30,9,30,17)
    $taskGraphics.DrawLines($taskWhite,[Drawing.Point[]]@((New-Object Drawing.Point(17,29)),(New-Object Drawing.Point(22,34)),(New-Object Drawing.Point(31,25))))
    $taskHandle=$taskBitmap.GetHicon()
    $taskIcon=[Drawing.Icon]::FromHandle($taskHandle)
    $taskIconPath=Join-Path $taskBuild 'launcher.ico'
    $taskStream=[IO.File]::Create($taskIconPath)
    try {$taskIcon.Save($taskStream)} finally {$taskStream.Dispose();$taskIcon.Dispose()}
} finally {$taskGraphics.Dispose();$taskBitmap.Dispose();$taskWhite.Dispose();$taskBackground.Dispose()}
$taskCandidate=Join-Path $taskBuild 'MeetingLauncher.exe'
& $taskCompiler /nologo /target:winexe /platform:anycpu /optimize+ /utf8output /reference:System.Windows.Forms.dll ("/win32icon:"+$taskIconPath) ("/out:"+$taskCandidate) $taskSource
if ($LASTEXITCODE -ne 0) {throw '启动程序编译失败，已保留构建记录。'}
# Move only this newly generated artifact; File.Move refuses to replace an existing target.
[IO.File]::Move($taskCandidate,$taskOutput)
Write-Output ('已生成：'+$taskOutput)
Write-Output ('SHA256：'+(Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash)
