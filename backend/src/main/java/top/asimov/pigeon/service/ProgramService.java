package top.asimov.pigeon.service;

import static top.asimov.pigeon.util.YoutubeHelper.API_KEY;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ProgramMapper;
import top.asimov.pigeon.model.Program;
import top.asimov.pigeon.util.YoutubeHelper;

@Service
public class ProgramService {

  protected final ProgramMapper programMapper;

  public ProgramService(ProgramMapper programMapper) {
    this.programMapper = programMapper;
  }

  @Transactional
  public void fetchAndSavePrograms(String channelId, Long maxVideos) {
    List<Program> programs = fetchChannelVideos(channelId, maxVideos);
    savePrograms(programs);
  }

  public void updatePrograms(String channelId, Long maxVideos) {
    List<Program> programs = fetchChannelVideos(channelId, 3L);
    for (Program program : programs) {
      programMapper.updateById(program);
    }
  }


  public List<Program> findByChannelId(String channelId) {
    LambdaQueryWrapper<Program> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Program::getChannelId, channelId);
    return programMapper.selectList(queryWrapper);
  }

  void savePrograms(List<Program> programs) {
    for (Program program : programs) {
      programMapper.insert(program);
    }
  }

  List<Program> fetchChannelVideos(String channelId, Long maxVideos) {
    try {
      YouTube youtubeService = YoutubeHelper.getService();
      List<Program> programs = new ArrayList<>();

      // 获取"Uploads"播放列表ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(API_KEY);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0)
          .getContentDetails().getRelatedPlaylists().getUploads();

      System.out.println("找到'Uploads'播放列表ID: " + uploadsPlaylistId);

      // 获取指定数量的视频
      List<PlaylistItem> allVideos = new ArrayList<>();
      String nextPageToken = "";
      long videosToFetch = Math.min(maxVideos, 1000L); // 设置一个合理的上限
      long videosRemaining = videosToFetch;

      do {
        long pageSize = Math.min(50L, videosRemaining); // 每页最多50个
        YouTube.PlaylistItems.List playlistRequest = youtubeService.playlistItems()
            .list("snippet,contentDetails")
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(pageSize)
            .setPageToken(nextPageToken)
            .setKey(API_KEY);

        PlaylistItemListResponse playlistResponse = playlistRequest.execute();
        List<PlaylistItem> items = playlistResponse.getItems();
        allVideos.addAll(items);

        videosRemaining -= items.size();
        nextPageToken = playlistResponse.getNextPageToken();
      } while (nextPageToken != null && videosRemaining > 0);

      System.out.println("获取到频道视频数: " + allVideos.size());

      // 构建Program对象列表
      for (PlaylistItem item : allVideos) {
        PlaylistItemSnippet snippet = item.getSnippet();
        String videoId = snippet.getResourceId().getVideoId();

        // 使用Builder模式构建Program对象
        Program.ProgramBuilder programBuilder = Program.builder()
            .id(videoId)
            .channelId(channelId)
            .position(snippet.getPosition().intValue())
            .title(snippet.getTitle())
            .description(snippet.getDescription())
            .publishedAt(YoutubeHelper.convertToLocalDateTime(snippet.getPublishedAt()));

        // 只获取default和maxres两个分辨率的封面
        if (snippet.getThumbnails().getDefault() != null) {
          programBuilder.defaultCoverUrl(snippet.getThumbnails().getDefault().getUrl());
        }

        if (snippet.getThumbnails().getMaxres() != null) {
          programBuilder.maxCoverUrl(snippet.getThumbnails().getMaxres().getUrl());
        }

        programs.add(programBuilder.build());
      }

      return programs;
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道视频时发生错误: " + e.getMessage());
    }
  }

}
