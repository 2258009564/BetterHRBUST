# 将 web/public/HRBUST.png 转换为 desktop/build/icon.ico
# 实现：System.Drawing 高质量缩放到 256x256，再以 ICO 容器封装 PNG 载荷
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\make-icon.ps1

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$desktopDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $desktopDir

$output = Join-Path $desktopDir 'build\icon.ico'

# 源图优先级：build\icon-source.png（自定义图标） > web\public\HRBUST.png（校徽回退）
$preferred = Join-Path $desktopDir 'build\icon-source.png'
if (Test-Path $preferred) {
    $source = $preferred
}
else {
    $source = Join-Path $repoRoot 'web\public\HRBUST.png'
}

if (-not (Test-Path $source)) {
    Write-Error "源图不存在: $source"
}
Write-Host "源图: $source"

Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Image]::FromFile($source)
try {
    # 源图已是正方形校徽，等比缩放到 256x256 并保留透明通道
    $bmp = New-Object System.Drawing.Bitmap 256, 256
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.DrawImage($src, 0, 0, 256, 256)
    }
    finally {
        $g.Dispose()
    }

    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $png = $ms.ToArray()
    $bmp.Dispose()

    # ICO 容器：6 字节文件头 + 16 字节目录项 + PNG 载荷（宽高字段 0 表示 256）
    $ico = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ico)
    try {
        $w.Write([uint16]0)                 # 保留字段
        $w.Write([uint16]1)                 # 类型：图标
        $w.Write([uint16]1)                 # 图像数量
        $w.Write([byte]0)                   # 宽（0 = 256）
        $w.Write([byte]0)                   # 高（0 = 256）
        $w.Write([byte]0)                   # 调色板色数
        $w.Write([byte]0)                   # 保留
        $w.Write([uint16]1)                 # 颜色平面数
        $w.Write([uint16]32)                # 位深
        $w.Write([uint32]$png.Length)       # 数据长度
        $w.Write([uint32]22)                # 数据偏移 = 6 + 16
        $w.Write($png)
        $w.Flush()
    }
    finally {
        $w.Dispose()
    }

    $buildDir = Split-Path -Parent $output
    if (-not (Test-Path $buildDir)) {
        New-Item -ItemType Directory -Path $buildDir | Out-Null
    }
    [System.IO.File]::WriteAllBytes($output, $ico.ToArray())

    Write-Host ("icon.ico 已生成: {0} ({1} bytes)" -f $output, ($png.Length + 22))
}
finally {
    $src.Dispose()
}
