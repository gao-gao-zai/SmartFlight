# SmartFlight 技术栈与环境文档

## 1. 项目结论

SmartFlight 适合采用原生 Android 技术栈开发，不建议使用 Flutter、React Native 等跨端方案作为首选。

原因：

- 该应用重度依赖 Android 系统能力和权限模型
- 涉及前台服务、广播、使用情况访问、高权限执行链路
- 需要长期维护复杂的后台运行与系统兼容逻辑
- 原生 Kotlin 与 Jetpack 生态在这类工具型应用上更稳

当前产品定位为仅服务具备高级权限能力的用户，因此项目从第一版开始就必须以内建高级执行链路为前提，而不是把高级模式作为后补功能。

## 2. 推荐技术栈

### 2.1 开发语言

- `Kotlin`

选择原因：

- Android 一线开发语言
- 与 Jetpack 生态结合最好
- 适合协程、Flow、现代架构模式

### 2.2 UI 层

- `Jetpack Compose`
- `Material 3`

选择原因：

- 便于构建现代化、多页、动态主题界面
- 状态驱动 UI，更适合规则型工具应用
- 适合快速搭建主题切换和深浅色支持

### 2.3 架构模式

- `Single-Activity`
- `MVVM`
- `ViewModel + StateFlow`
- `Navigation Compose`

选择原因：

- 结构清晰
- 页面状态集中管理
- 易于测试和扩展

### 2.4 依赖注入

- `Hilt`

选择原因：

- 适合注入仓库、扫描器、执行器、服务协作者
- 与 Android 官方架构契合

### 2.5 本地存储

- `Room`
- `DataStore`

职责划分建议：

- `Room`：应用实体、白名单、黑名单、扫描结果、日志
- `DataStore`：主题、模式、延迟秒数、总开关、偏好项

### 2.6 后台与系统能力

- `Foreground Service`
- `BroadcastReceiver`
- `WorkManager`
- `UsageStatsManager`
- `PackageManager`
- `ConnectivityManager`
- `WifiManager`
- 蓝牙状态管理 API

职责划分建议：

- `Foreground Service`：维持核心监听和规则执行主循环
- `BroadcastReceiver`：处理亮屏、息屏、开机、安装卸载等系统事件
- `WorkManager`：做补偿恢复和低实时后台任务
- `UsageStatsManager`：识别前台应用
- `PackageManager`：扫描应用、权限和清单信息
- `ConnectivityManager`：判断当前网络连接状态，识别 Wi-Fi 环境
- `WifiManager` / 蓝牙状态管理 API：读取并恢复 Wi-Fi、蓝牙状态

## 3. 模块划分建议

建议至少按以下逻辑拆分：

- `app`
- `core-ui`
- `core-data`
- `core-domain`
- `feature-home`
- `feature-apps`
- `feature-rules`
- `feature-theme`
- `feature-diagnostics`
- `feature-permissions`
- `service-runtime`
- `executor`

如果首版想降低复杂度，也可以先用单模块应用，但代码层仍应保留以下分层：

- `ui`
- `domain`
- `data`
- `service`
- `receiver`
- `executor`
- `permission`
- `di`

## 4. 关键核心组件

### 4.1 Rule Engine

负责输入规则条件并给出动作结论。

输入：

- 屏幕状态
- 当前前台应用
- 白名单 / 黑名单
- 候选联网应用列表
- 用户设置
- 当前是否连接可用 Wi-Fi
- 当前 Wi-Fi 状态
- 当前蓝牙状态

输出：

- 恢复联网
- 延迟断网
- 保持当前状态

### 4.2 App Scanner

负责扫描候选联网应用。

建议依据：

- 是否声明 `INTERNET` 权限
- 是否有 Launcher 入口
- 是否为用户应用

### 4.3 Runtime Service

负责保活、监听状态变化、触发规则引擎和执行器。

### 4.4 Executor Layer

负责真正执行联网控制动作。

建议接口：

- `ConnectivityExecutor`
- `AirplaneModeExecutor`
- `MobileDataExecutor`
- `StatePreservationCoordinator`

建议实现：

- `ShizukuExecutor`
- `AdbBootstrappedExecutor`
- `RootExecutor`

## 5. 权限与能力说明

### 5.1 普通公开权限与特殊授权

SmartFlight 至少会涉及以下能力：

- 通知权限
- 前台服务
- 使用情况访问权限
- 忽略电池优化引导

### 5.2 高级执行能力

以下能力不能默认假设普通用户已经具备：

- 静默开关飞行模式
- 静默开关移动数据
- 在联网切换过程中保留 Wi-Fi 状态
- 在联网切换过程中保留蓝牙状态

这些能力是项目的基本前提，应通过执行器隔离，并在应用启动时先验证高级能力是否可用。若不可用，则不提供核心服务。

### 5.3 启动门槛

建议把高级权限检查做成首启门槛。

用户至少需要满足以下其一：

- 已配置可用的 Shizuku
- 已完成可用的 ADB 初始化
- 已授予 Root 权限

并且还需要满足：

- 已授权使用情况访问权限

