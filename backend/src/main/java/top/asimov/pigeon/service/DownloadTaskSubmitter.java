package top.asimov.pigeon.service;

import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class DownloadTaskSubmitter {

  private final ThreadPoolTaskExecutor downloadTaskExecutor;
  private final DownloadService downloadService;
  private final TaskStatusService taskStatusService;

  @Autowired
  public DownloadTaskSubmitter(ThreadPoolTaskExecutor downloadTaskExecutor,
      DownloadService downloadService,
      @Lazy TaskStatusService taskStatusService) {
    this.downloadTaskExecutor = downloadTaskExecutor;
    this.downloadService = downloadService;
    this.taskStatusService = taskStatusService;
  }

  /**
   * 尝试提交单个下载任务
   *
   * @param episodeId 节目ID
   * @return true if successful, false otherwise
   */
  public boolean submitDownloadTask(String episodeId) {
    try {
      // 先尝试更新状态为QUEUED（通过代理Bean调用，确保新事务生效）
      boolean updated = taskStatusService.updateStatusToQueued(episodeId);
      if (updated) {
        // 状态更新成功后，提交到线程池
        downloadTaskExecutor.execute(() -> {
          downloadService.downloadAudioSync(episodeId);
        });
        log.debug("任务已提交到队列: {}", episodeId);
        return true;
      }
      return false;
    } catch (RejectedExecutionException e) {
      // 提交失败，回滚状态到PENDING（通过代理Bean调用）
      taskStatusService.rollbackStatusToPending(episodeId);
      log.warn("线程池队列已满，任务被拒绝，状态回滚为 PENDING: {}", episodeId);
      return false;
    }
  }
}


