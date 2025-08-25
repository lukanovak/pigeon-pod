package top.asimov.pigeon.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.Video;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.EpisodeDownloadStatus;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.EpisodeMapper;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Episode.EpisodeBuilder;
import top.asimov.pigeon.util.YoutubeHelper;

@Service
public class EpisodeService {

  protected final EpisodeMapper episodeMapper;
  private final AccountService accountService;
  private final DownloadService downloadService;

  public EpisodeService(EpisodeMapper episodeMapper, AccountService accountService,
      DownloadService downloadService) {
    this.episodeMapper = episodeMapper;
    this.accountService = accountService;
    this.downloadService = downloadService;
  }

  @Transactional
  public List<Episode> manualRefresh(String channelId) {
    List<Episode> episodes = fetchChannelVideos(channelId, 10L);
    saveEpisodes(episodes);
    return episodes;
  }


  public List<Episode> findByChannelId(String channelId) {
    LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Episode::getChannelId, channelId);
    return episodeMapper.selectList(queryWrapper);
  }

  List<Episode> saveEpisodes(List<Episode> episodes) {
    for (Episode episode : episodes) {
      episodeMapper.insert(episode);
    }
    return episodes;
  }

  void downloadEpisodeAudio(String videoId) {
    downloadService.downloadAudio(videoId);
  }

  public List<Episode> fetchChannelVideos(String channelId, Long fetchNum) {
    try {
      // 获取当前登录用户的YouTube API密钥
      String loginId = (String) StpUtil.getLoginId();
      String youtubeApiKey = accountService.getYoutubeApiKey(loginId);
      if (ObjectUtils.isEmpty(youtubeApiKey)) {
        throw new BusinessException("YouTube API key is not set, please set it in the user setting.");
      }

      YouTube youtubeService = YoutubeHelper.getService();
      List<Episode> episodes = new ArrayList<>();

      // 获取"Uploads"播放列表ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(youtubeApiKey);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0)
          .getContentDetails().getRelatedPlaylists().getUploads();

      // 分页获取播放列表中的视频
      List<PlaylistItem> allVideos = new ArrayList<>();
      String nextPageToken = "";

      // YouTube API对某些请求有隐性上限，1000是个合理值
      long videosRemaining = Math.min(fetchNum, 1000L);

      do {
        long pageSize = Math.min(50L, videosRemaining); // API允许的每页最大值为50
        YouTube.PlaylistItems.List playlistRequest = youtubeService.playlistItems()
            .list("snippet,contentDetails")
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(pageSize)
            .setPageToken(nextPageToken)
            .setKey(youtubeApiKey);

        PlaylistItemListResponse playlistResponse = playlistRequest.execute();
        List<PlaylistItem> items = playlistResponse.getItems();
        if (items != null) {
          allVideos.addAll(items);
          videosRemaining -= items.size();
        }

        nextPageToken = playlistResponse.getNextPageToken();
      } while (nextPageToken != null && videosRemaining > 0);

      // 将API返回的视频信息转换为 episode 对象
      for (PlaylistItem item : allVideos) {
        PlaylistItemSnippet snippet = item.getSnippet();
        // 有时视频可能因各种原因（如被删除但仍在播放列表中）没有resourceId
        if (snippet.getResourceId() == null || snippet.getResourceId().getVideoId() == null) {
          continue; // 跳过无效的视频项
        }
        String videoId = snippet.getResourceId().getVideoId();

        YouTube.Videos.List videoRequest = youtubeService.videos()
            .list("contentDetails") // 我们只需要 contentDetails 部分来获取时长
            .setId(videoId)
            .setKey(youtubeApiKey);

        Video videoDetail = videoRequest.execute().getItems().get(0);
        String duration = videoDetail.getContentDetails().getDuration();

        EpisodeBuilder episodeBuilder = Episode.builder()
            .id(videoId)
            .channelId(channelId)
            .position(snippet.getPosition().intValue())
            .title(snippet.getTitle())
            .description(snippet.getDescription())
            .duration(duration)
            .publishedAt(YoutubeHelper.convertToLocalDateTime(snippet.getPublishedAt()))
            .downloadStatus(EpisodeDownloadStatus.PENDING.name());

        if (snippet.getThumbnails() != null) {
          if (snippet.getThumbnails().getDefault() != null) {
            episodeBuilder.defaultCoverUrl(snippet.getThumbnails().getDefault().getUrl());
          }
          if (snippet.getThumbnails().getMaxres() != null) {
            episodeBuilder.maxCoverUrl(snippet.getThumbnails().getMaxres().getUrl());
          }
        }

        episodes.add(episodeBuilder.build());
      }

      return episodes;
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取YouTube频道视频时发生错误: " + e.getMessage());
    }
  }

}
