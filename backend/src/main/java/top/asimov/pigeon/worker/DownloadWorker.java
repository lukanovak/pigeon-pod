package top.asimov.pigeon.worker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
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
  private final YoutubeHelper youtubeHelper;

  public DownloadWorker(EpisodeMapper episodeMapper, CookiesService cookiesService, YoutubeHelper youtubeHelper) {
    this.episodeMapper = episodeMapper;
    this.cookiesService = cookiesService;
    this.youtubeHelper = youtubeHelper;
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

      // 双重保险：检测是否为 live 节目（通常已在获取节目信息时过滤）
      if (youtubeHelper.isLiveVideo(videoId)) {
        log.warn("下载时检测到 live 节目，跳过下载: {}", episode.getTitle());
        // 直接删除这个 episode，live 结束后变成普通视频之后，在订阅源更新时会自动重新添加
        episodeMapper.deleteById(videoId);
        return;
      }

      // 获取下载进程
      Process process = getProcess(videoId, tempCookiesFile);

      // 读取命令输出和错误，非常重要，否则可能导致进程阻塞
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      BufferedReader errorReader = new BufferedReader(
          new InputStreamReader(process.getErrorStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        log.debug("[yt-dlp-out] {}", line);
      }
      while ((line = errorReader.readLine()) != null) {
        log.error("[yt-dlp-err] {}", line);
        episode.setErrorLog(line);
      }

      int exitCode = process.waitFor(); // 等待命令执行完成

      // 根据结果更新最终状态
      if (exitCode == 0) {
        // 假设输出文件名就是 videoId.mp3
        String finalPath = audioStoragePath + videoId + ".mp3";
        episode.setAudioFilePath(finalPath);
        episode.setDownloadStatus(EpisodeStatus.COMPLETED.name());
        log.info("下载成功: {}", episode.getTitle());
      } else {
        episode.setDownloadStatus(EpisodeStatus.FAILED.name());
        log.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
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

  private Process getProcess(String videoId, String cookiesFilePath) throws IOException {
    // 确保目录存在
    File dir = new File(audioStoragePath);
    if (!dir.exists() && !dir.mkdirs()) {
      throw new RuntimeException("无法创建目录: " + audioStoragePath);
    }

    // 准备并执行 yt-dlp 命令
    String outputTemplate = audioStoragePath + "%(id)s.%(ext)s";
    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

    List<String> command = new ArrayList<>();
    command.add("yt-dlp");
    command.add("-x"); // 提取音频
    command.add("--audio-format");
    command.add("mp3"); // 指定音频格式
    command.add("-o");
    command.add(outputTemplate); // 输出文件模板

    // 如果有cookies文件，添加cookies参数
    if (cookiesFilePath != null) {
      command.add("--cookies");
      command.add(cookiesFilePath);
      log.debug("使用cookies文件: {}", cookiesFilePath);
    }

    command.add(videoUrl);

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(new File(audioStoragePath)); // 设置工作目录
    return processBuilder.start();
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
