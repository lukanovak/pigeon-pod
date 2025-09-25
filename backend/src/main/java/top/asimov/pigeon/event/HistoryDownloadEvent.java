package top.asimov.pigeon.event;

public class HistoryDownloadEvent extends ChannelDownloadEvent{

  public HistoryDownloadEvent(Object source, String channelId, Integer downloadNumber,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    super(source, channelId, downloadNumber, containKeywords, excludeKeywords, minimumDuration);
  }
}
