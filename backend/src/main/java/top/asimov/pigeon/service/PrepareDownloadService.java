package top.asimov.pigeon.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.EpisodeStatus;
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
   * 检查任务是否可以下载，不再更新状态
   * 状态更新已在EpisodeEventListener中完成
   *
   * @param videoId 视频ID
   * @return 如果成功将状态更新为 QUEUED，则返回true，否则返回false。
   */
  @Transactional(readOnly = true)
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 5,
      backoff = @Backoff(delay = 200, multiplier = 2, maxDelay = 2000)
  )
  public boolean prepareDownload(String videoId) {
    Episode episode = episodeMapper.selectById(videoId);
    if (ObjectUtils.isEmpty(episode)) {
      log.warn("准备下载时未找到Episode: {}", videoId);
      return false;
    }

    // 只检查状态，不更新状态，此时任务应该已经是QUEUED状态
    if (!EpisodeStatus.QUEUED.name().equals(episode.getDownloadStatus())) {
      log.info("Episode {} 状态不是 QUEUED，跳过下载。当前状态: {}",
          videoId, episode.getDownloadStatus());
      return false;
    }

    log.debug("Episode {} 准备就绪，可以开始下载", videoId);
    return true;
  }
}
