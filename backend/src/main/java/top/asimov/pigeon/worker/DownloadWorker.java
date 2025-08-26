package top.asimov.pigeon.worker;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

@Log4j2
@Component
public class DownloadWorker {

  @Value("${pigeon.audio-file-path}")
  private String audioStoragePath;
  private final EpisodeMapper episodeMapper;

  public DownloadWorker(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  public void download(String videoId) {
    Episode episode = episodeMapper.selectById(videoId);

    try {
      // 准备并执行 yt-dlp 命令
      // 建议将下载路径和yt-dlp路径配置在 application.properties 中
      String downloadPath = audioStoragePath;
      String outputTemplate = downloadPath + "%(id)s.%(ext)s";
      String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

      ProcessBuilder processBuilder = new ProcessBuilder(
          "yt-dlp",
          "-x", // 提取音频
          "--audio-format", "mp3", // 指定音频格式
          "-o", outputTemplate, // 输出文件模板
          videoUrl
      );

      processBuilder.directory(new File(downloadPath)); // 设置工作目录
      Process process = processBuilder.start();

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
      }

      int exitCode = process.waitFor(); // 等待命令执行完成

      // 根据结果更新最终状态
      if (exitCode == 0) {
        // 假设输出文件名就是 videoId.mp3
        String finalPath = downloadPath + videoId + ".mp3";
        episode.setAudioFilePath(finalPath);
        episode.setDownloadStatus(EpisodeDownloadStatus.COMPLETED.name());
        log.info("下载成功: {}", episode.getTitle());
      } else {
        episode.setDownloadStatus(EpisodeDownloadStatus.FAILED.name());
        log.error("下载失败，退出码 {}: {}", exitCode, episode.getTitle());
      }

    } catch (Exception e) {
      log.error("下载时发生异常: {}", episode.getTitle(), e);
      episode.setDownloadStatus(EpisodeDownloadStatus.FAILED.name());
    } finally {
      // 无论成功失败，都保存最终状态
      episodeMapper.updateById(episode);
    }
  }
}
