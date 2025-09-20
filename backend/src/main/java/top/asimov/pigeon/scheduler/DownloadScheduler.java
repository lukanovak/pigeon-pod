package top.asimov.pigeon.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.constant.EpisodeStatus;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.service.DownloadTaskSubmitter;

@Log4j2
@Component
public class DownloadScheduler {

  @Qualifier("downloadTaskExecutor")
  private ThreadPoolTaskExecutor downloadTaskExecutor;
  private final EpisodeMapper episodeMapper;
  private final DownloadTaskSubmitter downloadTaskSubmitter;

  public DownloadScheduler(ThreadPoolTaskExecutor downloadTaskExecutor, EpisodeMapper episodeMapper,
      DownloadTaskSubmitter downloadTaskSubmitter) {
    this.downloadTaskExecutor = downloadTaskExecutor;
    this.episodeMapper = episodeMapper;
    this.downloadTaskSubmitter = downloadTaskSubmitter;
  }

  // 每30秒检查一次待下载任务
  @Scheduled(fixedDelay = 30000)
  public void processQueuedDownloads() {
    // 获取线程池状态
    int activeCount = downloadTaskExecutor.getActiveCount();
    int queueSize = downloadTaskExecutor.getThreadPoolExecutor().getQueue().size();
    int maxPoolSize = downloadTaskExecutor.getMaxPoolSize();
    int queueCapacity = downloadTaskExecutor.getQueueCapacity();

    // 计算还能接受多少任务
    int availableSlots = (maxPoolSize + queueCapacity) - (activeCount + queueSize);

    log.debug("线程池状态检查: 活跃={}, 队列={}, 可用空位={}", activeCount, queueSize,
        availableSlots);

    if (availableSlots > 0) {
      // 查找PENDING状态的任务
      List<Episode> pendingEpisodes = episodeMapper.selectList(
          new QueryWrapper<Episode>()
              .eq("download_status", EpisodeStatus.PENDING.name())
              .orderByAsc("created_at")
              .last("LIMIT " + availableSlots)
      );

      for (Episode episode : pendingEpisodes) {
        boolean success = downloadTaskSubmitter.submitDownloadTask(episode.getId());
        if (!success) {
          break; // 提交失败，可能是队列满了，停止继续处理
        }
      }
    }
  }

}
