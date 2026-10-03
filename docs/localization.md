# English localization — issue #1

This change is based on master commit `65f7df37a918ef98c97a247eb5530bb46cd8d506` and addresses [Translate into English](https://github.com/gao-gao-zai/SmartFlight/issues/1).

## Inventory and resources

The original `app/src/main` inventory contained 515 lines with Chinese text. The Kotlin string inventory identified 483 distinct Chinese strings: 481 display strings and two fragments used to make execution decisions. Display strings now live in resources; the two decision fragments have been replaced by structured checks.

`app/src/main/res/values/strings.xml` retains the original Chinese display text. `app/src/main/res/values-en/strings.xml` contains the corresponding English catalog. Each catalog has **525 strings and 10 plurals**. The plurals cover app/filter counts, delays, countdowns, and the seconds display. Intentional spaces in diagnostic prefixes and suffixes are preserved using Android resource quoting.

| Area | Sources covered | User-visible copy |
| --- | --- | --- |
| Startup and navigation | `SmartFlightRoot`, `MainViewModel`, `AccessGateScreen`, `AccessGateSummaryCard` | App name, setup heading, access requirements, navigation titles, initial state, Back description |
| Permission checks | `AccessModels`, `SystemPermissionChecker`, `ShizukuAccessChecker`, `RootAccessChecker`, `AdbAccessChecker` | Statuses, recommendations, explanations, command versions, authorization results and errors |
| Permission actions | `AccessGateAdvancedCard`, `AccessHandlingDialog`, `SystemAccessCard`, `AdbBootstrapActions` | Settings links, Shizuku/Root authorization actions, ADB copy/reset/complete actions |
| Automation settings | `RulesScreen`, `UserSettings`, `UserSettingsPreferencesMapper`, `SmartFlightUiComponents` | All rule labels and descriptions, delay units, prompts, rule preview, saved state, monitoring methods |
| Foreground notification and accessibility | `values/strings.xml`, `AutomationForegroundService`, accessibility service XML | Notification title/body/channel, accessibility service description |
| Quick Settings tile | `AutomationTileService`, `AutomationDisableMode` | Tile name and all nine disable-cycle subtitles |
| Dashboard and runtime | `DashboardScreen`, `DashboardUiStateMapper`, `RuntimeSummaryFormatter`, runtime handlers/reporters | Connectivity/executor states, pauses, restores, cancellation reasons, countdowns, rule explanations and event failures |
| Executors | Command models, probe service, validators, command runners, `NetworkControlCommandMapper` | Command purposes, availability, authorization, unsupported-device messages, successful/failed/already-applied outcomes |
| Diagnostics and logs | `DiagnosticsScreen`, `DiagnosticsDialogs`, `AccessResultFormatter`, execution-log mapper | Confirmation dialogs, diagnostic actions, action/result labels, output/error prefixes and summaries |
| App management | `AppsManagementController`, `AppsUiModels`, `AppManagementScreen`, filter/list/scope components | Scan results/errors, empty states, search, filters, source tags and online/offline selections |
| Appearance | `AppearanceScreen`, theme enums | Display modes, palettes, intensity, corner styles, preview badges and color labels |
| About and updates | `AboutScreen`, `UpdatePromptDialog`, action dispatcher and update classes | Version information, release links, update explanations, check results, errors and skip/dismiss actions |

`res/xml/locales_config.xml` declares Chinese and English for Android 13+ per-app language settings. On older Android versions, resources follow the system locale. The default fallback remains Chinese.

## Behavior preserved

- Permission actions use `AccessKind`, rather than comparing a translated title. All built-in checks and initial gate results carry the corresponding kind.
- An executor that already has the requested state reports `alreadyInRequestedState`; result classification no longer searches the summary for Chinese words. Missing-executor checks use runner availability.
- Enum labels and read-only command purposes are resolved when read, so they do not capture the language used when their instances were first initialized. Compose screens resolve labels with `stringResource`.
- Runtime formatters use the application resource resolver installed in `SmartFlightApp.attachBaseContext`, before Hilt constructs app state. No Activity is retained.
- New default reconnect/disconnect prompts use an empty stored value to mean the built-in localized prompt. The editor shows that prompt as a placeholder, and the notifier resolves it at display time. Previously saved and newly customized prompt text stays verbatim, including Chinese text that matches an older built-in default.
- Existing execution logs and persisted explanation text remain in their original language. Probe action labels recognize both supported language prefixes after a locale change. New messages use the active locale; an old persisted runtime explanation can remain until a fresh event updates it.
- Foreground notifications, channel names/descriptions and tile text refresh on configuration changes. Notification/channel IDs, tile state cycling, executor commands, enum persistence names, database schema, delays and automation decisions remain unchanged.
- Installed-app names, command stdout/stderr, exception messages from external components, remote release notes and system-owned settings screens retain the text supplied by their source.

## Verification

Completed locally:

- `python3 scripts/check_localization.py`: all 535 resource keys match; formatting arguments match in all English plural forms; resource references are valid; English resources and main Kotlin/Java sources contain no Chinese text.
- Compared the default catalog against the original Kotlin inventory: all 481 original display strings are retained. The removed fragments were used only for decisions.
- `git diff --check`: passed.
- Parsed main and test Kotlin files with Tree-sitter Kotlin and compared against the original master: no new syntax errors. This is a syntax check, not Kotlin/Android compilation.

Added checks:

- JVM tests read the actual Chinese/English XML catalogs. Existing regression tests retain their Chinese expectations. New tests cover language changes, enum/command labels, permission kinds, language-independent skipped results, countdown plurals, historical probe labels, and custom-prompt persistence.
- Instrumentation tests exercise actual Android resource loading for notification/tile strings, quoted whitespace, Chinese fallback, and English singular/plural seconds.
- `.github/workflows/android.yml` runs catalog validation plus `assembleDebug`, `testDebugUnitTest` and `lintDebug`, and uploads reports.

The branch has been pushed and [draft PR #2](https://github.com/gao-gao-zai/SmartFlight/pull/2) is open. Gradle compilation, JVM tests and Android lint run in GitHub Actions; see the [PR checks](https://github.com/gao-gao-zai/SmartFlight/pull/2/checks) for the current result. The first CI attempt stopped during SDK setup because the action's default requested the removed `tools` package. The workflow now installs explicit platform and build-tools packages.

Local Gradle validation remains unavailable: this environment has no Android SDK, and wrapper bootstrap failed with `java.net.SocketException: Network is unreachable` while downloading Gradle 8.7. Instrumentation tests and the real-device review below have not been executed.

On a configured Android development machine:

```bash
python3 scripts/check_localization.py
bash gradlew assembleDebug testDebugUnitTest lintDebug
bash gradlew connectedDebugAndroidTest
```

## Real-device review

| Surface | Checks still required |
| --- | --- |
| First launch/setup | English and Chinese installations, denied/absent permissions, correct settings destinations and button actions; the existing usage-access requirement wording is intentionally preserved |
| Advanced access | Shizuku absent/not running/denied/authorized; Root absent/denied/timed out/successful; ADB copy/reset/mark-complete flows |
| Settings | Long English descriptions on narrow/watch screens, large fonts, all monitoring/executor choices, seconds and rule-preview plurals, blank built-in prompts and custom prompts |
| Foreground notification | Title/body and channel text before/after a system or per-app language change, while the service is running; existing channel preferences must remain intact |
| Quick Settings tile | All nine subtitles and the existing state cycle on Android/OEM variants; refresh after changing language; English truncation on compact tiles |
| Dashboard/diagnostics/logs | State probes, no-op/skipped results, failed actions, app-exit/screen-off countdowns, pause/restore flows and logs retained across a language change |
| About/update dialogs | Version text, checking/up-to-date/error/new-version dialogs, copy/open/skip actions and long externally supplied release notes |
| App scope/appearance | Filter menus, app rule menus, translated source tags, palette names, preview badges and large-font layout |

English layout, OEM-owned permission screens and live Shizuku/Root/network-control paths have not been verified on a device. Issue #1 remains open pending review and integration.
