package top.asimov.pigeon.worker;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.DownloadType;
import top.asimov.pigeon.constant.EpisodeStatus;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Playlist;
import top.asimov.pigeon.model.PlaylistEpisode;
import top.asimov.pigeon.service.CookiesService;

@Log4j2
@Component
public class DownloadWorker {

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;
  private final EpisodeMapper episodeMapper;
  private final CookiesService cookiesService;
  private final ChannelMapper channelMapper;
  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final MessageSource messageSource;

  public DownloadWorker(EpisodeMapper episodeMapper, CookiesService cookiesService,
      ChannelMapper channelMapper, PlaylistMapper playlistMapper,
      PlaylistEpisodeMapper playlistEpisodeMapper, MessageSource messageSource) {
    this.episodeMapper = episodeMapper;
    this.cookiesService = cookiesService;
    this.channelMapper = channelMapper;
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 audioStoragePath 值
    if (audioStoragePath != null && !audioStoragePath.endsWith("/")) {
      audioStoragePath = audioStoragePath + "/";
      log.info("配置的audioStoragePath值末尾没有/，已调整为: {}", audioStoragePath);
    }
  }

  public void download(String episodeId) {
    Episode episode = episodeMapper.selectById(episodeId);
    if (episode == null) {
      log.error("找不到对应的Episode，ID: {}", episodeId);
      return;
    }

    // 在提交阶段已标记为 DOWNLOADING；若因竞态未被设置，此处兜底设置
    if (!EpisodeStatus.DOWNLOADING.name().equals(episode.getDownloadStatus())) {
      episode.setDownloadStatus(EpisodeStatus.DOWNLOADING.name());
      updateEpisodeWithRetry(episode);
    }

    String tempCookiesFile = null;

    try {
      // 单用户系统，直接使用默认用户的cookies
      tempCookiesFile = cookiesService.createTempCookiesFile("0");

      FeedContext feedContext = resolveFeedContext(episode);
      String feedName = feedContext.title();
      String safeTitle = getSafeTitle(episode.getTitle());

      // 构建输出目录：audioStoragePath/{feed name}/
      String outputDirPath = audioStoragePath + sanitizeFileName(feedName) + File.separator;

      // 获取下载进程
      Process process = getProcess(episodeId, tempCookiesFile, outputDirPath, safeTitle, feedContext);

      // 读取命令输出和错误，非常重要，否则可能导致进程阻塞
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      BufferedReader errorReader = new BufferedReader(
          new InputStreamReader(process.getErrorStream()));
      String line;
      StringBuilder errorLog = new StringBuilder();

      while ((line = reader.readLine()) != null) {
        log.debug("[yt-dlp-out] {}", line);
      }
      while ((line = errorReader.readLine()) != null) {
        log.error("[yt-dlp-err] {}", line);
        errorLog.append(line).append("\n");
      }

      // 设置详细的错误日志
      if (!errorLog.isEmpty()) {
        episode.setErrorLog(errorLog.toString());
      }

      int exitCode = process.waitFor(); // 等待命令执行完成

      // 根据结果更新最终状态
      if (exitCode == 0) {
        DownloadType downloadType = feedContext.downloadType();
        String extension = (downloadType == DownloadType.VIDEO) ? "mp4" : "mp3";
        String mimeType = (downloadType == DownloadType.VIDEO) ? "video/mp4" : "audio/mpeg";

        String finalPath =
            audioStoragePath + sanitizeFileName(feedName) + File.separator + safeTitle + "." + extension;

        episode.setMediaFilePath(finalPath);
        episode.setMediaType(mimeType);
        episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
        // 如果之前有错误日志，下载成功后清空
        episode.setErrorLog(null);
        log.info("下载成功: {}", episode.getTitle());
      } else {
        episode.setDownloadStatus(EpisodeStatus.FAILED.name());
        incrementRetryNumber(episode);
        log.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
      }

    } catch (Exception e) {
      log.error("下载时发生异常: {}", episode.getTitle(), e);
      episode.setErrorLog(e.toString());
      episode.setDownloadStatus(EpisodeStatus.FAILED.name());
      incrementRetryNumber(episode);
    } finally {
      // 清理临时cookies文件
      if (tempCookiesFile != null) {
        cookiesService.deleteTempCookiesFile(tempCookiesFile);
      }
      // 无论成功失败，都保存最终状态（使用重试机制）
      updateEpisodeWithRetry(episode);
    }
  }

