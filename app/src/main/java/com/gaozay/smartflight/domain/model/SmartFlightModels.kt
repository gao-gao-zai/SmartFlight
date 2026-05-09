package com.gaozay.smartflight.domain.model

enum class AppListStatus(val label: String) {
    Candidate("候选"),
    Whitelist("白名单"),
    Blacklist("黑名单"),
    Ignored("忽略"),
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
    System("Follow System"),
    Light("Light"),
    Dark("Dark"),
}

enum class ThemePalette(val label: String) {
    Brand("Brand"),
    Sky("Sky"),
    Mint("Mint"),
    Sand("Sand"),
    Rose("Rose"),
    Night("Night"),
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
