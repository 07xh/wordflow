# 词序 · WordFlow v0.4.0

自用、离线优先的 Android 通知背词应用。通过独立实现复现 FluidWords 的可观察行为，不包含其 APK 的原始代码或资源。

## 功能
- SQLite 本地词库；添加、修改、搜索、删除；TXT、CSV 导入与 CSV 导出
- 通知背词：不会、记住、下一词三个快捷操作；旧通知令牌保护
- 顺序、随机、难词优先，10–300 秒轮换
- 英文、中文、英中同时、先英文后显示中文四种模式
- 首页查看当前单词与快速切换
- 简化间隔复习，不宣称实现 FSRS
- 无云端账号、无联网权限

## 构建
GitHub Actions 在推送后执行源码检查、Gradle 单元测试和 Debug APK 编译，并提供下载 Artifact。工作流：`.github/workflows/build.yml`。

当前未完成真机适配验收；Android 官方 Live Updates 和 OPPO/星河岛尚未接入，普通通知不保证上岛。Debug APK 的 CI 签名可能随每次构建发生变化，升级之前请先导出词库（当前 CSV 不包含复习历史）。

## 固定签名与完整备份

自 v0.5.0 起，设置页提供包含学习状态的完整 JSON 备份/恢复。长期更新签名需配置仓库 Actions Secret `WORDFLOW_SIGNING_BUNDLE`；参见 [SIGNING_SETUP.md](SIGNING_SETUP.md)。私钥不包含在公开仓库中。尚未配置 Secret 时，构建输出的普通 Debug APK 可能无法覆盖其他 CI 构建。
