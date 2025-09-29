package top.asimov.pigeon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Playlist;

@Log4j2
@Service
public class FeedService {

  private final ChannelService channelService;
  private final PlaylistService playlistService;
  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  public FeedService(ChannelService channelService, PlaylistService playlistService,
      ObjectMapper objectMapper, MessageSource messageSource) {
    this.channelService = channelService;
    this.playlistService = playlistService;
    this.objectMapper = objectMapper;
    this.messageSource = messageSource;
  }

  public FeedType resolveType(String rawType) {
    if (!StringUtils.hasText(rawType)) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
    try {
      return FeedType.valueOf(rawType.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(messageSource
          .getMessage("feed.type.invalid", new Object[]{rawType},
              LocaleContextHolder.getLocale()));
    }
  }

  public List<?> list(FeedType type) {
    return switch (type) {
      case CHANNEL -> channelService.selectChannelList();
      case PLAYLIST -> playlistService.selectPlaylistList();
    };
  }

  public Map<String, List<?>> listAll() {
    return Map.of(
        FeedType.CHANNEL.name().toLowerCase(), channelService.selectChannelList(),
        FeedType.PLAYLIST.name().toLowerCase(), playlistService.selectPlaylistList());
  }

  public Object detail(FeedType type, String id) {
    return switch (type) {
      case CHANNEL -> channelService.channelDetail(id);
      case PLAYLIST -> playlistService.playlistDetail(id);
    };
  }

  public String getSubscribeUrl(FeedType type, String id) {
    return switch (type) {
      case CHANNEL -> channelService.getChannelRssFeedUrl(id);
      case PLAYLIST -> playlistService.getPlaylistRssFeedUrl(id);
    };
  }

  public Object updateConfig(FeedType type, String id, Map<String, Object> payload) {
    return switch (type) {
      case CHANNEL -> channelService.updateChannelConfig(id, convert(payload, Channel.class));
      case PLAYLIST -> playlistService.updatePlaylistConfig(id,
          convert(payload, Playlist.class));
    };
  }

  public Object fetch(FeedType type, Map<String, String> request) {
    return switch (type) {
      case CHANNEL -> channelService.fetchChannel(
          resolveSourceUrl(request, "channelUrl"));
      case PLAYLIST -> playlistService.fetchPlaylist(
          resolveSourceUrl(request, "playlistUrl"));
    };
  }

  public Object preview(FeedType type, Map<String, Object> payload) {
    return switch (type) {
      case CHANNEL -> channelService.previewChannel(convert(payload, Channel.class));
      case PLAYLIST -> playlistService.previewPlaylist(convert(payload, Playlist.class));
    };
  }

  public Object add(FeedType type, Map<String, Object> payload) {
    return switch (type) {
      case CHANNEL -> channelService.saveChannel(convert(payload, Channel.class));
      case PLAYLIST -> playlistService.savePlaylist(convert(payload, Playlist.class));
    };
  }

  public void delete(FeedType type, String id) {
    switch (type) {
      case CHANNEL -> channelService.deleteChannel(id);
      case PLAYLIST -> playlistService.deletePlaylist(id);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T convert(Map<String, Object> payload, Class<T> targetType) {
    return objectMapper.convertValue(payload, targetType);
  }

  private String resolveSourceUrl(Map<String, String> request, String specificKey) {
    String sourceUrl = request.get("sourceUrl");
    if (!StringUtils.hasText(sourceUrl)) {
      sourceUrl = request.get(specificKey);
    }
    if (!StringUtils.hasText(sourceUrl)) {
      throw new BusinessException(messageSource
          .getMessage("feed.source.url.missing", null, LocaleContextHolder.getLocale()));
    }
    return sourceUrl;
  }
}
