# SmartFlight 本机环境文档

本文档只记录当前项目在本机开发、编译、调试时需要直接使用的环境信息，避免关键信息散落在其他设计或技术说明文档中。

## 1. Java 环境

- JDK 路径：`F:\GAOZAY\AndroidSDK\jdk-17`
- 已确认版本：`OpenJDK 17.0.19`

### 1.1 当前终端临时配置

如果当前终端还没有配置 `JAVA_HOME`，可先在 PowerShell 中执行：

```powershell
$env:JAVA_HOME='F:\GAOZAY\AndroidSDK\jdk-17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

### 1.2 验证命令

```powershell
java -version
./gradlew.bat :app:compileDebugKotlin
```

## 2. 项目路径

- 项目根目录：`F:\GAOZAY\SmartFlight`
- 文档目录：`F:\GAOZAY\SmartFlight\docs`

## 3. 本次已验证结果

已使用以下环境完成编译验证：

- `JAVA_HOME=F:\GAOZAY\AndroidSDK\jdk-17`
- 编译命令：`./gradlew.bat :app:compileDebugKotlin`
- 结果：`BUILD SUCCESSFUL`

## 4. 维护约定

- 后续如果 JDK、Android SDK、Gradle 运行方式发生变化，优先更新本文档。
- 其他文档可以引用本文档，不再重复维护同一份环境路径。
