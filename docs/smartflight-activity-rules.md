# Activity 级应用管理

## 使用入口

应用范围页保留应用默认联网/断网和快捷菜单，增加 Activity 规则数量、暂停/失效提示，以及“有 Activity 规则”筛选。点击应用进入详情；详情内可修改原有应用默认规则、暂停该应用的子规则、重新扫描、搜索完整类名或备注，筛选全部/已配置/已进入组件，并显示停用组件。

点击组件进入独立规则编辑页：选择“跟随应用”“联网”“断网”，填写最多 500 字的备注后保存。返回时有未保存改动会提示。备注只用于展示，匹配始终使用包名和完整 Activity 类名。

“记录 Activities”允许打开所选应用后记录，或手动打开无启动入口的应用。返回 SmartFlight 时结束记录；结果按本次首次进入时间排列，同一 Activity 合并展示。点击结果可编辑规则，记录本身不会修改规则。同一 Activity 内由 Fragment、Compose 或 WebView 切换的页面不另建规则。

## 匹配与优先级

- 使用精确组件身份，不使用模糊类名、界面文字或通配符。activity-alias 归到目标 Activity，共享配置。
- 启用且有效的 Activity 联网/断网规则可覆盖应用默认规则，包括应用黑名单；跟随应用保留原有自动识别/手动联网/手动断网语义。
- 全局自动化暂停、执行器可用性、Wi-Fi 例外、息屏规则和已有离开目标应用延迟仍由原引擎处理。Activity 分类不会绕过它们。
- 同一应用内 Activity 切换会重新评估；“暂停至下次应用切换”仍只由包名切换解除。
- 不知道类名、确认过期、子规则暂停、组件缺失/停用、扫描失败或应用卸载时，使用应用默认规则，并显示回退原因。
- 重复或较旧事件不会重复发命令或重启离开倒计时；重新进入联网范围会取消倒计时。

## 扫描、更新与保留

仅对已打开详情的应用维护 Activity 缓存，不全量扫描所有应用的全部组件。扫描包含非导出 Activity、停用组件和别名，显示版本、扫描时间和错误状态。

已跟踪应用在安装/更新/卸载及进程启动时重新核对组件。旧组件和规则保留；移除或改名后标为失效，不自动猜测新名称。重装后仅相同、有效的类名恢复匹配，同时保留复核提示。扫描异常保留旧列表和规则，但停止使用未能确认的子规则。

系统恢复事件发现尚未在声明缓存内的类名时，可以记录进入历史；创建覆盖规则前必须成功重新扫描并验证声明。应用停用、组件停用和卸载后的规则不会执行。

数据库升级到版本 4，新增组件、子规则、应用级开关和进入记录表。原应用规则、执行日志及设置保留，不使用破坏性迁移；新增日志字段允许为空，旧日志按原方式展示。

## 前台识别与诊断

使用情况访问读取 ACTIVITY_RESUMED 的包名、类名和实际恢复时间，按增量游标读取并保存全部恢复事件。后续非恢复事件不会伪造新的进入时间。无障碍窗口事件的类名必须能解析为有效声明 Activity；同应用的 Dialog/View 事件和未知 SystemUI 浮层不会覆盖已确认类名。

自动模式在有启用且有效的子规则或正在记录时，补充使用情况访问确认；没有子规则时保留原无障碍快速路径。仅无障碍模式不偷偷使用 UsageStats。服务允许 Android 系统绑定，并继续由 BIND_ACCESSIBILITY_SERVICE 签名权限保护；不请求读取窗口内容。记录会按当前监测模式核对权限。

诊断显示“最近确认”的组件、来源、事件时间和匹配/回退原因，不承诺缓存是此刻正在显示的界面。确认有效期为 30 秒；权限消失或来源切换清除可信状态。运行时诊断及新执行日志包含 Activity、匹配层级和原因。

## 验证与真机复核

JVM 回归覆盖精确匹配、默认语义、黑名单覆盖、暂停/失效/过期回退、自动模式补充识别、同包切换和倒计时去重。Android 集成测试使用真实 Room 数据库和迁移，另安装测试 APK，包含普通 Activity、非导出、停用、别名及升级后替换组件。

