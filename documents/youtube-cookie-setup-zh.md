# YouTube Cookie 配置指南

## 问题描述

当您部署项目后无法正常下载 YouTube 节目，并在服务日志中看到以下错误信息时：

```
Sign in to confirm you're not a bot.
```

这表明 YouTube 检测到了来自非正常 IP 地址的请求，并启动了风险控制措施。这种情况通常发生在：

- 客户端 IP 来自数据中心
- 使用云服务器部署
- 网络请求频率过高
- 其他被 YouTube 认为可能存在风险的网络环境

## 解决方案

您可以通过在"用户设置"中配置 YouTube Cookie 来解决此问题。

## 重要警告

⚠️ **使用风险提醒**：通过您的 YouTube 账户使用本项目存在账户被封禁（临时或永久）的风险。请注意控制请求频率和下载数量。仅在必要时使用，或考虑使用临时账户。

📝 **使用场景**：此功能仅在访问需要账户权限的内容时必需，例如私人播放列表、年龄限制视频和会员专属内容。

## Cookie 导出步骤

### 步骤 1：准备浏览器插件

首先安装合适的浏览器插件来导出 Cookie：

- **Chrome 用户**：安装 [Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) 插件
- **Firefox 用户**：安装 [cookies.txt](https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/) 插件

⚠️ **安全提醒**：请谨慎选择浏览器插件。如果您之前安装过 "Get cookies.txt"（非 "LOCALLY" 版本）Chrome 插件，建议立即卸载，该插件已被报告为恶意软件并从 Chrome 网上应用店移除。

### 步骤 2：使用隐私浏览模式导出 Cookie

为确保导出的 Cookie 不会被 YouTube 频繁轮换，请按以下步骤操作：

1. **打开隐私浏览窗口**
   - 打开一个新的隐私浏览/无痕浏览窗口
   - 登录您的 YouTube 账户

2. **访问特定页面**
   - 在同一个隐私浏览标签页中，导航至：`https://www.youtube.com/robots.txt`
   - 确保这是唯一打开的隐私浏览标签页

3. **导出 Cookie**
   - 使用已安装的浏览器插件导出 `youtube.com` 的 Cookie
   - 将 Cookie 保存为 `cookies.txt` 文件

4. **关闭浏览器窗口**
   - 导出完成后，立即关闭隐私浏览窗口
   - 确保该会话不会在浏览器中再次打开

### 步骤 3：上传 Cookie 文件

1. 登录您的 PigeonPod
2. 进入"用户设置"页面
3. 找到 “设置 Cookies” 按钮
4. 上传刚才导出的 `cookies.txt` 文件
5. 保存设置
6. 重新尝试下载

## 注意事项

- 本项目仅支持上传通过浏览器插件导出的 `cookies.txt` 文件
- 不支持使用 `yt-dlp --cookies-from-browser` 等命令行方式
- Cookie 文件应保持私密，不要与他人分享
- 如果下载仍然失败，可能需要重新导出更新的 Cookie
- 建议定期更新 Cookie 以确保持续可用性

## 故障排除

如果配置 Cookie 后仍然无法下载：

1. 确认 Cookie 文件格式正确
2. 检查 YouTube 账户是否正常
3. 尝试重新导出 Cookie
4. 考虑更换网络环境或 IP 地址

---

通过正确配置 YouTube Cookie，您应该能够解决大部分由于 IP 风险控制导致的下载问题。如有其他疑问，请查看项目文档或联系技术支持。
