package top.asimov.pigeon.service.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.service.ChannelService;

@Component
public class ChannelFeedHandler extends AbstractFeedHandler<Channel> {

  private final ChannelService channelService;

  public ChannelFeedHandler(ChannelService channelService, ObjectMapper objectMapper,
      MessageSource messageSource) {
    super(objectMapper, messageSource);
    this.channelService = channelService;
  }

  @Override
  public FeedType getType() {
    return FeedType.CHANNEL;
  }

  @Override
  public List<Channel> list() {
    return channelService.selectChannelList();
  }

  @Override
  public Channel detail(String id) {
    return channelService.channelDetail(id);
  }

  @Override
  public String getSubscribeUrl(String id) {
    return channelService.getChannelRssFeedUrl(id);
  }

  @Override
  public Object updateConfig(String id, Map<String, Object> payload) {
    return channelService.updateChannelConfig(id, convert(payload, Channel.class));
  }

  @Override
  public Object fetch(Map<String, ?> payload) {
    return channelService.fetchChannel(resolveSourceUrl(payload, "channelUrl"));
  }

  @Override
  public Object preview(Map<String, Object> payload) {
    return channelService.previewChannel(convert(payload, Channel.class));
  }

  @Override
  public Object add(Map<String, Object> payload) {
    return channelService.saveChannel(convert(payload, Channel.class));
  }

  @Override
  public void delete(String id) {
    channelService.deleteChannel(id);
  }
}
