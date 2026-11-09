# WordFlow 固定签名（仅供个人保存）

## 为什么不能直接复用以前的 Debug APK 签名？

旧版 GitHub Actions 使用每台云构建机自动生成的 Debug 证书。各次构建可能不一致，Android 因此拒绝覆盖升级。新方案使用独立的长期密钥，仅保存在你的私有备份与 GitHub Actions Secret 中。公开仓库只存储证书指纹，**绝不提交私钥或密码**。

## 手机上一次性配置

1. 安全保存本次提供的 `WordFlow-v1-private-signing-key.zip`，以及 `WordFlow-v1-GitHub-Secret.txt`。这两份内容都必须保密；不要贴入 Issues、PR、README 或聊天截图。
2. 在手机浏览器打开仓库 `07xh/wordflow` → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`。
3. Secret 名称填写 `WORDFLOW_SIGNING_BUNDLE`，值为本次提供的 `WordFlow-v1-GitHub-Secret.txt` 的**整段 Base64 字符串**（不要增加空格）。
4. 保存后在 `Actions` → `WordFlow Android APK` → `Run workflow` 触发一次构建。
5. 成功后，构建产物中会额外出现 `WordFlow-v0.5.0-stable-signed`，里面的 `app-release.apk` 使用固定签名。后续版本继续用同一 Secret，才可以覆盖升级。

## 重要说明

- 旧版 `v0.4.0` Debug 安装包和新的固定签名 APK **签名不同，不能直接覆盖安装**。旧版没有全量备份功能，所以更新前应至少通过旧版 CSV 导出词条；CSV 不包含复习进度，不可用来完整迁移。
- `v0.5.0` 完整备份功能在未来固定签名版本之间迁移时可保留学习记录。第一次从旧 Debug 迁移时，原有复习记录没有安全的应用内导出方案；切勿在没有备份的情况下直接卸载。
- ZIP 和 Base64 文本都包含相同的私钥，不代表加密保护。请放到自己能够长期访问、不会公开共享的安全位置。
- 私钥丢失后无法使用原签名更新；如果密钥意外公开，应该停止用该签名分发和安装 APK。
- GitHub Secret 只为后续构建提供私钥，并不是私钥的备份。必须另外离线保存 ZIP。
- CI 只有在 Secret 正确配置且证书 SHA-256 与 `SIGNING_CERT_SHA256.txt` 一致时才发布带固定签名的安装包。否则只产生普通 Debug APK。
