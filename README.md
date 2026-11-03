# ScreenQA

一个 Android 屏幕识题助手：双击手机背面（或点快捷开关）后，读取当前屏幕的题目，调用 DeepSeek 给出答案，并在顶部胶囊里显示结果。

> 仅用于学习、复习和自测。请勿用于考试、测评或其他需要独立完成的场合。

## 功能

- 静默截屏：使用 Android 无障碍服务的 `takeScreenshot()`，不弹系统截图预览。
- 优先读取无障碍节点文本：WebView 题目直接拿到准确题干/选项；读不到时回退 ML Kit 中文 OCR。
- 按题型分流模型：
  - 选择题 / 判断题 → `deepseek-chat`
  - 填空题 / 简答题 / 论述题 → `deepseek-reasoner`
  - `reasoner` 失败时自动回退 `chat`
- 顶部胶囊只显示 `答案：...`，点一下展开完整解析。
- 本地知识库：
  - `题目|答案` 形式的 `.txt` 作为题库，命中后直接返回，不调用模型。
  - 普通 `.txt` 作为资料，按关键词检索相关片段，拼进提示词（RAG）。
- 支持双击背面触发（需要配套的 TapTap 补丁）或控制中心快捷开关。

## 环境要求

- Android 11（API 30）及以上，因为使用 `AccessibilityService.takeScreenshot()`。
- 已在 Android 17 / HyperOS 4.0 / Redmi K80 上验证；其他 ROM 可能需要调整后台保活设置。
- JDK 21、Android SDK 35、Gradle 8.9（仓库自带 Gradle Wrapper）。

## 构建

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

注意：默认 `release` 在没有 `keystore.properties` 时使用 debug 签名，方便本地构建。正式发布请复制 `keystore.properties.example` 为 `keystore.properties`，填入自己的 keystore 信息；构建会自动使用 release 签名。

## 安装与权限

1. 安装 APK。
2. 打开 ScreenQA，填写：
   - DeepSeek API Key
   - 选择题模型（默认 `deepseek-chat`）
   - 填空/简答模型（默认 `deepseek-reasoner`）
   - API 地址（默认 `https://api.deepseek.com`）
3. 授予悬浮窗权限。
4. 在系统设置 → 无障碍中启用 **ScreenQA**。
5. 将 ScreenQA 电池策略设为“无限制”，避免被 MIUI/HyperOS 冻结。

更完整的步骤见 [docs/INSTALL.md](docs/INSTALL.md)。

## 双击背面触发

Android 没有给普通应用开放背部双击传感器。要让“双击背面”生效，需要配合 TapTap 的一个非官方补丁：

- 仓库：[TapTap-ScreenQA-Patch](https://github.com/EEElysia1111/TapTap-ScreenQA-Patch)
- 先卸载官方 TapTap，再安装按补丁构建的 TapTap。
- 双击动作设置为“启动应用 → ScreenQA”。

如果不使用 TapTap，也可以在控制中心添加 **“ScreenQA 识题”** 快捷开关，或直接点应用图标。

## 本地知识库

在 ScreenQA 设置页点 **“导入知识库 .txt”**，选择手机上的文本文件。

题库格式（一行一题）：

```text
题目|答案
题目<Tab>答案
题目=>答案
题目 答案：答案
```

普通文本会按约 500 字切段，识别题目后检索最相关的片段。

详见 [docs/KNOWLEDGE_BASE.md](docs/KNOWLEDGE_BASE.md)。

## 工作流程

```text
TapTap 双击 / 快捷开关 / 应用图标
        ↓
CaptureReceiver / TileService / TriggerActivity
        ↓
ScreenCaptureService（无障碍截屏）
        ↓
QuestionProcessor
  ├─ 无障碍节点文本（优先）
  ├─ ML Kit 中文 OCR（回退）
  ├─ LocalKnowledge 题库匹配 / 资料检索
  └─ DeepSeekClient（chat 或 reasoner）
        ↓
OverlayManager（顶部胶囊 / 展开卡片）
```

## 权限

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 调用 DeepSeek API |
| `ACCESS_NETWORK_STATE` | 网络状态 |
| `SYSTEM_ALERT_WINDOW` | 显示顶部胶囊 |
| 无障碍服务 | 读取当前界面文本、静默截屏 |

没有申请存储、相册、通讯录、定位、摄像头、麦克风权限。

## 隐私

- 截图只在本地内存中处理，不上传、不保存到相册。
- OCR 出的题目文字会发送到用户配置的 DeepSeek API 地址。
- 本地知识库只存在手机本地；只有命中的片段会随题目发送给 API。
- API Key 存在应用私有的 `SharedPreferences`，未加密；root 设备或备份可能读取。
- 没有统计、埋点或广告 SDK。

详见 [PRIVACY.md](PRIVACY.md)。

## 已知限制

- 只支持 Android；iOS 不可用。
- 复杂公式、图形题、纯图片题 OCR 效果有限。
- 地方政策、时政等模型本身不知道的知识，正确率取决于模型；可导入资料或使用联网搜索。
- MIUI/HyperOS 可能冻结后台服务；需要电池无限制、自启动，必要时锁定最近任务。
- 无障碍文本在页面切换后可能短暂滞后，代码里已加延迟与“只允许最新请求更新”的保护。

## 安全

无障碍 + 悬浮窗属于高敏感权限组合。请只安装可信来源的 APK，并自行审查源码。

安全问题请见 [SECURITY.md](SECURITY.md)。

## 许可证

本项目使用 **GNU General Public License v3.0**。详见 [LICENSE](LICENSE)。

你可以修改、分发，但衍生作品必须同样以 GPL-3.0 开源，并提供完整对应源码。

## 致谢

- [TapTap](https://github.com/KieronQuinn/TapTap)（GPL-3.0）：背部手势识别。
- [ML Kit](https://developers.google.com/ml-kit)：中文文字识别。
- [DeepSeek](https://www.deepseek.com/)：题目作答 API。

本项目与 TapTap、DeepSeek 官方均无隶属关系。
