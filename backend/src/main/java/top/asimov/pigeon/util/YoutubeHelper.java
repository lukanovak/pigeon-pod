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
import lombok.extern.log4j.Log4j2;
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

@Log4j2
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

  /**
   * 根据输入获取 YouTube 频道信息
   * 支持多种输入格式:
   * 1. 直接的频道 ID: UCuAXFkgsw1L7xaCfnd5JJOw
   * 2. @handle 链接: https://www.youtube.com/@StorytellerFan
   * 3. /channel/ 链接: https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw
   * @param input 频道输入（URL 或 ID）
   * @return YouTube 频道信息
   */
  public Channel fetchYoutubeChannelByUrl(String input) {
    // 首先尝试直接提取频道 ID
    String channelId = extractChannelId(input);
    
    if (channelId != null) {
      // 直接使用频道 ID 获取信息
      return fetchYoutubeChannelByYoutubeChannelId(channelId);
    } else {
      // 使用传统的 handle 搜索方式
      String resolvedChannelId = fetchYoutubeChannelIdByUrl(input);
      return fetchYoutubeChannelByYoutubeChannelId(resolvedChannelId);
    }
  }

  public String getHandleFromUrl(String channelUrl) {
    if (channelUrl == null || !channelUrl.contains("@")) {
      return null;
    }
    int atIndex = channelUrl.lastIndexOf('@');
    return channelUrl.substring(atIndex + 1);
  }

  /**
   * 检测输入是否为 YouTube 频道 ID
   * YouTube 频道 ID 格式: UC + 22个字符，总共24个字符
   * @param input 输入字符串
   * @return 如果是频道 ID 返回 true，否则返回 false
   */
  public boolean isYouTubeChannelId(String input) {
    if (input == null || input.trim().isEmpty()) {
      return false;
    }
    
    String trimmed = input.trim();
    // YouTube 频道 ID 通常以 UC 开头，总长度为 24 个字符
    // 例如: UCuAXFkgsw1L7xaCfnd5JJOw
    return trimmed.length() == 24 && 
           trimmed.startsWith("UC") && 
           trimmed.matches("^[A-Za-z0-9_-]{24}$");
  }

  /**
   * 从输入中提取频道 ID
   * 支持多种输入格式:
   * 1. 直接的频道 ID: UCuAXFkgsw1L7xaCfnd5JJOw
   * 2. 频道链接: https://www.youtube.com/@StorytellerFan
   * 3. 频道页面链接: https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw
   * @param input 输入字符串
   * @return 频道 ID，如果无法解析则返回 null
   */
  public String extractChannelId(String input) {
    if (input == null || input.trim().isEmpty()) {
      return null;
    }
    
    String trimmed = input.trim();
    
    // 1. 检查是否直接是频道 ID
    if (isYouTubeChannelId(trimmed)) {
      return trimmed;
    }
    
    // 2. 检查是否是 /channel/ 格式的链接
    if (trimmed.contains("/channel/")) {
      int channelIndex = trimmed.indexOf("/channel/");
      String channelId = trimmed.substring(channelIndex + 9); // "/channel/".length() = 9
      // 移除可能的查询参数
      int questionIndex = channelId.indexOf('?');
      if (questionIndex > 0) {
        channelId = channelId.substring(0, questionIndex);
      }
      // 移除可能的路径
      int slashIndex = channelId.indexOf('/');
      if (slashIndex > 0) {
        channelId = channelId.substring(0, slashIndex);
      }
      
      if (isYouTubeChannelId(channelId)) {
        return channelId;
      }
    }
    
    // 3. 如果不是以上格式，返回 null，让调用者使用传统的 handle 搜索方式
    return null;
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

          // 获取视频信息并检测是否为 live
          VideoInfo videoInfo = fetchVideoInfo(youtubeService, youtubeApiKey, item);
          if (videoInfo == null) {
            // 这是 live 节目，跳过
            continue;
          }
          
          String duration = videoInfo.getDuration();
          durationCache.put(item.getId(), duration);

          if (minimalDuration != null) {
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
            // 如果缓存中没有，重新获取并检测 live 状态
            VideoInfo videoInfo = fetchVideoInfo(youtubeService, youtubeApiKey, item);
            if (videoInfo == null) {
              // 这是 live 节目，跳过
              continue;
            }
            duration = videoInfo.getDuration();
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

  /**
   * 获取视频时长并检测是否为 live 节目
   * @param youtubeService YouTube 服务
   * @param apiKey API 密钥
   * @param item 播放列表项
   * @return VideoInfo 包含时长和 live 状态，如果是 live 节目返回 null
   */
  private VideoInfo fetchVideoInfo(YouTube youtubeService, String apiKey,
      PlaylistItem item) throws IOException {
    String videoId = item.getSnippet().getResourceId().getVideoId();

    YouTube.Videos.List videoRequest = youtubeService.videos()
        .list("contentDetails,snippet,liveStreamingDetails")  // 添加 snippet 和 liveStreamingDetails
        .setId(videoId)
        .setKey(apiKey);

    VideoListResponse videoResponse = videoRequest.execute();
    List<Video> videos = videoResponse.getItems();
    if (CollectionUtils.isEmpty(videos)) {
      return null;
    }

    Video video = videos.get(0);
    String liveBroadcastContent = video.getSnippet().getLiveBroadcastContent();
    
    // 检查是否为 live 内容
    if ("live".equals(liveBroadcastContent) || "upcoming".equals(liveBroadcastContent)) {
      log.info("跳过 live 节目: {} - {}", videoId, item.getSnippet().getTitle());
      return null; // 返回 null 表示这是 live 节目，应该跳过
    }
    
    // 额外检查：如果有 liveStreamingDetails，说明是 live 相关内容
    if (video.getLiveStreamingDetails() != null) {
      if (video.getLiveStreamingDetails().getScheduledStartTime() != null && 
          video.getLiveStreamingDetails().getActualEndTime() == null) {
        log.info("跳过即将开始的 live 节目: {} - {}", videoId, item.getSnippet().getTitle());
        return null;
      }
    }

    String duration = video.getContentDetails().getDuration();
    return new VideoInfo(duration, false); // false 表示不是 live
  }

  /**
   * 视频信息类，包含时长和是否为 live 的信息
   */
  private static class VideoInfo {
    private final String duration;
    private final boolean isLive;
    
    public VideoInfo(String duration, boolean isLive) {
      this.duration = duration;
      this.isLive = isLive;
    }
    
    public String getDuration() {
      return duration;
    }
    
    public boolean isLive() {
      return isLive;
    }
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

  /**
   * 检测视频是否为 live 节目
   * @param videoId 视频ID
   * @return 如果是 live 节目返回 true，否则返回 false
   */
  public boolean isLiveVideo(String videoId) {
    try {
      String youtubeApiKey = getYoutubeApiKey();
      YouTube youtubeService = getService();

      // 使用 YouTube Data API 获取视频详细信息
      YouTube.Videos.List videoRequest = youtubeService.videos()
          .list("snippet,liveStreamingDetails")
          .setId(videoId)
          .setKey(youtubeApiKey);

      VideoListResponse videoResponse = videoRequest.execute();
      List<Video> videos = videoResponse.getItems();
      
      if (CollectionUtils.isEmpty(videos)) {
        // 视频不存在，可能已被删除
        return false;
      }

      Video video = videos.get(0);
      String liveBroadcastContent = video.getSnippet().getLiveBroadcastContent();
      
      // 检查是否为 live 内容
      if ("live".equals(liveBroadcastContent) || "upcoming".equals(liveBroadcastContent)) {
        return true;
      }
      
      // 额外检查：如果有 liveStreamingDetails，说明是 live 相关内容
      if (video.getLiveStreamingDetails() != null) {
        // 如果有预定开始时间但没有实际结束时间，可能是即将开始或正在进行的直播
        if (video.getLiveStreamingDetails().getScheduledStartTime() != null && 
            video.getLiveStreamingDetails().getActualEndTime() == null) {
          return true;
        }
      }
      
      return false;
      
    } catch (Exception e) {
      // API 调用失败时，记录警告但不阻止下载
      // 可能是网络问题或 API 配额问题
      return false;
    }
  }

  private String getYoutubeApiKey() {
    String youtubeApiKey = accountService.getYoutubeApiKey("0");
    if (ObjectUtils.isEmpty(youtubeApiKey)) {
      throw new BusinessException(messageSource.getMessage("youtube.api.key.not.set", null, LocaleContextHolder.getLocale()));
    }
    return youtubeApiKey;
  }

}