若以上条件均不满足：

- 不进入核心功能页
- 仅展示环境检查与接入引导

## 6. 主题系统设计建议

主题系统建议包含：

- 跟随系统
- 浅色
- 深色
- 默认品牌主题
- 若干备选主题

默认品牌主题建议取自现有 Logo 主色，整体风格要保持克制、清晰、工具化，不要做成游戏化界面。

第一版建议明确为：

- 固定 5 套主题
- 不支持任意自定义取色
- Logo 主色主题作为默认主题

## 7. 数据模型建议

### 7.1 应用实体

建议字段：

- 应用名
- 包名
- 图标缓存键
- 是否系统应用
- 是否声明联网权限
- 是否有 Launcher 入口
- 是否为候选联网应用
- 是否白名单
- 是否黑名单
- 是否忽略
- 最后扫描时间

### 7.2 用户设置

建议字段：

- 总开关
- 联网控制模式
- 首选执行器
- 息屏延迟秒数
- 离开目标应用延迟秒数
- 是否只响应白名单
- 是否启用息屏自动断网
- 是否在 Wi-Fi 环境下不自动执行恢复联网动作
- 是否在 Wi-Fi 环境下不自动执行断网动作
- 是否保留 Wi-Fi 状态
- 是否保留蓝牙状态
- 是否在亮屏后不自动恢复联网
- 是否在解锁后不自动恢复联网
- 主题模式
- 主题配色

### 7.3 日志实体

建议字段：

- 时间戳
- 触发来源
- 前台应用
- 命中的规则
- 动作类型
- 执行方式
- 执行结果
- 错误信息

## 8. 规则实现补充

### 8.1 Wi-Fi 环境判断

建议以“当前是否连接可用 Wi-Fi 网络”为准，而不是：

- 仅 Wi-Fi 开关打开
- 仅存在历史已保存网络

### 8.2 Wi-Fi 环境例外

规则引擎应支持：

- 在 Wi-Fi 环境下不自动执行恢复联网动作
- 在 Wi-Fi 环境下不自动执行断网动作

### 8.3 Wi-Fi / 蓝牙状态保留

执行器在切换飞行模式或移动数据前后应具备以下流程：

1. 读取当前 Wi-Fi 状态
2. 读取当前蓝牙状态
3. 执行目标联网动作
4. 根据用户设置决定是否恢复 Wi-Fi 状态
5. 根据用户设置决定是否恢复蓝牙状态

该能力应抽象成独立协作组件，避免散落在不同执行器实现中。

## 9. 测试建议

### 8.1 单元测试

- 规则引擎测试
- 黑白名单逻辑测试
- 扫描规则测试
- Wi-Fi 环境例外测试
- 状态保留逻辑测试

### 8.2 UI 测试

- 多页导航
- 应用列表筛选
- 添加删除白名单黑名单
- 主题切换
- 高级权限门槛页
- Wi-Fi / 蓝牙保留设置项

### 8.3 真机测试

必须重点覆盖：

- 息屏与亮屏
- 前台应用切换
- 安装与卸载应用
- 前台服务保活
- 不同执行器的可用性
- 飞行模式切换前后的 Wi-Fi 状态恢复
- 飞行模式切换前后的蓝牙状态恢复
- 移动数据模式下的 Wi-Fi / 蓝牙规则一致性

## 10. 本机环境详情

当前已确认环境如下。

### 10.1 工作目录

- 项目目录：`F:\GAOZAY\SmartFlight`

### 10.2 现有素材

- `logo.png`
- `logo.svg`

这两个素材可用于：

- 应用图标导出
- 启动画面
- 品牌主题色提取

### 10.3 Android SDK

- SDK 根目录：`F:\GAOZAY\AndroidSDK`
- 已安装平台：`android-35`
- 已安装 Build Tools：`34.0.0`、`35.0.1`
- 已安装组件目录包括：
  - `build-tools`
  - `cmdline-tools`
  - `jdk-17`
  - `licenses`
  - `platform-tools`
  - `platforms`

### 10.4 Java 环境

- JDK 路径：`F:\GAOZAY\AndroidSDK\jdk-17`
- 已确认版本：`OpenJDK 17.0.19`

### 10.5 推荐构建基线

建议基线如下：

- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk` 可在项目创建时再根据目标用户范围决定
- `Java/Kotlin toolchain = 17`

## 11. 创建项目时的建议

建议项目初始化时采用以下方向：

- 使用 Kotlin DSL
- 启用 Compose
- 使用版本目录 `libs.versions.toml`
- 先建立基础分层和执行器接口，不要后补
- 首次启动先做高级权限能力检查
- 首次启动同时检查使用情况访问权限
- 在第一天就把日志和诊断页纳入项目结构

## 12. 当前建议结论

SmartFlight 应采用：

- 原生 Android
- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Hilt
- Room
- DataStore
- Foreground Service
- BroadcastReceiver
- WorkManager

并从架构上明确区分：

- 高级权限门槛
- 执行器抽象
- Wi-Fi / 蓝牙状态保留
- Wi-Fi 环境例外规则
