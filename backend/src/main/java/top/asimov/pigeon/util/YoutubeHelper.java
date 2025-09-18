package top.asimov.pigeon.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Episode.EpisodeBuilder;
import top.asimov.pigeon.service.AccountService;

@Component
public class YoutubeHelper {

  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final AccountService accountService;
  private final MessageSource messageSource;

  public YoutubeHelper(AccountService accountService, MessageSource messageSource) {
    this.accountService = accountService;
    this.messageSource = messageSource;
  }

  public Channel fetchYoutubeChannelByUrl(String channelUrl) {
    String channelId = fetchYoutubeChannelIdByUrl(channelUrl);
    return fetchYoutubeChannelByYoutubeChannelId(channelId);
  }

  public String getHandleFromUrl(String channelUrl) {
    if (channelUrl == null || !channelUrl.contains("@")) {
      return null;
    }
    int atIndex = channelUrl.lastIndexOf('@');
    return channelUrl.substring(atIndex + 1);
  }

  public List<Episode> fetchYoutubeChannelVideos(String channelId, String lastSyncedVideoId,
      int fetchNum, String containKeywords, String excludeKeywords, Integer minimalDuration) {
    try {
      YouTube youtubeService = getService();
      String youtubeApiKey = getYoutubeApiKey();

      // 1. 获取 Uploads 播放列表 ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0).getContentDetails()
          .getRelatedPlaylists().getUploads();

      // 2. 循环分页抓取：边抓取边批量查时长并过滤，直到满足 fetchNum 或无更多数据
      List<Episode> resultEpisodes = new ArrayList<>();
      String nextPageToken = "";
      boolean foundLastSyncedVideo = false;

      Map<String, String> durationCache = new HashMap<>();

      while (resultEpisodes.size() < fetchNum) {
        long pageSize = Math.min(50L, fetchNum); // API 单页最大 50
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
            .list("snippet")
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(pageSize)
            .setPageToken(nextPageToken)
            .setKey(youtubeApiKey);

        PlaylistItemListResponse response = request.execute();
        List<PlaylistItem> pageItems = response.getItems();
        if (pageItems == null || pageItems.isEmpty()) {
          break; // 没有更多数据
        }

        // 2.1 先用标题关键词与 lastSynced 过滤，得到候选集合
        List<PlaylistItem> candidates = new ArrayList<>();
        for (PlaylistItem item : pageItems) {
          String title = item.getSnippet().getTitle();

          // 处理 containKeywords，支持空格分割的多个关键词，包含任意一个就行
          if (StringUtils.hasLength(containKeywords)) {
            String[] keywords = containKeywords.trim().split("\\s+");
            boolean containsAny = false;
            for (String keyword : keywords) {
              if (title.toLowerCase().contains(keyword.toLowerCase())) {
                containsAny = true;
                break;
              }
            }
            if (!containsAny) {
              continue;
            }
          }

          // 处理 excludeKeywords，支持空格分割的多个关键词，包含任意一个就排除
          if (StringUtils.hasLength(excludeKeywords)) {
            String[] keywords = excludeKeywords.trim().split("\\s+");
            boolean shouldExclude = false;
            for (String keyword : keywords) {
              if (title.toLowerCase().contains(keyword.toLowerCase())) {
                shouldExclude = true;
                break;
              }
            }
            if (shouldExclude) {
              continue;
            }
          }

          if (minimalDuration != null) {
            String duration = fetchVideoDurations(youtubeService, youtubeApiKey, item);
            durationCache.put(item.getId(), duration);

            if (!StringUtils.hasText(duration)) {
              continue;
            }

            long minutes = Duration.parse(duration).toMinutes();
            if (minutes < minimalDuration) {
              continue;
            }
          }

          String currentVideoId = item.getSnippet().getResourceId().getVideoId();
          if (currentVideoId.equals(lastSyncedVideoId)) {
            foundLastSyncedVideo = true;
            break; // 到达上次同步视频，后面的都是旧的
          }
          candidates.add(item);
        }

        // 2.2 批量查询候选的时长并应用时长过滤
        for (PlaylistItem item : candidates) {
          String duration = durationCache.get(item.getId());
          if (!StringUtils.hasText(duration)) {
            duration = fetchVideoDurations(youtubeService, youtubeApiKey, item);
          }
          EpisodeBuilder episodeBuilder = Episode.builder()
              .id(item.getSnippet().getResourceId().getVideoId())
              .channelId(channelId)
              .position(item.getSnippet().getPosition().intValue())
              .title(item.getSnippet().getTitle())
              .description(item.getSnippet().getDescription())
              .publishedAt(LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                  ZoneId.systemDefault()))
              .duration(duration)
              .downloadStatus(EpisodeDownloadStatus.PENDING.name())
              .createdAt(LocalDateTime.now());

          if (item.getSnippet().getThumbnails() != null) {
            if (item.getSnippet().getThumbnails().getDefault() != null) {
              episodeBuilder.defaultCoverUrl(
                  item.getSnippet().getThumbnails().getDefault().getUrl());
            }
            if (item.getSnippet().getThumbnails().getMaxres() != null) {
              episodeBuilder.maxCoverUrl(item.getSnippet().getThumbnails().getMaxres().getUrl());
            }
          }

          resultEpisodes.add(episodeBuilder.build());
          if (resultEpisodes.size() >= fetchNum) {
            break;
          }
        }

        if (resultEpisodes.size() >= fetchNum || foundLastSyncedVideo) {
          break;
        }

        nextPageToken = response.getNextPageToken();
        if (nextPageToken == null) {
          break; // 没有下一页
        }
      }

      if (resultEpisodes.isEmpty()) {
        return Collections.emptyList();
      }

      // 3. 截断到精确数量
      if (resultEpisodes.size() > fetchNum) {
        return resultEpisodes.subList(0, fetchNum);
      }
      return resultEpisodes;

    } catch (Exception e) {
      throw new BusinessException(messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  // 量获取视频时长
  private String fetchVideoDurations(YouTube youtubeService, String apiKey,
      PlaylistItem item) throws IOException {
    String videoId = item.getSnippet().getResourceId().getVideoId();

    YouTube.Videos.List videoRequest = youtubeService.videos()
        .list("contentDetails")
        .setId(videoId)
        .setKey(apiKey);

    VideoListResponse videoResponse = videoRequest.execute();
    List<Video> items = videoResponse.getItems();
    if (CollectionUtils.isEmpty(items)) {
      return null;
    }

    return items.get(0).getContentDetails().getDuration();
  }

  // 批量获取时长
  private Map<String, String> fetchVideoDurationsInBatch(YouTube youtubeService,
      List<PlaylistItem> items, String apiKey) throws IOException {
    Map<String, String> durationMap = new HashMap<>();
    List<String> videoIds = items.stream()
        .map(item -> item.getSnippet().getResourceId().getVideoId())
        .collect(Collectors.toList());

    // YouTube API 一次最多接受50个ID
    for (int i = 0; i < videoIds.size(); i += 50) {
      List<String> subList = videoIds.subList(i, Math.min(i + 50, videoIds.size()));
      String idsToQuery = String.join(",", subList);

      YouTube.Videos.List videoRequest = youtubeService.videos()
          .list("contentDetails")
          .setId(idsToQuery)
          .setKey(apiKey);

      VideoListResponse videoResponse = videoRequest.execute();
      for (Video video : videoResponse.getItems()) {
        durationMap.put(video.getId(), video.getContentDetails().getDuration());
      }
    }
    return durationMap;
  }

  private Channel fetchYoutubeChannelByYoutubeChannelId(String channelId) {
    try {
      String youtubeApiKey = getYoutubeApiKey();

      YouTube youtubeService = getService();

      // 使用Channel ID获取频道的详细信息
      YouTube.Channels.List channelRequest = youtubeService.channels()
          .list("snippet,statistics,brandingSettings");
      channelRequest.setId(channelId);
      channelRequest.setKey(youtubeApiKey);

      ChannelListResponse response = channelRequest.execute();
      List<com.google.api.services.youtube.model.Channel> channels = response.getItems();

      if (ObjectUtils.isEmpty(channels)) {
        throw new BusinessException(messageSource.getMessage("youtube.channel.not.found", null, LocaleContextHolder.getLocale()));
      }

      return channels.get(0);
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException(messageSource.getMessage("youtube.fetch.channel.failed", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  private String fetchYoutubeChannelIdByUrl(String channelUrl) {
    try {
      String youtubeApiKey = getYoutubeApiKey();

      // 从URL提取handle
      String handle = getHandleFromUrl(channelUrl);
      if (handle == null) {
        throw new BusinessException(messageSource.getMessage("youtube.invalid.url", null, LocaleContextHolder.getLocale()));
      }

      YouTube youtubeService = getService();

      // 使用handle搜索以获取Channel ID
      YouTube.Search.List searchListRequest = youtubeService.search()
          .list("snippet")
          .setQ(handle) // 使用 handle 作为查询词
          .setType("channel") // 只搜索频道
          .setMaxResults(1L); // 我们只需要最相关的那个

      searchListRequest.setKey(youtubeApiKey);
      SearchListResponse response = searchListRequest.execute();
      List<SearchResult> searchResults = response.getItems();

      if (!CollectionUtils.isEmpty(searchResults)) {
        // 第一个结果就是我们想要的频道
        return searchResults.get(0).getSnippet().getChannelId();
      }
      throw new BusinessException(messageSource.getMessage("youtube.channel.not.found", null, LocaleContextHolder.getLocale()));
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException(messageSource.getMessage("youtube.fetch.channel.failed", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  private YouTube getService() throws GeneralSecurityException, IOException {
    return new YouTube.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        null // No need for HttpRequestInitializer for API key access
    ).setApplicationName(APPLICATION_NAME).build();
  }

  private String getYoutubeApiKey() {
    String youtubeApiKey = accountService.getYoutubeApiKey("0");
    if (ObjectUtils.isEmpty(youtubeApiKey)) {
      throw new BusinessException(messageSource.getMessage("youtube.api.key.not.set", null, LocaleContextHolder.getLocale()));
    }
    return youtubeApiKey;
  }

}
