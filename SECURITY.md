# Security Policy

## 报告问题

请通过 GitHub 的 **Private vulnerability reporting** 或 Security Advisory 联系维护者，不要在公开 Issue 中贴出可利用细节、API Key 或个人信息。

## 范围

本应用使用无障碍服务和悬浮窗，属于高敏感权限组合。以下问题尤其重要：

- 任意应用可触发截屏或读取屏幕内容
- 广播/组件导出导致的权限绕过
- API Key 泄露
- 本地知识库路径穿越
- 恶意构造的题目文本导致的注入或崩溃
- 供应链/依赖漏洞

## 不在范围内

- 需要 root、解锁 Bootloader 或物理接触设备的攻击
- 用户自行安装的第三方修改版 APK
- DeepSeek API 本身的安全问题（请联系 DeepSeek）
- 社会工程或用户主动泄露 Key

## 已知限制

- `com.dsh.screenqa.CAPTURE` 是一个导出广播，用于让 TapTap 补丁触发识题。
  - **Android 14 及以上**：1.0.1 起只接受来自 `com.kieronquinn.app.taptap` 的发送方，并限制 2 秒冷却、每分钟最多 6 次。
  - **Android 11–13**：系统没有提供广播发送方身份，任何应用都可以触发。请勿在装有不可信应用的设备上开启无障碍。
- `SettingsActivity` 从 1.0.1 起不再导出，只能从应用内部打开。

## 发布校验

正式 Release 会提供 APK 的 SHA-256 校验和。请只从本仓库的 Releases 下载。

## 签名

- 官方构建使用维护者持有的 release keystore。
- 仓库不包含任何 keystore、密码或 API Key。
- 如果签名发生变化，请视为不同应用，先卸载旧版再安装。

## 免责声明

本项目按“现状”提供，不提供任何担保。使用本软件造成的成绩、账号、数据或设备问题由使用者自行承担。
