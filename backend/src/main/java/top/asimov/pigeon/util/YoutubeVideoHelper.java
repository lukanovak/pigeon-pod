package top.asimov.pigeon.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.ThumbnailDetails;
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
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.EpisodeStatus;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Episode.EpisodeBuilder;
import top.asimov.pigeon.service.AccountService;

@Log4j2
@Component
public class YoutubeVideoHelper {

  private static final String APPLICATION_NAME = "My YouTube App";
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  private final AccountService accountService;
  private final MessageSource messageSource;
  private final YouTube youtubeService;

  public YoutubeVideoHelper(AccountService accountService, MessageSource messageSource) {
    this.accountService = accountService;
    this.messageSource = messageSource;

    try {
      this.youtubeService = new YouTube.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          JSON_FACTORY,
          null // No need for HttpRequestInitializer for API key access
      ).setApplicationName(APPLICATION_NAME).build();
    } catch (GeneralSecurityException | IOException e) {
      log.error("Failed to initialize YouTube service", e);
      throw new RuntimeException("Failed to initialize YouTube service", e);
    }
  }

  /* ------------------------ Public API ----------------------- */

  /**
   * 抓取指定 YouTube 频道的视频列表，默认不使用任何过滤条件
   *
   * @param channelId 频道 ID
   * @param fetchNum  本次抓取的视频数量
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum) {
    return fetchYoutubeChannelVideos(channelId, fetchNum, null, null, null, null);
  }

  /**
   * 抓取指定 YouTube 频道的视频列表，支持关键词和时长过滤
   *
   * @param channelId       频道 ID
   * @param fetchNum        本次抓取的视频数量
   * @param containKeywords 标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords 标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration 最小视频时长（分钟），null 表示不限制
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum,
      String containKeywords, String excludeKeywords, Integer minimalDuration) {
    return fetchYoutubeChannelVideos(channelId, fetchNum, null, containKeywords, excludeKeywords,
        minimalDuration);
  }

  /**
   * 增量抓取指定 YouTube 频道的视频列表
   *
   * @param channelId         频道 ID
   * @param lastSyncedVideoId 上次同步的视频 ID（用于增量抓取）
   * @param fetchNum          本次抓取的视频数量
   * @param containKeywords   标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords   标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration   最小视频时长（分钟），null 表示不限制
   * @return 视频列表
   */
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum,
      String lastSyncedVideoId,
      String containKeywords, String excludeKeywords, Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null, fetchNum, containKeywords, excludeKeywords, minimalDuration,
        (fetchNumLong) -> Math.min(50L, fetchNumLong), // API 单页最大 50
        Integer.MAX_VALUE // 不限制页数
    );

    Predicate<PlaylistItem> stopCondition = item -> {
      String currentVideoId = item.getSnippet().getResourceId().getVideoId();
      return currentVideoId.equals(lastSyncedVideoId);
    };

    Predicate<PlaylistItem> skipCondition = item -> false; // 不跳过任何视频

    return fetchVideosWithConditions(config, stopCondition, skipCondition);
  }

  /**
   * 抓取指定 YouTube 频道在指定日期之前的视频列表
   *
   * @param channelId       频道 ID
   * @param fetchNum        本次抓取的视频数量
   * @param publishedBefore 发布日期截止时间
   * @param containKeywords 标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords 标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration 最小视频时长（分钟），null 表示不限制
   * @return 视频列表，按发布时间倒序排列
   */
  public List<Episode> fetchYoutubeChannelVideosBeforeDate(String channelId, int fetchNum,
      LocalDateTime publishedBefore, String containKeywords, String excludeKeywords,
      Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        channelId, null, fetchNum, containKeywords, excludeKeywords, minimalDuration,
        (fetchNumLong) -> 50L, // API 单页最大 50，获取更多数据以便过滤
        20 // 限制最大检查页数，避免无限循环
    );

    Predicate<PlaylistItem> stopCondition = item -> false; // 不因特定视频而停止

    Predicate<PlaylistItem> skipCondition = item -> {
      LocalDateTime videoPublishedAt = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
          ZoneId.systemDefault());
      return videoPublishedAt.isAfter(publishedBefore); // 跳过太新的视频
    };

    log.info("开始获取频道 {} 在 {} 之前的视频，目标数量: {}", channelId, publishedBefore, fetchNum);
    List<Episode> result = fetchVideosWithConditions(config, stopCondition, skipCondition);
    log.info("最终获取到 {} 个符合条件的视频", result.size());
    return result;
  }

  /**
   * 抓取指定播放列表的视频，默认不使用任何过滤条件
   *
   * @param playlistId 播放列表 ID
   * @param fetchNum   本次抓取的视频数量
   * @return 视频列表
   */
  public List<Episode> fetchPlaylistVideos(String playlistId, int fetchNum) {
    return fetchPlaylistVideos(playlistId, fetchNum, null, null, null, null);
  }

  /**
   * 抓取指定播放列表的视频，支持关键词和时长过滤
   *
   * @param playlistId      播放列表 ID
   * @param fetchNum        本次抓取的视频数量
   * @param containKeywords 标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords 标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration 最小视频时长（分钟），null 表示不限制
   * @return 视频列表
   */
  public List<Episode> fetchPlaylistVideos(String playlistId, int fetchNum,
      String containKeywords, String excludeKeywords, Integer minimalDuration) {
    return fetchPlaylistVideos(playlistId, fetchNum, null, containKeywords, excludeKeywords,
        minimalDuration);
  }

  /**
   * 增量抓取指定播放列表的视频
   *
   * @param playlistId        播放列表 ID
   * @param fetchNum          本次抓取的视频数量
   * @param lastSyncedVideoId 上次同步的视频 ID（用于增量抓取）
   * @param containKeywords   标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords   标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration   最小视频时长（分钟），null 表示不限制
   * @return 视频列表
   */
  public List<Episode> fetchPlaylistVideos(String playlistId, int fetchNum,
      String lastSyncedVideoId, String containKeywords, String excludeKeywords,
      Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        null, playlistId, fetchNum, containKeywords, excludeKeywords, minimalDuration,
        (fetchNumLong) -> Math.min(50L, fetchNumLong), // API 单页最大 50
        Integer.MAX_VALUE // 不限制页数
    );

    Predicate<PlaylistItem> stopCondition = item -> {
      String currentVideoId = item.getSnippet().getResourceId().getVideoId();
      return currentVideoId.equals(lastSyncedVideoId);
    };

    Predicate<PlaylistItem> skipCondition = item -> false; // 不跳过任何视频

    return fetchVideosWithConditions(config, stopCondition, skipCondition);
  }

  /**
   * 抓取指定播放列表在指定日期之前的视频
   *
   * @param playlistId      播放列表 ID
   * @param fetchNum        本次抓取的视频数量
   * @param publishedBefore 发布日期截止时间
   * @param containKeywords 标题必须包含的关键词，多个关键词用空格分隔，包含一个即匹配
   * @param excludeKeywords 标题必须不包含的关键词，多个关键词用空格分隔，包含一个即排除
   * @param minimalDuration 最小视频时长（分钟），null 表示不限制
   * @return 视频列表，按发布时间倒序排列
   */
  public List<Episode> fetchPlaylistVideosBeforeDate(String playlistId, int fetchNum,
      LocalDateTime publishedBefore, String containKeywords, String excludeKeywords,
      Integer minimalDuration) {
    VideoFetchConfig config = new VideoFetchConfig(
        null, playlistId, fetchNum, containKeywords, excludeKeywords, minimalDuration,
        (fetchNumLong) -> 50L, // API 单页最大 50，获取更多数据以便过滤
        20 // 限制最大检查页数，避免无限循环
    );

    Predicate<PlaylistItem> stopCondition = item -> false; // 不因特定视频而停止

    Predicate<PlaylistItem> skipCondition = item -> {
      LocalDateTime videoPublishedAt = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
          ZoneId.systemDefault());
      return videoPublishedAt.isAfter(publishedBefore); // 跳过太新的视频
    };

    log.info("开始获取播放列表 {} 在 {} 之前的视频，目标数量: {}", playlistId, publishedBefore,
        fetchNum);
    List<Episode> result = fetchVideosWithConditions(config, stopCondition, skipCondition);
    log.info("最终获取到 {} 个符合条件的视频", result.size());
    return result;
  }
  /* ------------------------ Public API ----------------------- */


  /* ------------------------ Fetch Router ----------------------- */

  /**
   * 统一的视频抓取方法，根据配置自动选择抓取源（频道或播放列表）
   *
   * @param config        抓取配置
   * @param stopCondition 停止条件，返回true时停止抓取
   * @param skipCondition 跳过条件，返回true时跳过当前视频
   * @return 抓取到的视频列表
   */
  private List<Episode> fetchVideosWithConditions(VideoFetchConfig config,
      Predicate<PlaylistItem> stopCondition,
      Predicate<PlaylistItem> skipCondition) {
    try {
      String youtubeApiKey = accountService.getYoutubeApiKey();

      // 根据配置选择播放列表ID来源
      String playlistId;
      if (config.playlistId() != null) {
        // 直接使用提供的播放列表ID
        playlistId = config.playlistId();
      } else if (config.channelId() != null) {
        // 通过频道ID获取上传播放列表ID
        playlistId = getUploadsPlaylistId(config.channelId(), youtubeApiKey);
      } else {
        throw new IllegalArgumentException("必须提供 channelId 或 playlistId 中的一个");
      }

      return fetchVideosFromPlaylist(playlistId, config, stopCondition, skipCondition);
    } catch (Exception e) {
      throw new BusinessException(
          messageSource.getMessage("youtube.fetch.videos.error", new Object[]{e.getMessage()},
              LocaleContextHolder.getLocale()));
    }
  }
  /* ------------------------ Fetch Router ----------------------- */


  /* ------------------------ Core Fetch Function ----------------------- */

  /**
   * 从指定播放列表抓取视频的核心实现方法
   *
   * @param playlistId    播放列表 ID
   * @param config        抓取配置
   * @param stopCondition 停止条件，返回true时停止抓取
   * @param skipCondition 跳过条件，返回true时跳过当前视频
   * @return 抓取到的视频列表
   */
  private List<Episode> fetchVideosFromPlaylist(String playlistId, VideoFetchConfig config,
      Predicate<PlaylistItem> stopCondition,
      Predicate<PlaylistItem> skipCondition) throws IOException {
    String youtubeApiKey = accountService.getYoutubeApiKey();
    List<Episode> resultEpisodes = new ArrayList<>();
    String nextPageToken = "";
    int currentPage = 0;
    boolean shouldStop = false;

    while (resultEpisodes.size() < config.fetchNum() &&
        currentPage < config.maxPagesToCheck() && !shouldStop) {

      long pageSize = config.pageSizeCalculator().apply((long) config.fetchNum());

      PlaylistItemListResponse response = fetchPlaylistPage(
          playlistId, pageSize, nextPageToken, youtubeApiKey);

      List<PlaylistItem> pageItems = response.getItems();
      if (pageItems == null || pageItems.isEmpty()) {
        log.info("没有更多视频数据，停止抓取");
        break;
      }

      currentPage++;
      if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
        log.info("处理第 {} 页，获取到 {} 个视频项", currentPage, pageItems.size());
      }

      for (PlaylistItem item : pageItems) {
        // 检查停止条件
        if (stopCondition.test(item)) {
          shouldStop = true;
          break;
        }

        // 检查跳过条件
        if (skipCondition.test(item)) {
          continue;
        }

        VideoProcessResult processResult = processVideoItem(
            item, config, youtubeApiKey, resultEpisodes);

        if (processResult.shouldBreak()) {
          break;
        }

        if (!processResult.shouldContinue()) {
          continue;
        }
      }

      nextPageToken = response.getNextPageToken();
      if (nextPageToken == null) {
        if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
          log.info("已到达播放列表末尾");
        }
        break;
      }
    }

    if (currentPage >= config.maxPagesToCheck()
        && config.maxPagesToCheck() < Integer.MAX_VALUE) {
      log.warn("已检查 {} 页视频，停止继续搜索", config.maxPagesToCheck());
    }

    return processResultList(resultEpisodes, config.fetchNum());
  }
  /* ------------------------ Core Fetch Function ----------------------- */

  /* ------------------------ Util Functions ----------------------- */

  /**
   * 获取频道的上传播放列表ID
   */
  private String getUploadsPlaylistId(String channelId, String youtubeApiKey) throws IOException {
    YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
    channelRequest.setId(channelId).setKey(youtubeApiKey);
    ChannelListResponse channelResponse = channelRequest.execute();
    return channelResponse.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();
  }

  /**
   * 获取播放列表的一页数据
   */
  private PlaylistItemListResponse fetchPlaylistPage(String playlistId, long pageSize,
      String nextPageToken, String youtubeApiKey) throws IOException {
    YouTube.PlaylistItems.List request = youtubeService.playlistItems()
        .list("snippet")
        .setPlaylistId(playlistId)
        .setMaxResults(pageSize)
        .setPageToken(nextPageToken)
        .setKey(youtubeApiKey);
    return request.execute();
  }

  /**
   * 处理单个视频
   */
  private VideoProcessResult processVideoItem(PlaylistItem item, VideoFetchConfig config,
      String youtubeApiKey, List<Episode> resultEpisodes) throws IOException {
    String title = item.getSnippet().getTitle();

    // 关键词过滤（使用 PlaylistItem 的标题，避免为明显不匹配的项额外请求 API）
    if (!matchesKeywordFilter(title, config.containKeywords(), config.excludeKeywords())) {
      return new VideoProcessResult(false, false);
    }

    // 仅调用一次视频详情查询，并基于 Video 构建 Episode
    String videoId = item.getSnippet().getResourceId().getVideoId();
    Video video = fetchVideoDetails(youtubeService, youtubeApiKey, videoId);
    if (video == null || video.getSnippet() == null) {
      return new VideoProcessResult(false, false);
    }

    // 跳过直播或即将直播的视频
    if (shouldSkipLiveContent(video)) {
      return new VideoProcessResult(false, false);
    }

    // 从 Video 获取时长并做过滤
    String duration = (video.getContentDetails() != null)
        ? video.getContentDetails().getDuration()
        : null;
    if (!StringUtils.hasText(duration)) {
      log.warn("无法读取视频时长: {} - {}", videoId, video.getSnippet().getTitle());
      return new VideoProcessResult(false, false);
    }

    if (!matchesDurationFilter(duration, config.minimalDuration())) {
      return new VideoProcessResult(false, false);
    }

    // 构建 Episode：优先使用传入的 channelId，否则使用 Video.snippet.channelId（而非 PlaylistItem）
    String channelId = config.channelId() != null ? config.channelId() : video.getSnippet()
        .getChannelId();
    Episode episode = buildEpisodeFromVideo(video, channelId, duration);
    resultEpisodes.add(episode);

    if (config.maxPagesToCheck() < Integer.MAX_VALUE) {
      LocalDateTime publishedAt = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(video.getSnippet().getPublishedAt().getValue()),
          ZoneId.systemDefault());
      log.info("添加符合条件的视频: {} (发布于: {})", video.getSnippet().getTitle(), publishedAt);
    }

    // 检查是否已达到目标数量
    if (resultEpisodes.size() >= config.fetchNum()) {
      return new VideoProcessResult(false, true);
    }

    return new VideoProcessResult(true, false);
  }

  /**
   * 基于 Video 详情构建 Episode（优先于 PlaylistItem 数据）
   */
  private Episode buildEpisodeFromVideo(Video video, String channelId, String duration) {
    LocalDateTime publishedAt = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(video.getSnippet().getPublishedAt().getValue()),
        ZoneId.systemDefault());

    EpisodeBuilder builder = Episode.builder()
        .id(video.getId())
        .channelId(channelId)
        .title(video.getSnippet().getTitle())
        .description(video.getSnippet().getDescription())
        .publishedAt(publishedAt)
        .duration(duration)
        .downloadStatus(EpisodeStatus.PENDING.name())
        .createdAt(LocalDateTime.now());

    applyThumbnails(builder, video.getSnippet().getThumbnails());
    return builder.build();
  }

  /**
   * 检查标题是否匹配关键词过滤条件
   *
   * @param title           视频标题
   * @param containKeywords 必须包含的关键词，多个关键词用空格分隔
   * @param excludeKeywords 必须排除的关键词，多个关键词用空格分隔
   * @return true 如果标题匹配过滤条件，false 否则
   */
  private boolean matchesKeywordFilter(String title, String containKeywords,
      String excludeKeywords) {
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

  // 移除基于 PlaylistItem 再次查询详情获取时长的方法，避免重复 API 调用

  // 保留最小外部 API 调用：不再提供基于 videoId 的时长查询入口

  /**
   * 获取视频详细信息
   *
   * @param youtubeService YouTube 服务对象
   * @param apiKey         API 密钥
   * @param videoId        视频 ID
   * @return 视频对象，如果未找到返回 null
   * @throws IOException IO 异常
   */
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

  /**
   * 检查视频是否为 live 节目，包含正在直播和即将开始的直播
   *
   * @param video 视频对象
   * @return 如果是 live 节目返回 true，否则返回 false
   */
  private boolean shouldSkipLiveContent(Video video) {
    String title = video.getSnippet().getTitle();
    String videoId = video.getId();
    String liveBroadcastContent = video.getSnippet().getLiveBroadcastContent();

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

  /**
   * 应用缩略图到 EpisodeBuilder
   *
   * @param builder    Episode 构建器
   * @param thumbnails 缩略图详情
   */
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

    builder.maxCoverUrl(maxCoverUrl);
  }
  /* ------------------------ Util Functions ----------------------- */


  /**
   * 视频抓取配置类
   *
   * @param channelId          频道 ID，用于抓取频道视频时使用
   * @param playlistId         播放列表 ID，用于直接抓取播放列表视频时使用
   * @param fetchNum           本次抓取的视频数量
   * @param containKeywords    标题必须包含的关键词
   * @param excludeKeywords    标题必须排除的关键词
   * @param minimalDuration    最小视频时长
   * @param pageSizeCalculator 页面大小计算器
   * @param maxPagesToCheck    最大检查页数
   */
  private record VideoFetchConfig(String channelId, String playlistId, int fetchNum,
                                  String containKeywords, String excludeKeywords,
                                  Integer minimalDuration, Function<Long, Long> pageSizeCalculator,
                                  int maxPagesToCheck) {

  }

  /**
   * 视频处理结果类
   */
  private record VideoProcessResult(boolean shouldContinue, boolean shouldBreak) {

  }
}
