# 架构说明

## 组件

| 组件 | 作用 |
| --- | --- |
| `TriggerActivity` | 应用图标入口；透明 Activity，触发截屏后结束 |
| `CaptureReceiver` | 接收 TapTap 广播 `com.dsh.screenqa.CAPTURE`，延迟 450ms 后截屏 |
| `ScreenQaTileService` | 控制中心快捷开关 |
| `ScreenCaptureService` | 无障碍服务；`takeScreenshot()`；读取当前窗口文本 |
| `QuestionProcessor` | 题型判断、题库匹配、资料检索、模型选择、请求序号控制 |
| `LocalKnowledge` | 读取 `.txt`，解析题库，切段与关键词检索 |
| `DeepSeekClient` | OpenAI 兼容的 `/chat/completions` 请求 |
| `OverlayManager` | 顶部胶囊与展开卡片 |
| `SettingsActivity` | API Key、模型、知识库导入、权限入口 |

## 触发流程

```text
TapTap 双击
  → LaunchAppAction 发送广播
  → CaptureReceiver
  → 450ms 延迟
  → ScreenCaptureService.capture()
  → QuestionProcessor.process()
```

应用图标和快捷开关走相似路径，但不经过广播。

## 文本来源

1. 优先 `AccessibilityNodeInfo` 树：`ScreenCaptureService.extractText()`。
2. 文本不足时，回退 ML Kit 中文 OCR（裁剪掉状态栏/导航栏）。

这样能避免 OCR 把选项字母、标点、专有名词认错。

## 题型与模型

`QuestionProcessor.shouldUseReasoner()`：

- 含“选择题/单选题/多选题/判断题”或至少两个 A/B/C/D 选项 → `deepseek-chat`
- 含“填空/简答/论述/名词解释”或 `____` → `deepseek-reasoner`
- 其余 → `deepseek-chat`

`DeepSeekClient` 对 `reasoner` 不发送 `temperature` 参数；调用失败时回退 `chat`。

## 并发与“上一题”问题

- 每次 `process()` 生成递增 `requestId`。
- 只有 `requestId == LATEST_REQUEST` 的回调才能更新胶囊。
- 旧请求晚到的结果会被丢弃，避免覆盖当前题。
- 触发后延迟 450ms，等待 WebView/无障碍树稳定。

## 悬浮窗

`OverlayManager` 使用 `TYPE_APPLICATION_OVERLAY`：

- 顶部居中、不可聚焦、不拦截触摸。
- 胶囊只显示 `答案：` 行。
- 点击展开完整解析，带关闭按钮。

## 隐私边界

- 图片只在内存中处理。
- 题目文字发送到用户配置的 API。
- 本地知识库不离开设备；命中片段会随请求发送。
