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
import top.asimov.pigeon.constant.EpisodeStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.service.CookiesService;
import top.asimov.pigeon.util.YoutubeHelper;

@Log4j2
@Component
public class DownloadWorker {

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;
  private final EpisodeMapper episodeMapper;
  private final CookiesService cookiesService;
  private final MessageSource messageSource;

  public DownloadWorker(EpisodeMapper episodeMapper, CookiesService cookiesService, MessageSource messageSource) {
    this.episodeMapper = episodeMapper;
    this.cookiesService = cookiesService;
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

  public void download(String videoId) {
    Episode episode = episodeMapper.selectById(videoId);

    // 标记为真正的DOWNLOADING
    episode.setDownloadStatus(EpisodeStatus.DOWNLOADING.name());
    updateEpisodeWithRetry(episode);

    String tempCookiesFile = null;

    try {
      // 单用户系统，直接使用默认用户的cookies
      tempCookiesFile = cookiesService.createTempCookiesFile("0");

      String channelName = episodeMapper.getChannelNameByEpisodeId(videoId);
      String safeTitle = getSafeTitle(episode.getTitle());

      // 构建输出目录：audioStoragePath/{channel name}/
      String outputDirPath = audioStoragePath + sanitizeFileName(channelName) + File.separator;

      // 获取下载进程
      Process process = getProcess(videoId, tempCookiesFile, outputDirPath, safeTitle);

      // 读取命令输出和错误，非常重要，否则可能导致进程阻塞
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      BufferedReader errorReader = new BufferedReader(
          new InputStreamReader(process.getErrorStream()));
      String line;
      StringBuilder errorLog = new StringBuilder();
      boolean hasFormatError = false;
      
      while ((line = reader.readLine()) != null) {
        log.debug("[yt-dlp-out] {}", line);
      }
      while ((line = errorReader.readLine()) != null) {
        log.error("[yt-dlp-err] {}", line);
        errorLog.append(line).append("\n");
        
        // 检测特定的格式错误和PO Token错误
        if (line.contains("Requested format is not available") || 
            line.contains("No video formats found") ||
            line.contains("format is not available") ||
            line.contains("Only images are available") ||
            line.contains("require a GVS PO Token") ||
            line.contains("SABR streaming")) {
          hasFormatError = true;
        }
      }
      
      // 设置详细的错误日志
      if (!errorLog.isEmpty()) {
        episode.setErrorLog(errorLog.toString());
      }

      int exitCode = process.waitFor(); // 等待命令执行完成

      // 根据结果更新最终状态
      if (exitCode == 0) {
        String finalPath = audioStoragePath + sanitizeFileName(channelName) + File.separator + safeTitle + ".mp3";
        episode.setAudioFilePath(finalPath);
        episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
        // 如果之前有错误日志，下载成功后清空
        episode.setErrorLog(null);
        log.info("下载成功: {}", episode.getTitle());
      } else {
        // 如果是格式错误，尝试使用更宽松的格式选择
        if (hasFormatError) {
          log.warn("检测到格式错误，尝试使用回退格式重新下载: {}", episode.getTitle());
          try {
            Process fallbackProcess = getFallbackProcess(videoId, tempCookiesFile, outputDirPath, safeTitle);
            int fallbackExitCode = fallbackProcess.waitFor();
            
            if (fallbackExitCode == 0) {
              String finalPath = audioStoragePath + sanitizeFileName(channelName) + File.separator + safeTitle + ".mp3";
              episode.setAudioFilePath(finalPath);
              episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
              log.info("回退格式下载成功: {}", episode.getTitle());
            } else {
              episode.setDownloadStatus(EpisodeStatus.FAILED.name());
              log.error("回退格式下载也失败，退出码 {}: {}", fallbackExitCode, episode.getTitle());
            }
          } catch (Exception fallbackEx) {
            episode.setDownloadStatus(EpisodeStatus.FAILED.name());
            log.error("回退格式下载异常: {}", episode.getTitle(), fallbackEx);
          }
        } else {
          episode.setDownloadStatus(EpisodeStatus.FAILED.name());
          log.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
        }
      }

    } catch (Exception e) {
      log.error("下载时发生异常: {}", episode.getTitle(), e);
      episode.setErrorLog(e.toString());
      episode.setDownloadStatus(EpisodeStatus.FAILED.name());
    } finally {
      // 清理临时cookies文件
      if (tempCookiesFile != null) {
        cookiesService.deleteTempCookiesFile(tempCookiesFile);
      }
      // 无论成功失败，都保存最终状态（使用重试机制）
      updateEpisodeWithRetry(episode);
    }
  }

  private Process getProcess(String videoId, String cookiesFilePath, String outputDirPath, String safeTitle) throws IOException {
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

    // 输出模板：{outputDir}/{title}.%(ext)s
    String outputTemplate = outputDirPath + safeTitle + ".%(ext)s";
    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

    List<String> command = new ArrayList<>();
    command.add("yt-dlp");
    command.add("-x"); // 提取音频
    command.add("--audio-format");
    command.add("mp3"); // 指定音频格式
    command.add("-o");
    command.add(outputTemplate); // 输出文件模板
    
    // 添加格式回退机制，优先选择最佳音频格式
    command.add("-f");
    command.add("bestaudio[ext=mp4]/bestaudio[ext=webm]/bestaudio/best");
    
    // 添加重试和错误恢复选项
    command.add("--retries");
    command.add("3");
    command.add("--fragment-retries");
    command.add("3");
    
    // 忽略一些非致命错误
    command.add("--ignore-errors");

    // 如果有cookies文件，添加cookies参数
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
      log.debug("使用cookies文件: {}", cookiesFilePath);
    }

    command.add(videoUrl);

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(outputDir); // 设置工作目录
    return processBuilder.start();
  }

  /**
   * 获取回退格式的下载进程，使用更宽松的格式选择
   */
  private Process getFallbackProcess(String videoId, String cookiesFilePath, String outputDirPath, String safeTitle) throws IOException {
    File outputDir = new File(outputDirPath);
    String outputTemplate = outputDirPath + safeTitle + ".%(ext)s";
    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

    List<String> command = new ArrayList<>();
    command.add("yt-dlp");
    command.add("-x"); // 提取音频
    command.add("--audio-format");
    command.add("mp3");
    command.add("-o");
    command.add(outputTemplate);
    
    // 使用最宽松的格式选择
    command.add("-f");
    command.add("best[height<=480]/worst");
    
    // 添加更多容错选项
    command.add("--no-check-certificate");
    command.add("--socket-timeout");
    command.add("30");
    
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
    }

    command.add(videoUrl);

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(outputDir);
    return processBuilder.start();
  }

  // 处理title，按UTF-8字节长度截断，最多200字节，结尾加...，并去除非法字符
  private String getSafeTitle(String title) {
    if (title == null) return "untitled";
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
      if (byteCount + charBytes > 200) break;
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

}
