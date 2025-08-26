package top.asimov.pigeon.listener;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.service.DownloadService;

@Log4j2
@Component
public class EpisodeEventListener {

  private final DownloadService downloadService;

  public EpisodeEventListener(DownloadService downloadService) {
    this.downloadService = downloadService;
  }

  // **最关键的注解**
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEpisodesCreated(EpisodesCreatedEvent event) {
    log.info("监听到事务已提交的 EpisodesCreatedEvent 事件，开始处理下载任务。");
    List<String> episodeIds = event.getEpisodeIds();
    for (String episodeId : episodeIds) {
      downloadService.downloadAudio(episodeId);
    }
  }
}
