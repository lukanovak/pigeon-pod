# YouTube 下载问题排查指南

## 常见错误：Requested format is not available

### 错误描述
```
ERROR: [youtube] s1_p7Kj49FY: Requested format is not available. Use --list-formats for a list of available formats
```

### 问题原因

这个错误通常由以下几个原因引起：

1. **YouTube 格式限制**：某些视频只提供特定的流格式（如 m3u8 HLS 流），而不是传统的 mp4/webm 格式
2. **PO Token 限制**：YouTube 启用了 Server-Side Ad Placement (SSAP) 实验，需要 PO Token 才能访问某些格式
3. **地区限制**：视频在某些地区可能有不同的可用格式
4. **版本兼容性**：yt-dlp 版本过旧可能无法处理新的 YouTube 格式
5. **客户端限制**：某些格式只对特定客户端（如 iOS、Android）可用

### 解决方案

#### 1. 自动格式回退机制（已实现）

系统已经实现了智能格式选择和回退机制：

**主要格式选择**：
```bash
yt-dlp -f "bestaudio[ext=mp4]/bestaudio[ext=webm]/bestaudio/best" \
       --extractor-args "youtube:player_client=web,android"
```

**回退格式选择**（Android客户端模式）：
```bash
yt-dlp -f "best[height<=480]/worst" \
       --extractor-args "youtube:player_client=android"
```

#### 2. 错误处理改进

- 自动检测格式错误和PO Token错误并触发回退机制
- 使用Android客户端绕过PO Token限制
- 增加重试次数（3次）和片段重试
- 详细的错误日志记录
- 忽略非致命错误继续处理

#### 3. 手动排查步骤

如果问题仍然存在，可以手动排查：

**查看可用格式**：
```bash
yt-dlp --list-formats "https://www.youtube.com/watch?v=VIDEO_ID"
```

**测试下载**：
```bash
yt-dlp -x --audio-format mp3 --simulate \
       --extractor-args "youtube:player_client=web,android" \
       "https://www.youtube.com/watch?v=VIDEO_ID"
```

**更新 yt-dlp**：
```bash
sudo yt-dlp -U
```

#### 4. 特殊情况处理

**Live 直播**：
- 系统会自动检测并跳过 live 直播
- Live 结束后会自动重新添加为普通视频

**受限视频**：
- 确保 cookies 文件配置正确
- 检查视频是否需要登录访问

### 系统改进

最新版本的 DownloadWorker 包含以下改进：

1. **智能格式选择**：优先选择最佳音频格式，支持多种容器格式
2. **客户端回退**：使用Android客户端绕过PO Token限制
3. **自动回退**：格式错误时自动尝试更宽松的格式选择
4. **增强重试**：支持整体重试和片段重试
5. **详细日志**：记录完整的错误信息便于排查
6. **容错处理**：忽略非致命错误，提高下载成功率

### 监控建议

1. 定期检查错误日志中的格式错误模式
2. 监控下载成功率，如果某个频道持续失败可能需要特殊处理
3. 定期更新 yt-dlp 到最新版本
4. 关注 YouTube API 变更和限制

### 相关链接

- [yt-dlp GitHub](https://github.com/yt-dlp/yt-dlp)
- [YouTube 格式文档](https://github.com/yt-dlp/yt-dlp#format-selection)
