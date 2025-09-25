package top.asimov.pigeon.event;

public class InitDownloadEvent extends ChannelDownloadEvent {

  public InitDownloadEvent(Object source, String channelId, Integer downloadNumber,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    super(source, channelId, downloadNumber, containKeywords, excludeKeywords, minimumDuration);
  }
}
