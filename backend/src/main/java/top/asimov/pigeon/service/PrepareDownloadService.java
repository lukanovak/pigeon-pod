package top.asimov.pigeon.service;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;

@Log4j2
@Service
public class PrepareDownloadService {

  private final EpisodeMapper episodeMapper;

  public PrepareDownloadService(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  /**
   * 开启一个独立的事务来准备下载任务。
   * 使用重试机制处理可能的数据库锁定冲突。
   *
   * @param videoId 视频ID
   * @return 如果成功将状态更新为DOWNLOADING，则返回true，否则返回false。
   */
  @Transactional
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
  public boolean prepareDownload(String videoId) {
    Episode episode = episodeMapper.selectById(videoId);
    if (ObjectUtils.isEmpty(episode)) {
      log.warn("准备下载时未找到Episode: {}", videoId);
      return false;
    }

    List<String> validStatuses = List.of(
        EpisodeDownloadStatus.PENDING.name(),
        EpisodeDownloadStatus.FAILED.name()
    );
    if (!validStatuses.contains(episode.getDownloadStatus())) {
      log.info("Episode {} 状态不是 PENDING 或 FAILED，跳过下载准备。", videoId);
      return false;
    }

    episode.setDownloadStatus(EpisodeDownloadStatus.DOWNLOADING.name());
    episodeMapper.updateById(episode);
    log.info("状态已更新为 DOWNLOADING: {}", episode.getTitle());
    return true;
  }
}
