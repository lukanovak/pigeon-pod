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

  public void downloadAudioSync(String videoId) {
    boolean canDownload = prepareDownloadService.prepareDownload(videoId);
    if (canDownload) {
      downloadWorker.download(videoId);
    }
  }

}
