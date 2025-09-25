package top.asimov.pigeon.listener;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.event.ChannelDownloadEvent;
import top.asimov.pigeon.event.HistoryDownloadEvent;
import top.asimov.pigeon.event.InitDownloadEvent;
import top.asimov.pigeon.service.DownloadTaskSubmitter;
import top.asimov.pigeon.service.ChannelService;
import org.springframework.scheduling.annotation.Async;

@Log4j2
@Component
public class EpisodeEventListener {

  private final DownloadTaskSubmitter downloadTaskSubmitter;
  private final ChannelService channelService;

  public EpisodeEventListener(DownloadTaskSubmitter downloadTaskSubmitter, ChannelService channelService) {
    this.downloadTaskSubmitter = downloadTaskSubmitter;
    this.channelService = channelService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEpisodesCreated(EpisodesCreatedEvent event) {
    log.info("监听到事务已提交的 EpisodesCreatedEvent 事件，开始处理下载任务。");
    List<String> episodeIds = event.getEpisodeIds();

    for (String episodeId : episodeIds) {
      downloadTaskSubmitter.submitDownloadTask(episodeId);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleChannelInitializationDownload(InitDownloadEvent event) {
    log.info("监听到频道异步初始化事件，频道ID: {}, 初始视频数量: {}", event.getChannelId(), event.getDownloadNumber());
    
    // 异步处理频道初始化
    channelService.processChannelInitializationAsync(
        event.getChannelId(),
        event.getDownloadNumber(),
        event.getContainKeywords(),
        event.getExcludeKeywords(),
        event.getMinimumDuration()
    );
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleChannelHistoryDownload(HistoryDownloadEvent event) {
    log.info("监听到频道异步下载历史节目事件，频道ID: {}, 下载历史视频数量: {}", event.getChannelId(), event.getDownloadNumber());
    
    // 异步处理频道初始化
    channelService.processChannelDownloadHistoryAsync(
        event.getChannelId(),
        event.getDownloadNumber(),
        event.getContainKeywords(),
        event.getExcludeKeywords(),
        event.getMinimumDuration()
    );
  }

}
