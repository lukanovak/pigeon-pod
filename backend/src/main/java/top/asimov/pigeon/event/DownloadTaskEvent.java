package top.asimov.pigeon.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DownloadTaskEvent extends ApplicationEvent {

  private final DownloadTargetType targetType;
  private final DownloadAction action;
  private final String targetId;
  private final Integer downloadNumber;
  private final String containKeywords;
  private final String excludeKeywords;
  private final Integer minimumDuration;

  public DownloadTaskEvent(Object source, DownloadTargetType targetType, DownloadAction action,
      String targetId, Integer downloadNumber, String containKeywords,
      String excludeKeywords, Integer minimumDuration) {
    super(source);
    this.targetType = targetType;
    this.action = action;
    this.targetId = targetId;
    this.downloadNumber = downloadNumber;
    this.containKeywords = containKeywords;
    this.excludeKeywords = excludeKeywords;
    this.minimumDuration = minimumDuration;
  }

  public enum DownloadTargetType {
    CHANNEL,
    PLAYLIST
  }

  public enum DownloadAction {
    INIT,
    HISTORY
  }
}