模拟器测试验证真实扫描、安装更新通知触发失效、UsageEvents 同包切换、原始恢复时间、真实无障碍服务的窗口切换与原生 Dialog 排除，以及从记录到编辑保存、备注保留、未保存返回提示的流程。Compose 测试生成中英文及 240dp/大字体布局截图。并发 DataStore 写入测试检查前台身份与其他运行字段不会互相覆盖。

仍需真机复核：各厂商无障碍事件与后台限制、锁屏/解锁、分屏/画中画、快捷设置/通知遮罩、权限中途撤销及恢复、无启动入口应用、实际小屏/圆屏设备，以及 Root/Shizuku/ADB 下真实联网动作和延迟。模拟器 UI 和识别测试不能证明 OEM 后台行为或执行器在用户设备上的表现。

## 快捷声明磁贴与第三方接口

在系统快捷设置编辑页添加 **快捷声明** 磁贴。停留在目标应用时点击磁贴，选框会显示目标应用和可确认的完整 Activity 名称。选择 **整个应用** 或 **这个 Activity**，然后选择 **联网 / 不联网 / 自动**，点击保存。取消、返回或点击选框外部都不会改变规则。

- 整个应用的自动：清除应用手动声明，恢复原有的联网自动判断；不清除该应用已有的 Activity 子规则。
- Activity 的自动：恢复跟随应用。原 Activity 编辑界面显示为 **自动（跟随应用）**，已有备注继续保留。
- 无法验证 Activity、组件停用或扫描失败时，禁用 Activity 范围，仍可修改整个应用。
- 保存会核对安装状态与组件有效性；全局自动化暂停或应用子规则暂停不会被这个入口解除，选框会提示。
- 选框是短暂的透明 Activity，不需要悬浮窗权限；前台监测忽略它，不会因打开选框把目标应用误判为离开。
- 磁贴在锁屏时先请求解锁。第三方不能通过这个入口在锁屏上修改规则。

第三方（例如 Tasker、快捷方式工具）通过 **启动 Activity** 调用。支持显式组件或下面的 action；不接受静默写规则的参数。调用后必须由用户在选框中选择并保存。

| 参数 | 值 / 含义 |
| --- | --- |
| Action | `com.gaozay.smartflight.action.QUICK_RULE` |
| Package | `com.gaozay.smartflight` |
| Class | `com.gaozay.smartflight.quickrule.QuickRuleActivity` |
| `package_name`（String，可选） | 指定已安装应用的包名。未指定则识别打开选框前的前台应用 |
| `activity_name`（String，可选） | Activity 完整类名，也接受相对类名；必须同时提供包名。组件需要通过声明和启用状态验证 |

调用示例（只打开选框，仍需点击保存）：

```sh
# 针对当前前台应用
adb shell am start -a com.gaozay.smartflight.action.QUICK_RULE -p com.gaozay.smartflight

# 针对调用者指定的应用 / Activity
adb shell am start -n com.gaozay.smartflight/.quickrule.QuickRuleActivity \
  --es package_name com.example.app \
  --es activity_name com.example.app.MainActivity
```

Android 调用：

```kotlin
val intent = Intent("com.gaozay.smartflight.action.QUICK_RULE")
    .setPackage("com.gaozay.smartflight")
    .putExtra("package_name", "com.example.app")
    .putExtra("activity_name", "com.example.app.MainActivity")
startActivity(intent) // 从非 Activity Context 发起时添加 FLAG_ACTIVITY_NEW_TASK
```

Android 后台启动限制仍适用，建议从用户点击的按钮、快捷方式或通知启动。若使用 Activity Result，保存成功返回 `RESULT_OK`，取消或未保存返回 `RESULT_CANCELED`。未指定目标时遵循用户配置的监测模式；只有无障碍模式不会调用 UsageStats。双窗口或画中画的当前焦点、MIUI 磁贴解锁和面板收起效果仍需真机复核。
