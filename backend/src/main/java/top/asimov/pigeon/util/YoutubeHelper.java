package top.asimov.pigeon.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.google.api.services.youtube.model.ThumbnailDetails;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import top.asimov.pigeon.constant.EpisodeStatus;
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
   * 根据输入获取 YouTube 频道信息 支持多种输入格式: 1. 直接的频道 ID: UCuAXFkgsw1L7xaCfnd5JJOw 2. @handle 链接:
   * https://www.youtube.com/@StorytellerFan 3. /channel/ 链接:
   * https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw
   *
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

  /**
   * 从频道 URL 中提取 handle 例如: https://www.youtube.com/@StorytellerFan -> StorytellerFan
   *
   * @param channelUrl 频道 URL
   * @return 提取的 handle，如果无法提取则返回 null
   */
  public String getHandleFromUrl(String channelUrl) {
    if (channelUrl == null || !channelUrl.contains("@")) {
      return null;
    }
    int atIndex = channelUrl.lastIndexOf('@');
    int slashIndex = channelUrl.indexOf('/', atIndex);
    if (slashIndex > 0) {
      return channelUrl.substring(atIndex + 1, slashIndex);
    }
    return channelUrl.substring(atIndex + 1);
  }

  /**
   * 检测输入是否为 YouTube 频道 ID YouTube 频道 ID 格式: UC + 22个字符，总共24个字符
   *
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
   * 从输入中提取频道 ID 支持多种输入格式: 1. 直接的频道 ID: UCuAXFkgsw1L7xaCfnd5JJOw 2. 频道链接:
   * https://www.youtube.com/@StorytellerFan 3. 频道页面链接:
   * https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw
   *
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

  /**
   * 抓取指定 YouTube 频道的视频列表
   *
   * @param channelId         频道 ID
   * @param lastSyncedVideoId 上次同步的视频 ID（用于增量抓取）
   * @param fetchNum          本次抓取的视频数量
   * @param containKeywords   标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords   标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration   最小视频时长（分钟），null 表示不限制
   * @return 视频列表
   */
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

          // 使用提取的关键词过滤方法
          if (!matchesKeywordFilter(title, containKeywords, excludeKeywords)) {
            continue;
          }

          // 获取视频信息并检测是否为 live
          String duration = fetchVideoDurationForPlaylistItem(youtubeService, youtubeApiKey, item);
          if (duration == null) {
            // 这是 live 节目，跳过
            continue;
          }

          durationCache.put(item.getId(), duration);

          // 使用提取的时长过滤方法
          if (!matchesDurationFilter(duration, minimalDuration)) {
            continue;
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
            duration = fetchVideoDurationForPlaylistItem(youtubeService, youtubeApiKey, item);
            if (duration == null) {
              // 这是 live 节目，跳过
              continue;
            }
          }
          
          // 使用提取的Episode构建方法
          Episode episode = buildEpisodeFromPlaylistItem(item, channelId, duration);
          resultEpisodes.add(episode);
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

      // 3. 使用提取的结果处理方法
      return processResultList(resultEpisodes, fetchNum);

    } catch (Exception e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 使用搜索 API 抓取指定 YouTube 频道在指定日期之前的视频列表
   *
   * @param channelId         频道 ID
   * @param fetchNum          本次抓取的视频数量
   * @param publishedBefore   发布日期截止时间（格式：yyyy-MM-dd）
   * @param containKeywords   标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords   标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration   最小视频时长（分钟），null 表示不限制
   * @return 视频列表，按发布时间倒序排列
   */
  public List<Episode> searchYoutubeChannelVideos(String channelId, int fetchNum, 
      LocalDateTime publishedBefore, String containKeywords, String excludeKeywords, Integer minimalDuration) {
    try {
      YouTube youtubeService = getService();
      String youtubeApiKey = getYoutubeApiKey();

      List<Episode> resultEpisodes = new ArrayList<>();
      String nextPageToken = "";
      
      while (resultEpisodes.size() < fetchNum) {
        long pageSize = Math.min(50L, fetchNum - resultEpisodes.size()); // API 单页最大 50
        
        // 构建搜索请求
        YouTube.Search.List searchRequest = youtubeService.search()
            .list("snippet")
            .setChannelId(channelId)
            .setType("video")
            .setOrder("date") // 按日期倒序排列
            .setMaxResults(pageSize)
            .setKey(youtubeApiKey);
        
        // 设置发布日期限制
        try {
          //将 publishedBefore 转换回 UTC Instant
          Instant utcInstant = publishedBefore.atZone(ZoneId.systemDefault()).toInstant();
          // 将UTC Instant格式化为API需要的字符串，格式为 "2020-11-05T10:17:45Z"
          String isoDateTime = DateTimeFormatter.ISO_INSTANT.format(utcInstant);
          searchRequest.setPublishedBefore(new DateTime(isoDateTime));
        } catch (Exception e) {
          log.warn("日期格式解析失败: {}", publishedBefore);
          throw new BusinessException(
              messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
                  LocaleContextHolder.getLocale()));
        }
        
        // 设置分页token
        if (StringUtils.hasText(nextPageToken)) {
          searchRequest.setPageToken(nextPageToken);
        }

        SearchListResponse searchResponse = searchRequest.execute();
        List<SearchResult> searchResults = searchResponse.getItems();
        
        if (searchResults == null || searchResults.isEmpty()) {
          break; // 没有更多数据
        }

        // 处理搜索结果
        List<SearchResult> candidates = new ArrayList<>();
        for (SearchResult result : searchResults) {
          String title = result.getSnippet().getTitle();

          // 使用提取的关键词过滤方法
          if (!matchesKeywordFilter(title, containKeywords, excludeKeywords)) {
            continue;
          }

          candidates.add(result);
        }

        // 获取视频详细信息并应用时长过滤
        for (SearchResult result : candidates) {
          String videoId = result.getId().getVideoId();
          
          // 获取视频详细信息
          String duration = fetchVideoDuration(youtubeService, youtubeApiKey, videoId,
              result.getSnippet().getTitle());
          if (duration == null) {
            // 这是 live 节目，跳过
            continue;
          }

          // 使用提取的时长过滤方法
          if (!matchesDurationFilter(duration, minimalDuration)) {
            continue;
          }

          // 使用提取的Episode构建方法
          Episode episode = buildEpisodeFromSearchResult(result, channelId, duration);
          resultEpisodes.add(episode);
          
          if (resultEpisodes.size() >= fetchNum) {
            break;
          }
        }

        if (resultEpisodes.size() >= fetchNum) {
          break;
        }

        nextPageToken = searchResponse.getNextPageToken();
        if (nextPageToken == null) {
          break; // 没有下一页
        }
      }

      // 使用提取的结果处理方法
      return processResultList(resultEpisodes, fetchNum);

    } catch (Exception e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 检查标题是否匹配关键词过滤条件
   *
   * @param title           视频标题
   * @param containKeywords 必须包含的关键词，多个关键词用空格分隔
   * @param excludeKeywords 必须排除的关键词，多个关键词用空格分隔
   * @return true 如果标题匹配过滤条件，false 否则
   */
  private boolean matchesKeywordFilter(String title, String containKeywords, String excludeKeywords) {
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
        return false;
      }
    }

    // 处理 excludeKeywords，支持空格分割的多个关键词，包含任意一个就排除
    if (StringUtils.hasLength(excludeKeywords)) {
      String[] keywords = excludeKeywords.trim().split("\\s+");
      for (String keyword : keywords) {
        if (title.toLowerCase().contains(keyword.toLowerCase())) {
          return false; // 包含排除关键词，不匹配
        }
      }
    }

    return true;
  }

  /**
   * 检查视频时长是否满足最小时长要求
   *
   * @param duration        视频时长（ISO 8601格式）
   * @param minimalDuration 最小时长要求（分钟），null 表示不限制
   * @return true 如果满足时长要求，false 否则
   */
  private boolean matchesDurationFilter(String duration, Integer minimalDuration) {
    if (minimalDuration == null) {
      return true; // 没有时长限制
    }
    
    if (!StringUtils.hasText(duration)) {
      return false; // 没有时长信息
    }
    
    try {
      long minutes = Duration.parse(duration).toMinutes();
      return minutes >= minimalDuration;
    } catch (Exception e) {
      log.warn("解析视频时长失败: {}", duration);
      return false;
    }
  }

  /**
   * 从PlaylistItem构建Episode对象
   *
   * @param item      播放列表项
   * @param channelId 频道ID
   * @param duration  视频时长
   * @return Episode对象
   */
  private Episode buildEpisodeFromPlaylistItem(PlaylistItem item, String channelId, String duration) {
    PlaylistItemSnippet snippet = item.getSnippet();
    EpisodeBuilder builder = newEpisodeBuilder(
            snippet.getResourceId().getVideoId(),
            channelId,
            snippet.getTitle(),
            snippet.getDescription(),
            snippet.getPublishedAt(),
            duration)
        .position(snippet.getPosition() != null ? snippet.getPosition().intValue() : 0);

    applyThumbnails(builder, snippet.getThumbnails());
    return builder.build();
  }

  /**
   * 从SearchResult构建Episode对象
   *
   * @param result    搜索结果项
   * @param channelId 频道ID
   * @param duration  视频时长
   * @return Episode对象
   */
  private Episode buildEpisodeFromSearchResult(SearchResult result, String channelId, String duration) {
    SearchResultSnippet snippet = result.getSnippet();
    EpisodeBuilder builder = newEpisodeBuilder(
            result.getId().getVideoId(),
            channelId,
            snippet.getTitle(),
            snippet.getDescription(),
            snippet.getPublishedAt(),
            duration)
        .position(0); // 搜索结果没有position信息，设为0

    applyThumbnails(builder, snippet.getThumbnails());
    return builder.build();
  }

  /**
   * 处理结果列表，确保不超过指定数量
   *
   * @param episodes 结果列表
   * @param fetchNum 期望的数量
   * @return 处理后的结果列表
   */
  private List<Episode> processResultList(List<Episode> episodes, int fetchNum) {
    if (episodes.isEmpty()) {
      return Collections.emptyList();
    }
    
    // 截断到精确数量
    if (episodes.size() > fetchNum) {
      return episodes.subList(0, fetchNum);
    }
    
    return episodes;
  }

  /**
   * 获取播放列表项的视频时长，并排除 live 节目
   */
  private String fetchVideoDurationForPlaylistItem(YouTube youtubeService, String apiKey,
      PlaylistItem item) throws IOException {
    PlaylistItemSnippet snippet = item.getSnippet();
    if (snippet == null || snippet.getResourceId() == null) {
      log.warn("播放列表项缺少必要的元数据，跳过: {}", item.getId());
      return null;
    }

    String videoId = snippet.getResourceId().getVideoId();
    return fetchVideoDuration(youtubeService, apiKey, videoId, snippet.getTitle());
  }

  /**
   * 获取视频时长，并排除 live 节目
   */
  private String fetchVideoDuration(YouTube youtubeService, String apiKey, String videoId,
      String fallbackTitle) throws IOException {
    Video video = fetchVideoDetails(youtubeService, apiKey, videoId);
    if (video == null) {
      return null;
    }

    String title = resolveTitle(video, fallbackTitle);
    if (shouldSkipLiveContent(video, videoId, title)) {
      return null;
    }

    if (video.getContentDetails() == null ||
        !StringUtils.hasText(video.getContentDetails().getDuration())) {
      log.warn("无法读取视频时长: {} - {}", videoId, title);
      return null;
    }

    return video.getContentDetails().getDuration();
  }

  private Video fetchVideoDetails(YouTube youtubeService, String apiKey, String videoId)
      throws IOException {
    VideoListResponse videoResponse = youtubeService.videos()
        .list("contentDetails,snippet,liveStreamingDetails")
        .setId(videoId)
        .setKey(apiKey)
        .execute();

    List<Video> videos = videoResponse.getItems();
    if (CollectionUtils.isEmpty(videos)) {
      return null;
    }

    return videos.get(0);
  }

  private String resolveTitle(Video video, String fallbackTitle) {
    if (video.getSnippet() != null && StringUtils.hasText(video.getSnippet().getTitle())) {
      return video.getSnippet().getTitle();
    }
    return fallbackTitle;
  }

  private boolean shouldSkipLiveContent(Video video, String videoId, String title) {
    String liveBroadcastContent = video.getSnippet() != null
        ? video.getSnippet().getLiveBroadcastContent()
        : null;

    if ("live".equals(liveBroadcastContent) || "upcoming".equals(liveBroadcastContent)) {
      log.info("跳过 live 节目: {} - {}", videoId, title);
      return true;
    }

    if (video.getLiveStreamingDetails() != null &&
        video.getLiveStreamingDetails().getScheduledStartTime() != null &&
        video.getLiveStreamingDetails().getActualEndTime() == null) {
      log.info("跳过即将开始的 live 节目: {} - {}", videoId, title);
      return true;
    }

    return false;
  }

  private EpisodeBuilder newEpisodeBuilder(String videoId, String channelId, String title,
      String description, DateTime publishedAt, String duration) {
    return Episode.builder()
        .id(videoId)
        .channelId(channelId)
        .title(title)
        .description(description)
        .publishedAt(convertToLocalDateTime(publishedAt))
        .duration(duration)
        .downloadStatus(EpisodeStatus.PENDING.name())
        .createdAt(LocalDateTime.now());
  }

  private void applyThumbnails(EpisodeBuilder builder, ThumbnailDetails thumbnails) {
    if (thumbnails == null) {
      return;
    }

    if (thumbnails.getDefault() != null) {
      builder.defaultCoverUrl(thumbnails.getDefault().getUrl());
    }

    String maxCoverUrl = null;
    if (thumbnails.getMaxres() != null) {
      maxCoverUrl = thumbnails.getMaxres().getUrl();
    } else if (thumbnails.getStandard() != null) {
      maxCoverUrl = thumbnails.getStandard().getUrl();
    } else if (thumbnails.getHigh() != null) {
      maxCoverUrl = thumbnails.getHigh().getUrl();
    } else if (thumbnails.getMedium() != null) {
      maxCoverUrl = thumbnails.getMedium().getUrl();
    } else if (thumbnails.getDefault() != null) {
      maxCoverUrl = thumbnails.getDefault().getUrl();
    }

    if (StringUtils.hasText(maxCoverUrl)) {
      builder.maxCoverUrl(maxCoverUrl);
    }
  }

  private LocalDateTime convertToLocalDateTime(DateTime publishedAt) {
    if (publishedAt == null) {
      return LocalDateTime.now();
    }
    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(publishedAt.getValue()),
        ZoneId.systemDefault());
  }

  /**
   * 使用频道 ID 获取频道详细信息
   *
   * @param channelId 频道 ID
   * @return 频道信息
   */
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
        throw new BusinessException(messageSource.getMessage("youtube.channel.not.found", null,
            LocaleContextHolder.getLocale()));
      }

      return channels.get(0);
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.channel.failed", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 使用频道 URL 或 handle 搜索并获取频道 ID
   *
   * @param channelUrl 频道 URL 或 handle
   * @return 频道 ID
   */
  private String fetchYoutubeChannelIdByUrl(String channelUrl) {
    try {
      String youtubeApiKey = getYoutubeApiKey();

      // 从URL提取handle
      String handle = getHandleFromUrl(channelUrl);
      if (handle == null) {
        throw new BusinessException(
            messageSource.getMessage("youtube.invalid.url", null, LocaleContextHolder.getLocale()));
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
      throw new BusinessException(messageSource.getMessage("youtube.channel.not.found", null,
          LocaleContextHolder.getLocale()));
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.channel.failed", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 创建并返回 YouTube 服务对象
   *
   * @return YouTube 服务对象
   * @throws GeneralSecurityException 安全异常
   * @throws IOException              IO 异常
   */
  private YouTube getService() throws GeneralSecurityException, IOException {
    return new YouTube.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        JSON_FACTORY,
        null // No need for HttpRequestInitializer for API key access
    ).setApplicationName(APPLICATION_NAME).build();
  }

  /**
   * 检测视频是否为 live 节目
   *
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

  /**
   * 获取 YouTube API Key
   *
   * @return YouTube API Key
   */
  private String getYoutubeApiKey() {
    String youtubeApiKey = accountService.getYoutubeApiKey("0");
    if (ObjectUtils.isEmpty(youtubeApiKey)) {
      throw new BusinessException(messageSource.getMessage("youtube.api.key.not.set", null,
          LocaleContextHolder.getLocale()));
    }
    return youtubeApiKey;
  }

}
