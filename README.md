# Yukino Counter

一个 Windows 键盘按键计数器。默认常驻系统托盘，统计累计与每日按键次数，并用可定制的键盘热力图展示。

## 功能

- 全局键盘次数统计；不记录输入文本和按键顺序
- 累计总计、每日历史与键盘热力图
- 完整 104 键布局，区分数字小键盘与左右修饰键
- 鼠标移动距离统计（只读取坐标，不安装鼠标 Hook）
- 自定义热力颜色
- 背景图片、透明度、缩放和位置调节
- 导入 `KMCounter.ini` 历史数据，按文件内容防止重复导入
- 开机自启动、单实例运行、托盘后台运行与完整退出
- SQLite 本地保存，3 秒批量写入以减少磁盘开销

数据保存在 `%LOCALAPPDATA%\YukinoCounter\counts.db`，外观设置保存在当前 Windows 用户的 Java Preferences 中。

## 下载与运行

GitHub Release 提供两种 Windows x64 版本，均已包含私有 Java 运行环境，不需要另外安装 Java：

- `YukinoCounter-Portable-1.1.1.exe`：单文件便携版，双击即可运行；程序会在临时目录启动，退出后自动清理
- `YukinoCounter-1.1.1.exe`：正式安装包，支持选择安装目录，并创建桌面和开始菜单快捷方式

两种版本使用同一份用户数据目录 `%LOCALAPPDATA%\YukinoCounter`，因此可以互换使用，不会丢失已有统计。

## 构建

安装 JDK 21 与 Maven 3.9+，然后在 PowerShell 执行：

```powershell
.\scripts\build.ps1
```

输出位于 `dist\YukinoCounter-Windows-x64.zip`。解压后运行 `YukinoCounter.exe`；不要只复制 exe，旁边的 `app` 和 `runtime` 目录也是程序的一部分。

如需构建单文件便携版，可安装 MinGW-w64 与 7-Zip standalone (`7zr`)，然后执行：

```powershell
.\scripts\build-portable.ps1
```

便携版启动器使用 7-Zip/LZMA SDK 的 public domain `7zr` 解压组件。7-Zip 项目主页：<https://www.7-zip.org/>。

## 使用 KM Counter 历史数据

打开统计界面，点击“导入 KM 数据”，选择原来的 `KMCounter.ini`。软件导入日期分区中的逐键次数、每日按键总数和 `move` 鼠标距离；KM 未提供具体键位的少量差额只计入总数，不会错误染色到某个键。`[total]` 分区不会导入，避免与每日数据重复相加。同一个内容完全相同的文件只会导入一次；从 1.0 升级时可只补充鼠标距离，不重复累加按键。

## 安全提示

发布文件尚未使用商业代码签名证书，Windows 可能在首次运行时显示 SmartScreen 提示。程序只统计按键编号和次数，不保存输入文本、按键顺序或鼠标位置。

