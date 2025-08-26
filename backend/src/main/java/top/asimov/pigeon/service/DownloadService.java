package top.asimov.pigeon.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.asimov.pigeon.worker.DownloadWorker;

@Service
public class DownloadService {

  private final PrepareDownloadService prepareDownloadService;
  private final DownloadWorker downloadWorker;

  public DownloadService(PrepareDownloadService prepareDownloadService,
      DownloadWorker downloadWorker) {
    this.prepareDownloadService = prepareDownloadService;
    this.downloadWorker = downloadWorker;
  }

  // 使用我们自定义的线程池
  @Async("downloadTaskExecutor")
  public void downloadAudio(String videoId) {
    // 1. 调用状态服务，执行第一个短事务
    boolean canDownload = prepareDownloadService.prepareDownload(videoId);

    // 2. 在事务之外，执行耗时操作（通过调用Worker）
    if (canDownload) {
      // 这是一个从 DownloadService 到 DownloadWorker 的外部调用，
      // 所以 DownloadWorker 上的 @Transactional 注解会生效。
      downloadWorker.download(videoId);
    }
  }
}
