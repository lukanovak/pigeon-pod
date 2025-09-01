package top.asimov.pigeon.util;

import cn.dev33.satoken.stp.StpUtil;
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

  public YoutubeHelper(AccountService accountService) {
    this.accountService = accountService;
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
      Long fetchNum, String containKeywords, String excludeKeywords, Integer minimalDuration) {
    try {
      YouTube youtubeService = getService();
      String youtubeApiKey = getYoutubeApiKey();

      // 1. 获取 Uploads 播放列表 ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0).getContentDetails()
          .getRelatedPlaylists().getUploads();

      // 2. 循环获取视频列表，直到找到上次同步过的视频或者达到 fetchNum 限制
      List<PlaylistItem> playlistItems = new ArrayList<>();
      String nextPageToken = "";
      boolean foundLastSyncedVideo = false;

      long videosRemaining = Math.min(fetchNum, 1000L); // YouTube API对某些请求有隐性上限，1000是个合理值
      do {
        Long pageSize = Math.min(50L, videosRemaining); // API允许的每页最大值为50
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
            .list("snippet") // 暂时只需要 snippet
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(pageSize)
            .setPageToken(nextPageToken)
            .setKey(youtubeApiKey);

        PlaylistItemListResponse response = request.execute();
        if (response.getItems() == null) {
          break;
        }

        for (PlaylistItem item : response.getItems()) {
          String title = item.getSnippet().getTitle();
          if (StringUtils.hasLength(containKeywords) && !title.contains(containKeywords)) {
            continue; // 不包含指定关键词，跳过
          }
          if (StringUtils.hasLength(excludeKeywords) && title.contains(excludeKeywords)) {
            continue; // 包含排除关键词，跳过
          }

          String currentVideoId = item.getSnippet().getResourceId().getVideoId();

          // 如果找到了上次同步的ID，说明之后的全是旧视频，停止查找
          if (currentVideoId.equals(lastSyncedVideoId)) {
            foundLastSyncedVideo = true;
            break;
          }
          playlistItems.add(item);
        }

        videosRemaining -= playlistItems.size();

        if (foundLastSyncedVideo) {
          break;
        }

        nextPageToken = response.getNextPageToken();
      } while (nextPageToken != null & videosRemaining > 0);

      if (playlistItems.isEmpty()) {
        return Collections.emptyList(); // 没有新视频
      }

      // 3. 性能优化：批量获取所有新视频的详情（时长等）
      Map<String, String> videoDurations = fetchVideoDurationsInBatch(youtubeService, playlistItems,
          youtubeApiKey);

      // 4. 将 PlaylistItem 转换为 Episode 对象
      List<Episode> newEpisodes = new ArrayList<>();
      for (PlaylistItem item : playlistItems) {
        // 过滤掉时长小于 minimalDuration 的视频
        String duration = videoDurations.get(item.getSnippet().getResourceId().getVideoId());
        if (duration == null) {
          continue; // 如果没有获取到时长信息，跳过该视频
        }
        long minutes = Duration.parse(duration).toMinutes();
        if (minimalDuration != null && minutes < minimalDuration) {
          continue; // 时长不符合要求，跳过
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
            episodeBuilder.defaultCoverUrl(item.getSnippet().getThumbnails().getDefault().getUrl());
          }
          if (item.getSnippet().getThumbnails().getMaxres() != null) {
            episodeBuilder.maxCoverUrl(item.getSnippet().getThumbnails().getMaxres().getUrl());
          }
        }

        newEpisodes.add(episodeBuilder.build());
      }

      return newEpisodes;

    } catch (Exception e) {
      throw new BusinessException("获取频道新视频时发生错误: " + e.getMessage());
    }
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
        throw new BusinessException("未找到频道信息");
      }

      return channels.get(0);
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道信息失败" + e.getMessage());
    }
  }

  private String fetchYoutubeChannelIdByUrl(String channelUrl) {
    try {
      String youtubeApiKey = getYoutubeApiKey();

      // 从URL提取handle
      String handle = getHandleFromUrl(channelUrl);
      if (handle == null) {
        throw new BusinessException("无效的YouTube频道URL");
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
      throw new BusinessException("未找到频道信息");
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道信息失败" + e.getMessage());
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
    String loginId = (String) StpUtil.getLoginId();
    String youtubeApiKey = accountService.getYoutubeApiKey(loginId);
    if (ObjectUtils.isEmpty(youtubeApiKey)) {
      throw new BusinessException("YouTube API key is not set, please set it in the user setting.");
    }
    return youtubeApiKey;
  }

}