  private Process getProcess(String videoId, String cookiesFilePath, String outputDirPath,
      String safeTitle, FeedContext feedContext) throws IOException {
    File outputDir = new File(outputDirPath);
    // 确保目录存在，如果不存在则创建，支持并发安全
    if (!outputDir.exists()) {
      // 使用 mkdirs() 创建目录，即使多线程同时调用也是安全的
      // mkdirs() 返回 false 可能因为：1) 创建失败，2) 目录已存在（其他线程创建的）
      outputDir.mkdirs();
      // 再次检查目录是否存在，这是最可靠的方式
      if (!outputDir.exists()) {
        throw new RuntimeException(messageSource.getMessage("system.create.directory.failed",
            new Object[]{outputDirPath}, LocaleContextHolder.getLocale()));
      }
    }

    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

    List<String> command = new ArrayList<>();
    command.add("yt-dlp");

    DownloadType downloadType = feedContext.downloadType();

    if (downloadType == DownloadType.VIDEO) {
      command.add("-f");
      String videoQuality = feedContext.videoQuality();
      if (StringUtils.hasText(videoQuality)) {
        String format = String.format(
            "bestvideo[height<=%s]+bestaudio/best[height<=%s]",
            videoQuality, videoQuality
        );
        command.add(format);
        log.info("配置为视频下载模式，最高质量: {}p", videoQuality);
      } else {
        command.add("bestvideo+bestaudio/best");
        log.info("配置为视频下载模式，质量: 最佳");
      }
      command.add("--merge-output-format");
      command.add("mp4");
    } else {
      command.add("-x"); // 提取音频
      command.add("--audio-format");
      command.add("mp3"); // 指定音频格式
      command.add("-f");
      command.add("bestaudio/best");

      Integer normalizedQuality = normalizeAudioQuality(feedContext.audioQuality());
      if (normalizedQuality != null) {
        command.add("--audio-quality");
        command.add(String.valueOf(normalizedQuality));
        log.debug("使用音频质量参数: {}", normalizedQuality);
      }
      log.info("配置为音频下载模式");
    }

    command.add("-o");
    String outputTemplate = outputDirPath + safeTitle + ".%(ext)s";
    command.add(outputTemplate); // 输出文件模板:{outputDir}/{title}.%(ext)s

    // 忽略一些非致命错误
    command.add("--ignore-errors");

    // 如果有cookies文件，添加cookies参数
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
      log.debug("使用cookies文件: {}", cookiesFilePath);
    }

    command.add(videoUrl);

    log.info("执行 yt-dlp 命令: {}", String.join(" ", command));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(outputDir); // 设置工作目录
    return processBuilder.start();
  }

  private FeedContext resolveFeedContext(Episode episode) {
    Playlist playlist = playlistMapper.selectLatestByEpisodeId(episode.getId());
    if (playlist != null) {
      String title = safeFeedTitle(playlist.getTitle());
      return new FeedContext(title, playlist.getDownloadType(), playlist.getAudioQuality(),
          playlist.getVideoQuality());
    }

    Channel channel = channelMapper.selectById(episode.getChannelId());
    if (channel != null) {
      String title = safeFeedTitle(channel.getTitle());
      return new FeedContext(title, channel.getDownloadType(), channel.getAudioQuality(),
          channel.getVideoQuality());
    }

    return new FeedContext("unknown", DownloadType.AUDIO, null, null);
  }

  private String safeFeedTitle(String rawTitle) {
    if (!StringUtils.hasText(rawTitle)) {
      return "unknown";
    }
    return rawTitle;
  }

  private Integer normalizeAudioQuality(Integer rawQuality) {
    if (rawQuality == null) {
      return null;
    }
    int normalized = Math.max(0, Math.min(rawQuality, 10));
    if (normalized != rawQuality) {
      log.warn("音频质量值 {} 超出范围，已调整为 {}", rawQuality, normalized);
    }
    return normalized;
  }

  private record FeedContext(String title, DownloadType downloadType, Integer audioQuality, String videoQuality) {
  }

  // 处理title，按UTF-8字节长度截断，最多200字节，结尾加...，并去除非法字符
  private String getSafeTitle(String title) {
    if (title == null) {
      return "untitled";
    }
    String clean = sanitizeFileName(title);
    byte[] bytes = clean.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length <= 200) {
      return clean;
    }
    // 截断到200字节以内，避免截断多字节字符
    int byteCount = 0;
    int i = 0;
    for (; i < clean.length(); i++) {
      int charBytes = String.valueOf(clean.charAt(i)).getBytes(StandardCharsets.UTF_8).length;
      if (byteCount + charBytes > 200) {
        break;
      }
      byteCount += charBytes;
    }
    return clean.substring(0, i) + "...";
  }

  // 过滤非法文件名字符
  private String sanitizeFileName(String name) {
    return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
  }

  /**
   * 使用重试机制更新 Episode 状态，处理可能的数据库锁定冲突
   *
   * @param episode 要更新的 Episode
   */
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 200, multiplier = 2, maxDelay = 2000))
  private void updateEpisodeWithRetry(Episode episode) {
    try {
      episodeMapper.updateById(episode);
      log.debug("成功更新 Episode 状态: {} -> {}", episode.getId(), episode.getDownloadStatus());
    } catch (Exception e) {
      log.warn("更新 Episode 状态失败，将重试: {} -> {}, 错误: {}",
          episode.getId(), episode.getDownloadStatus(), e.getMessage());
      throw e; // 重新抛出异常以触发重试
    }
  }

  private void incrementRetryNumber(Episode episode) {
    Integer current = episode.getRetryNumber();
    int nextRetry = current == null ? 1 : current + 1;
    episode.setRetryNumber(nextRetry);
  }

}
