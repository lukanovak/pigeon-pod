package top.asimov.pigeon.service;

import lombok.extern.log4j.Log4j2;
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
   *
   * @param videoId 视频ID
   * @return 如果成功将状态更新为DOWNLOADING，则返回true，否则返回false。
   */
  @Transactional
  public boolean prepareDownload(String videoId) {
    Episode episode = episodeMapper.selectById(videoId);
    if (ObjectUtils.isEmpty(episode)) {
      log.warn("准备下载时未找到Episode: {}", videoId);
      return false;
    }

    if (!EpisodeDownloadStatus.PENDING.name().equals(episode.getDownloadStatus())) {
      log.info("Episode {} 状态不是PENDING，跳过下载准备。", videoId);
      return false;
    }

    episode.setDownloadStatus(EpisodeDownloadStatus.DOWNLOADING.name());
    episodeMapper.updateById(episode);
    log.info("状态已更新为 DOWNLOADING: {}", episode.getTitle());
    return true;
  }
}
