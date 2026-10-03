package com.gaozay.smartflight.domain.model

import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.ResourceLabel

enum class AppOnlineSourceTag(override val labelRes: Int) : ResourceLabel {
    Auto(R.string.automatic),
    Manual(R.string.manual),
}

enum class NetworkControlMode(override val labelRes: Int) : ResourceLabel {
    AirplaneMode(R.string.airplane_mode),
    MobileData(R.string.mobile_data),
}

enum class ExecutorType(override val labelRes: Int) : ResourceLabel {
    Auto(R.string.automatic_selection),
    Shizuku(R.string.shizuku),
    AdbBootstrapped(R.string.adb_initialization),
    Root(R.string.root),
    Unavailable(R.string.unavailable),
}

enum class ThemeMode(override val labelRes: Int) : ResourceLabel {
    System(R.string.follow_system),
    Light(R.string.light),
    Dark(R.string.dark),
}

enum class ThemePalette(override val labelRes: Int, val seedColorArgb: Int) : ResourceLabel {
    LogoOriginal(R.string.original_logo_colors, 0xFF545D6D.toInt()),
    CoolGray(R.string.cool_gray_console, 0xFF657181.toInt()),
    NightFlight(R.string.night_flight, 0xFF2F3948.toInt()),
    WarmPaper(R.string.warm_paper, 0xFFA1859B.toInt()),
    Custom(R.string.my_style, 0xFF545D6D.toInt()),
}

enum class ThemeIntensity(override val labelRes: Int) : ResourceLabel {
    Restrained(R.string.subtle),
    Standard(R.string.standard),
    HighContrast(R.string.high_contrast),
}

enum class CornerStyle(override val labelRes: Int) : ResourceLabel {
    Compact(R.string.compact),
    Standard(R.string.standard),
    Soft(R.string.soft),
}

enum class ScreenState(override val labelRes: Int) : ResourceLabel {
    Unknown(R.string.unknown_smart_flight_models),
    ScreenOn(R.string.screen_on),
    ScreenOff(R.string.screen_off_smart_flight_models),
    Unlocked(R.string.unlocked),
}

enum class UnifiedNetworkState(override val labelRes: Int) : ResourceLabel {
    Unknown(R.string.unknown_smart_flight_models),
    Offline(R.string.offline_apps_ui_models),
    WifiOnly(R.string.wi_fi_only),
    CellularOnly(R.string.cellular_only),
    WifiAndCellular(R.string.wi_fi_and_cellular),
    AirplaneWithWifi(R.string.airplane_with_wi_fi),
}

enum class TriggerSource(override val labelRes: Int) : ResourceLabel {
    Manual(R.string.manual_smart_flight_models),
    AppForegroundChanged(R.string.app_foreground_changed),
    ScreenOff(R.string.screen_off_smart_flight_models),
    ScreenOn(R.string.screen_on),
    UserUnlocked(R.string.user_unlocked),
    SettingsChanged(R.string.settings_changed),
    ServiceRestored(R.string.service_restored),
}

enum class ExecutionAction(override val labelRes: Int) : ResourceLabel {
    DoNothing(R.string.do_nothing),
    ScheduleScreenOffDisconnect(R.string.schedule_screen_off_disconnect),
    ScheduleAppExitDisconnect(R.string.schedule_app_exit_disconnect),
    CancelScheduledDisconnect(R.string.cancel_scheduled_disconnect),
    ReconnectNow(R.string.reconnect_now_smart_flight_models),
    DisconnectNow(R.string.disconnect_now_smart_flight_models),
    PauseAutomation(R.string.pause_automation),
}

enum class ExecutionResult(override val labelRes: Int) : ResourceLabel {
    Pending(R.string.pending_smart_flight_models),
    Success(R.string.success_smart_flight_models),
    Failed(R.string.failed_smart_flight_models),
    PartialSuccess(R.string.partial_success_smart_flight_models),
    Skipped(R.string.skipped_smart_flight_models),
}
