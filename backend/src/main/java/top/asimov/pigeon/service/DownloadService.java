package top.asimov.pigeon.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

@Service
public class DownloadService {

  private static final Logger logger = LoggerFactory.getLogger(DownloadService.class);

  private final EpisodeMapper episodeMapper;

  public DownloadService(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  // 使用我们自定义的线程池
  @Async("downloadTaskExecutor")
  // 使用新的事务，确保下载任务的数据库操作独立，不影响调用方
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void downloadAudio(String videoId) {
    // 1. 从数据库获取最新的Episode状态
    Episode episode = episodeMapper.selectById(videoId);
    if (ObjectUtils.isEmpty(episode)) {
      logger.warn("尝试下载一个不存在的Episode: {}", videoId);
      return;
    }

    // 2. 状态检查，防止重复下载
    if (episode.getDownloadStatus().equals(EpisodeDownloadStatus.PENDING.name())) {
      logger.info("Episode {} 状态不是PENDING，跳过下载。", videoId);
      return;
    }

    // 3. 更新状态为 DOWNLOADING，并立即保存，起到“锁定”作用
    episode.setDownloadStatus(EpisodeDownloadStatus.DOWNLOADING.name());
    episodeMapper.updateById(episode);

    logger.info("开始下载: {}", episode.getTitle());

    try {
      // 4. 准备并执行 yt-dlp 命令
      // 建议将下载路径和yt-dlp路径配置在 application.properties 中
      String downloadPath = "./audio/storage/";
      String outputTemplate = downloadPath + "%(id)s.%(ext)s";
      String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

      ProcessBuilder processBuilder = new ProcessBuilder(
          "yt-dlp",
          "-x", // 提取音频
          "--audio-format", "mp3", // 指定音频格式 (aac, m4a, opus等也都很好)
          "-o", outputTemplate, // 输出文件模板
          videoUrl
      );

      processBuilder.directory(new File(downloadPath)); // 设置工作目录
      Process process = processBuilder.start();

      // 读取命令输出和错误，非常重要，否则可能导致进程阻塞
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        logger.debug("[yt-dlp-out] {}", line);
      }
      while ((line = errorReader.readLine()) != null) {
        logger.error("[yt-dlp-err] {}", line);
      }

      int exitCode = process.waitFor(); // 等待命令执行完成

      // 5. 根据结果更新最终状态
      if (exitCode == 0) {
        // 假设输出文件名就是 videoId.mp3
        String finalPath = downloadPath + videoId + ".mp3";
        episode.setAudioFilePath(finalPath);
        episode.setDownloadStatus(EpisodeDownloadStatus.COMPLETED.name());
        logger.info("下载成功: {}", episode.getTitle());
      } else {
        episode.setDownloadStatus(EpisodeDownloadStatus.FAILED.name());
        logger.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
      }

    } catch (Exception e) {
      logger.error("下载时发生异常: {}", episode.getTitle(), e);
      episode.setDownloadStatus(EpisodeDownloadStatus.FAILED.name());
    } finally {
      // 6. 无论成功失败，都保存最终状态
      episodeMapper.updateById(episode);
    }
  }
}
