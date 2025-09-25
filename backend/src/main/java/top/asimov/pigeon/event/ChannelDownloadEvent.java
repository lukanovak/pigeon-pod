package top.asimov.pigeon.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 频道下载事件，用于异步处理频道的下载任务
 */
@Getter
public class ChannelDownloadEvent extends ApplicationEvent {

  private final String channelId;
  private final Integer downloadNumber;
  private final String containKeywords;
  private final String excludeKeywords;
  private final Integer minimumDuration;

  public ChannelDownloadEvent(Object source, String channelId, Integer downloadNumber,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    super(source);
    this.channelId = channelId;
    this.downloadNumber = downloadNumber;
    this.containKeywords = containKeywords;
    this.excludeKeywords = excludeKeywords;
    this.minimumDuration = minimumDuration;
  }
}
