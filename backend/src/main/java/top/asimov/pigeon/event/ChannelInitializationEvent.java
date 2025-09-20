package top.asimov.pigeon.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 频道初始化事件，用于异步处理频道的初始化下载任务
 */
@Getter
public class ChannelInitializationEvent extends ApplicationEvent {

  private final String channelId;
  private final Integer initialEpisodes;
  private final String containKeywords;
  private final String excludeKeywords;
  private final Integer minimumDuration;

  public ChannelInitializationEvent(Object source, String channelId, Integer initialEpisodes,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    super(source);
    this.channelId = channelId;
    this.initialEpisodes = initialEpisodes;
    this.containKeywords = containKeywords;
    this.excludeKeywords = excludeKeywords;
    this.minimumDuration = minimumDuration;
  }
}
