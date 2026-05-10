package com.gaozay.smartflight.domain.model

enum class AppOnlineSourceTag(val label: String) {
    Auto("自动"),
    Manual("手动"),
}

enum class NetworkControlMode(val label: String) {
    AirplaneMode("飞行模式"),
    MobileData("移动数据"),
}

enum class ExecutorType(val label: String) {
    Auto("自动选择"),
    Shizuku("Shizuku"),
    AdbBootstrapped("ADB 初始化"),
    Root("Root"),
    Unavailable("不可用"),
}

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

enum class ThemePalette(val label: String, val seedColorArgb: Int) {
    LogoOriginal("Logo 原色", 0xFF545D6D.toInt()),
    CoolGray("冷灰控制台", 0xFF657181.toInt()),
    NightFlight("夜航深色", 0xFF2F3948.toInt()),
    WarmPaper("暖白纸面", 0xFFA1859B.toInt()),
    Custom("我的风格", 0xFF545D6D.toInt()),
}

enum class ThemeIntensity(val label: String) {
    Restrained("克制"),
    Standard("标准"),
    HighContrast("高对比"),
}

enum class CornerStyle(val label: String) {
    Compact("紧凑"),
    Standard("标准"),
    Soft("柔和"),
}

enum class ScreenState(val label: String) {
    Unknown("Unknown"),
    ScreenOn("Screen On"),
    ScreenOff("Screen Off"),
    Unlocked("Unlocked"),
}

enum class UnifiedNetworkState(val label: String) {
    Unknown("Unknown"),
    Offline("Offline"),
    WifiOnly("Wi-Fi Only"),
    CellularOnly("Cellular Only"),
    WifiAndCellular("Wi-Fi and Cellular"),
    AirplaneWithWifi("Airplane with Wi-Fi"),
}

enum class TriggerSource(val label: String) {
    Manual("Manual"),
    AppForegroundChanged("App Foreground Changed"),
    ScreenOff("Screen Off"),
    ScreenOn("Screen On"),
    UserUnlocked("User Unlocked"),
    SettingsChanged("Settings Changed"),
    ServiceRestored("Service Restored"),
}

enum class ExecutionAction(val label: String) {
    DoNothing("Do Nothing"),
    ScheduleScreenOffDisconnect("Schedule Screen-Off Disconnect"),
    ScheduleAppExitDisconnect("Schedule App-Exit Disconnect"),
    CancelScheduledDisconnect("Cancel Scheduled Disconnect"),
    ReconnectNow("Reconnect Now"),
    DisconnectNow("Disconnect Now"),
    PauseAutomation("Pause Automation"),
}

enum class ExecutionResult(val label: String) {
    Pending("Pending"),
    Success("Success"),
    Failed("Failed"),
    PartialSuccess("Partial Success"),
    Skipped("Skipped"),
}
