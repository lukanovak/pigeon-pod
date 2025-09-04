package top.asimov.pigeon.scheduler;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.asimov.pigeon.mapper.EpisodeMapper;

@Log4j2
@Service
public class EpisodeCleaner {

  private final EpisodeMapper episodeMapper;

  public EpisodeCleaner(EpisodeMapper episodeMapper) {
    this.episodeMapper = episodeMapper;
  }

  /**
   * 每2小时执行一次，清理超过频道最大集数限制的剧集
   */
  @Scheduled(fixedRate = 2, timeUnit = TimeUnit.HOURS)
  public void syncDueChannels() {
    log.info("开始执行清理任务...");
    episodeMapper.deleteEpisodesOverChannelMaximum();
    log.info("清理任务执行完毕。");
  }
}
