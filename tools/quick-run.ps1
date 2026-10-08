# Build -> cai -> mo lai app -> chup man hinh, trong 1 lenh.
# Cach dung (chay trong thu muc project):
#   powershell -ExecutionPolicy Bypass -File tools\quick-run.ps1
# Giu emulator dang chay; sau khi app mo, tu bam den man can xem roi chay lai script neu muon chup moi.

param(
    [switch]$NoLaunch,    # Chi build + cai, khong mo lai app
    [switch]$NoDebugLink  # Mo lai app nhung dung bam loi tat Confirm (de tu bam toi man khac)
)

$ErrorActionPreference = "Stop"
$pkg = "vn.baotinmanhhai.btmhkiosk"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$shot = Join-Path $root "app\build\kiosk-screen.png"

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:ANDROID_HOME = $sdk

Push-Location $root
try {
    # 1. Build tang dan (chi bien dich lai file da doi)
    $t0 = Get-Date
    & .\gradlew.bat assembleDebug --offline -q
    if ($LASTEXITCODE -ne 0) { throw "Build that bai" }
    "build: " + [math]::Round(((Get-Date) - $t0).TotalSeconds, 1) + "s"

    # 2. Cai APK (giu du lieu app)
    & $adb install -r $apk | Out-Null
    "installed"

    if (-not $NoLaunch) {
        # 3. Xoa du lieu app de luon ve man Kich hoat (chua enroll), roi mo lai va bam thang vao loi tat
        #    DEBUG "Xem man Thong tin don hang" -> vao thang man Confirm voi gio hang gia, bo qua
        #    kich hoat/dang nhap/them san pham. Muon xem man khac thi dung -NoDebugLink roi tu bam tay.
        & $adb shell pm clear $pkg | Out-Null
        & $adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
        Start-Sleep -Seconds 8
        if (-not $NoDebugLink) {
            & $adb shell input tap 540 1808   # lien ket "DEBUG: Xem man Thong tin don hang" duoi cung the Kich hoat
            Start-Sleep -Seconds 2
        }
    }

    # 4. Chup man hinh hien tai
    & $adb shell screencap -p /sdcard/quick.png
    & $adb pull /sdcard/quick.png $shot | Out-Null
    "screenshot: $shot"
}
finally {
    Pop-Location
}
