# SmartFlight / 自动飞行

SmartFlight 是一个面向 Android 高级用户的规则驱动联网控制工具。应用会结合前台应用、息屏状态、Wi-Fi 环境和用户规则，自动执行“恢复联网”或“断网”动作，以降低后台联网和待机消耗。

当前项目基于原生 Android 技术栈实现，重点支持 `Shizuku`、`ADB 初始化`、`Root` 这三类高级执行链路，不提供普通权限下的弱化模式。

## 当前能力

- 接入检查页：检测使用情况访问、通知权限、电池优化，以及 Shizuku / ADB / Root 可用性
- 自动化控制台：展示当前模式、执行器、前台应用、最近动作和规则解释
- 应用范围管理：扫描已安装应用，并支持联网列表、白名单、黑名单的手动调整
- 自动化规则配置：支持息屏断网、离开目标应用断网、Wi-Fi 例外、Wi-Fi / 蓝牙状态保留等规则
- 诊断与日志：查看执行器状态、统一网络状态、最近执行日志，并提供手动探测/切换操作
- 外观设置：支持主题模式、配色、强度和圆角风格调整

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- MVVM + `StateFlow`
- Hilt
- Room
- DataStore
- Foreground Service
- Shizuku API

## 运行前提

SmartFlight 的核心功能依赖 Android 的高权限执行能力。要进入完整功能页，至少需要满足以下条件：

- 已具备一种高级执行方式：`Shizuku`、`ADB 初始化` 或 `Root`
- 已授权“使用情况访问权限”

建议同时完成以下授权，以保证后台自动化稳定运行：

- 通知权限
- 忽略电池优化
- 蓝牙状态读取权限（Android 12+）

## 开发环境

- JDK 17
- Android SDK 35
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`

## 构建与测试

在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew test
```

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

如果需要发布签名包，项目会在根目录存在 `keystore.properties` 时自动加载本地签名配置。

## 项目结构

```text
app/src/main/java/com/gaozay/smartflight
├─ apps          已安装应用扫描、筛选与联网状态管理
├─ data          Room 数据库、DAO、实体
├─ di            Hilt 依赖注入
├─ domain        领域模型
├─ executor      Shizuku / ADB / Root 执行器与探测逻辑
├─ logs          执行日志仓储
├─ permission    接入检查与高级能力探测
├─ runtime       自动化前台服务、规则引擎、运行时状态
├─ settings      DataStore 设置存储
├─ shizuku       Shizuku 服务接入
└─ ui            Compose 页面与主题
```

## 仓库内文档

`docs/` 目录保留了产品和实现设计文档，建议按这个顺序阅读：

1. `docs/smartflight-product-design.md`
2. `docs/smartflight-implementation-overview.md`
3. `docs/smartflight-tech-stack-and-environment.md`

## 当前状态

该仓库已经包含可运行的 Android 工程骨架，以及接入检查、规则配置、应用扫描、运行时诊断和自动化服务主链路。不同厂商系统上的实际联网控制效果，仍然依赖设备版本、执行器能力和系统限制，需要继续做真机验证。
