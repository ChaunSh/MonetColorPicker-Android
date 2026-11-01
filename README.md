# MonetColorPicker Android

独立运行的 Android Monet 配色管理工具，适用于 Android 11（API 30）及以上。界面使用固定浅色 Material Design 3；配色计算、预览、文件管理与系统写入均在手机本地完成。

## 使用

“设置配色”页面支持读取系统主屏幕壁纸、选择图片并使用内置裁切器、提取候选主色、输入 Seed Color、选择主题样式、预览五组 65 色，以及导入和导出配色。“当前配色”页面从系统实际资源读取颜色和主题参数，可以刷新和导出系统快照。图片、裁切、颜色计算和导入不会自动写入系统；只有点击“应用到系统”才会执行写入。

外部图片使用系统文件选择器选择，在应用内按屏幕物理像素的竖屏比例裁切，超过屏幕尺寸时等比缩小，不放大小图。系统壁纸直接读取和分析，不经过该裁切流程。提取颜色时使用临时缩小的副本，不改变输入图像。

## 壁纸访问权限

在 Android 11 及以上读取系统静态壁纸需要系统允许应用访问壁纸。Android 13 及更高版本可通过“所有文件访问权限”进行访问：在应用中点击“读取当前壁纸”，按提示前往系统设置并自行授权。此权限的范围比壁纸更广，可访问大量共享存储文件；不授权仍可选择本地图片并使用其他功能。访问受设备厂商实现影响，动态壁纸、受保护壁纸或特别大的壁纸可能无法读取。

## 系统配色写入权限

首次应用系统配色前，连接已授权 USB 或无线调试的电脑，执行：

```sh
adb shell pm grant com.wallpapermonet.companion android.permission.WRITE_SECURE_SETTINGS
```

授权后日常使用无需连接电脑、Root 或 Shizuku。没有授权时仍可处理图片和预览。应用读取 `theme_customization_overlay_packages`，保留无关字段并修改配色字段；写入之前在私有存储中备份完整原值，写入后回读验证。无效的非空原始 JSON 会阻止写入。恢复时需要确认，并会先备份恢复前的值。卸载或清除应用数据会删除私有备份。写入成功不代表系统颜色资源已立即刷新。

## JSON 配置

文件使用 UTF-8 JSON：`format` 为 `MonetColorPicker`，`schemaVersion` 为 1，`type` 为 `editable-theme` 或 `system-snapshot`。包含 Seed Color、Theme Style、Color Source、导出时间和五组 `palette` 数组；每组按 Android 资源色阶 0、10、50、100、200、300、400、500、600、700、800、900、1000 排列 13 项。系统快照中不可用资源以 null 表示。只有包含有效主色和样式的快照才能重建编辑主题；快照不是逐色写入系统的指令。导入上限为 1 MB。

## 构建与安装

需要 JDK 17、Android SDK 36，以及 Gradle Wrapper 所需的依赖。在工程根目录执行：

```sh
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 使用 `gradlew.bat`。Debug APK 使用调试签名，不是正式签名的发布版本。不同签名的同包名应用不能直接覆盖安装；卸载旧应用会删除其私有数据和备份。

## 兼容性与算法

Android 11 不一定提供动态色资源，缺失资源会显示“不可用”。系统可能拒绝壁纸读取、系统设置写入，或延迟更新颜色；HyperOS 和其他厂商实现可能不同。动态壁纸没有保证可供读取的静态图。应用不会用截图替代壁纸。

图像颜色使用 Google Material Color Utilities 的 Celebi、Score、HCT 和 Tonal Palette，基于 2021 配色参数生成调色板；本地计算可能因系统版本和实现差异而不同于系统实际颜色。`WRITE_SECURE_SETTINGS` 是受保护权限，Android lint 会报告 `ProtectedPermissions`，需通过上述 ADB 授权。

## 来源与许可证

本工程基于 [ChaunSh/MonetColorPicker](https://github.com/ChaunSh/MonetColorPicker) 的 Android 工程及配色写入逻辑，参考提交 `04c516d8c9cbd248a0d7837eba8f8bd0f3c8fe0a`。该上游提交未包含许可证文件，不能据此推定其开源许可证。Google Material Color Utilities Java 源码按 Apache License 2.0 引入并调整包名，版权和许可证见 `licenses/`；AndroidX 与 Material Components 依赖遵循各自许可证。
