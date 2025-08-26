package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Episode.EpisodeBuilder;
import top.asimov.pigeon.util.YoutubeHelper;

@Log4j2
@Service
public class EpisodeService {

  protected final EpisodeMapper episodeMapper;
  private final AccountService accountService;

  public EpisodeService(EpisodeMapper episodeMapper, AccountService accountService) {
    this.episodeMapper = episodeMapper;
    this.accountService = accountService;
  }

  public List<Episode> findByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectList(queryWrapper);
  }

  // 获取节目列表，按按position排序倒序
  public List<Episode> findAllEpisode(String channelId, String status) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper
        .eq(Episode::getChannelId, channelId)
        .eq(Episode::getDownloadStatus, status)
        .orderByAsc(Episode::getPosition);
    return episodeMapper.selectList(queryWrapper);
  }

  List<Episode> saveEpisodes(List<Episode> episodes) {
    episodes.forEach(episodeMapper::insert);
    return episodes;
  }

  /**
   * 获取指定频道自上次同步以来的新视频。
   *
   * @param channel 要同步的频道
   * @return 只包含新视频的 Episode 列表
   */
  public List<Episode> fetchChannelVideos(Channel channel, Long fetchNum) {
    String channelId = channel.getId();
    String lastSyncedVideoId = channel.getLastSyncVideoId(); // 从频道对象中获取检查点

    try {
      YouTube youtubeService = YoutubeHelper.getService();
      String youtubeApiKey = getYoutubeApiKey();

      // 1. 获取 Uploads 播放列表 ID
      String uploadsPlaylistId = getUploadsPlaylistId(channelId);

      // 2. 循环获取视频列表，直到找到上次同步过的视频或者达到 fetchNum 限制
      List<PlaylistItem> PlaylistItems = new ArrayList<>();
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

        videosRemaining -= response.getItems().size();

        for (PlaylistItem item : response.getItems()) {
          String currentVideoId = item.getSnippet().getResourceId().getVideoId();

          // 如果找到了上次同步的ID，说明之后的全是旧视频，停止查找
          if (currentVideoId.equals(lastSyncedVideoId)) {
            foundLastSyncedVideo = true;
            break;
          }
          PlaylistItems.add(item);
        }

        if (foundLastSyncedVideo) {
          break;
        }

        nextPageToken = response.getNextPageToken();
      } while (nextPageToken != null & videosRemaining > 0);

      if (PlaylistItems.isEmpty()) {
        return Collections.emptyList(); // 没有新视频
      }

      // 3. **性能优化：批量获取所有新视频的详情（时长等）**
      Map<String, String> videoDurations = fetchVideoDurationsInBatch(youtubeService, PlaylistItems,
          youtubeApiKey);

      // 4. 将 PlaylistItem 转换为 Episode 对象
      List<Episode> newEpisodes = new ArrayList<>();
      for (PlaylistItem item : PlaylistItems) {
        EpisodeBuilder episodeBuilder = Episode.builder()
            .id(item.getSnippet().getResourceId().getVideoId())
            .channelId(channelId)
            .position(item.getSnippet().getPosition().intValue())
            .title(item.getSnippet().getTitle())
            .description(item.getSnippet().getDescription())
            .publishedAt(YoutubeHelper.convertToLocalDateTime(item.getSnippet().getPublishedAt()))
            .duration(
                videoDurations.get(item.getSnippet().getResourceId().getVideoId())) // 从Map中获取时长
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

      // YouTube API 返回的是从新到旧，我们希望插入数据库时也是这个顺序，
      // 或者如果你希望按发布顺序处理，可以在这里反转列表
      //Collections.reverse(newEpisodes); // 可选，反转为从旧到新

      return newEpisodes;

    } catch (Exception e) {
      throw new BusinessException("获取频道新视频时发生错误: " + e.getMessage());
    }
  }

  // 获取当前YouTube API密钥
  private String getYoutubeApiKey() {
    // 单用户工具，直接使用ID为0的用户的API Key
    String youtubeApiKey = accountService.getYoutubeApiKey("0");
    if (ObjectUtils.isEmpty(youtubeApiKey)) {
      throw new BusinessException(
          "YouTube API key is not set, please set it in the user setting.");
    }
    return youtubeApiKey;
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

  private String getUploadsPlaylistId(String channelId) {
    try {
      String youtubeApiKey = getYoutubeApiKey();
      YouTube youtubeService = YoutubeHelper.getService();

      // 获取"Uploads"播放列表ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      return channelResponse.getItems().get(0).getContentDetails().getRelatedPlaylists()
          .getUploads();
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取YouTube频道视频时发生错误: " + e.getMessage());
    }
  }

  /**
   * 删除符合条件的所有episodes
   *
   * @param wrapper 查询条件包装器
   * @return 删除的记录数
   */
  public int removeEpisodes(LambdaQueryWrapper<Episode> wrapper) {
    return episodeMapper.delete(wrapper);
  }
}
