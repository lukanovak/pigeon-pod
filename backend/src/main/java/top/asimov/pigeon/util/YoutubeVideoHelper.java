package top.asimov.pigeon.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
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
import java.util.List;
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

  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum) {
    return fetchYoutubeChannelVideos(channelId, fetchNum, null, null, null, null);
  }

  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum,
      String containKeywords, String excludeKeywords, Integer minimalDuration) {
    return fetchYoutubeChannelVideos(channelId, fetchNum, null, containKeywords, excludeKeywords,
        minimalDuration);
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
  public List<Episode> fetchYoutubeChannelVideos(String channelId, int fetchNum,
      String lastSyncedVideoId,
      String containKeywords, String excludeKeywords, Integer minimalDuration) {
    try {
      String youtubeApiKey = accountService.getYoutubeApiKey();

      // 1. 获取 Uploads 播放列表 ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0).getContentDetails()
          .getRelatedPlaylists().getUploads();

      // 2. 循环分页抓取：边抓取边过滤，直到满足 fetchNum 或无更多数据
      List<Episode> resultEpisodes = new ArrayList<>();
      String nextPageToken = "";
      boolean foundLastSyncedVideo = false;

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

        // 2. 使用过滤器过滤目标
        for (PlaylistItem item : pageItems) {
          String title = item.getSnippet().getTitle();

          // 使用提取的关键词过滤方法
          if (!matchesKeywordFilter(title, containKeywords, excludeKeywords)) {
            continue;
          }

          // 获取视频信息并检测是否为 live
          String duration = fetchVideoDuration(youtubeService, youtubeApiKey, item);
          if (duration == null) {
            // 这是 live 节目，跳过
            continue;
          }

          // 使用提取的时长过滤方法
          if (!matchesDurationFilter(duration, minimalDuration)) {
            continue;
          }

          String currentVideoId = item.getSnippet().getResourceId().getVideoId();
          if (currentVideoId.equals(lastSyncedVideoId)) {
            foundLastSyncedVideo = true;
            break; // 到达上次同步视频，后面的都是旧的
          }

          // 构建 Episode
          EpisodeBuilder builder = Episode.builder()
              .id(item.getSnippet().getResourceId().getVideoId())
              .channelId(channelId)
              .title(item.getSnippet().getTitle())
              .description(item.getSnippet().getDescription())
              .publishedAt(LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                  ZoneId.systemDefault()))
              .duration(duration)
              .downloadStatus(EpisodeStatus.PENDING.name())
              .createdAt(LocalDateTime.now())
              .position(item.getSnippet().getPosition() != null ? item.getSnippet().getPosition()
                  .intValue() : 0);

          // 设置缩略图
          applyThumbnails(builder, item.getSnippet().getThumbnails());

          Episode episode = builder.build();
          // 添加到结果列表
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
   * 使用播放列表 API 抓取指定 YouTube 频道在指定日期之前的视频列表 这个方法比 Search API 更可靠，能保证严格按时间顺序获取视频
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
    try {
      String youtubeApiKey = accountService.getYoutubeApiKey();

      // 1. 获取 Uploads 播放列表 ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0).getContentDetails()
          .getRelatedPlaylists().getUploads();

      // 2. 循环分页抓取：按时间顺序获取，直到找到足够的符合条件的视频
      List<Episode> resultEpisodes = new ArrayList<>();
      String nextPageToken = "";
      int maxPagesToCheck = 20; // 限制最大检查页数，避免无限循环
      int currentPage = 0;

      log.info("开始获取频道 {} 在 {} 之前的视频，目标数量: {}", channelId, publishedBefore,
          fetchNum);

      while (resultEpisodes.size() < fetchNum && currentPage < maxPagesToCheck) {
        long pageSize = 50L; // API 单页最大 50，获取更多数据以便过滤

        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
            .list("snippet")
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(pageSize)
            .setPageToken(nextPageToken)
            .setKey(youtubeApiKey);

        PlaylistItemListResponse response = request.execute();
        List<PlaylistItem> pageItems = response.getItems();
        if (pageItems == null || pageItems.isEmpty()) {
          log.info("没有更多视频数据，停止抓取");
          break; // 没有更多数据
        }

        currentPage++;
        log.info("处理第 {} 页，获取到 {} 个视频项", currentPage, pageItems.size());

        boolean foundVideosBeforeCutoff = false;

        // 处理当前页的视频
        for (PlaylistItem item : pageItems) {
          // 检查发布时间是否在截止日期之前
          LocalDateTime videoPublishedAt = LocalDateTime.ofInstant(
              Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
              ZoneId.systemDefault());

          if (videoPublishedAt.isAfter(publishedBefore)) {
            // 视频太新，跳过
            continue;
          }

          foundVideosBeforeCutoff = true;
          String title = item.getSnippet().getTitle();

          // 应用关键词过滤
          if (!matchesKeywordFilter(title, containKeywords, excludeKeywords)) {
            continue;
          }

          // 获取视频信息并检测是否为 live
          String duration = fetchVideoDuration(youtubeService, youtubeApiKey, item);
          if (duration == null) {
            // 这是 live 节目，跳过
            continue;
          }

          // 应用时长过滤
          if (!matchesDurationFilter(duration, minimalDuration)) {
            continue;
          }

          // 构建 Episode
          EpisodeBuilder builder = Episode.builder()
              .id(item.getSnippet().getResourceId().getVideoId())
              .channelId(channelId)
              .title(item.getSnippet().getTitle())
              .description(item.getSnippet().getDescription())
              .publishedAt(videoPublishedAt)
              .duration(duration)
              .downloadStatus(EpisodeStatus.PENDING.name())
              .createdAt(LocalDateTime.now())
              .position(item.getSnippet().getPosition() != null ? item.getSnippet().getPosition()
                  .intValue() : 0);

          // 设置缩略图
          applyThumbnails(builder, item.getSnippet().getThumbnails());

          Episode episode = builder.build();
          resultEpisodes.add(episode);

          log.info("添加符合条件的视频: {} (发布于: {})", title, videoPublishedAt);

          if (resultEpisodes.size() >= fetchNum) {
            break;
          }
        }

        // 如果这一页没有找到任何在截止日期之前的视频，说明已经到了更早的视频，可能需要继续查找
        // 但如果已经找到了一些符合条件的视频，可以考虑是否继续
        if (foundVideosBeforeCutoff && resultEpisodes.size() > 0) {
          log.info("当前页找到了 {} 个符合条件的视频，累计 {} 个",
              pageItems.stream().mapToInt(item -> {
                LocalDateTime publishTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                    ZoneId.systemDefault());
                return publishTime.isBefore(publishedBefore) || publishTime.isEqual(publishedBefore)
                    ? 1 : 0;
              }).sum(), resultEpisodes.size());
        }

        nextPageToken = response.getNextPageToken();
        if (nextPageToken == null) {
          log.info("已到达播放列表末尾");
          break; // 没有下一页
        }
      }

      if (currentPage >= maxPagesToCheck) {
        log.warn("已检查 {} 页视频，停止继续搜索", maxPagesToCheck);
      }

      log.info("最终获取到 {} 个符合条件的视频", resultEpisodes.size());
      return processResultList(resultEpisodes, fetchNum);

    } catch (Exception e) {
      log.error("获取频道视频时出错", e);
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

  /**
   * 获取播放列表项的视频时长
   */
  private String fetchVideoDuration(YouTube youtubeService, String apiKey, PlaylistItem item)
      throws IOException {
    PlaylistItemSnippet snippet = item.getSnippet();
    if (snippet == null || snippet.getResourceId() == null) {
      log.warn("播放列表项缺少必要的元数据，跳过: {}", item.getId());
      return null;
    }

    String videoId = snippet.getResourceId().getVideoId();
    return fetchVideoDuration(youtubeService, apiKey, videoId);
  }

  /**
   * 获取视频时长
   */
  private String fetchVideoDuration(YouTube youtubeService, String apiKey, String videoId)
      throws IOException {
    Video video = fetchVideoDetails(youtubeService, apiKey, videoId);
    if (video == null) {
      return null;
    }

    String title = video.getSnippet().getTitle();
    if (shouldSkipLiveContent(video)) {
      return null;
    }

    if (video.getContentDetails() == null ||
        !StringUtils.hasText(video.getContentDetails().getDuration())) {
      log.warn("无法读取视频时长: {} - {}", videoId, title);
      return null;
    }

    return video.getContentDetails().getDuration();
  }

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

}
