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
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ProgramMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Program;
import top.asimov.pigeon.util.YoutubeHelper;

@Service
public class ProgramService {

  protected final ProgramMapper programMapper;

  public ProgramService(ProgramMapper programMapper) {
    this.programMapper = programMapper;
  }

  public void fetchAllChannelVideos(Channel channel) {
    try {
      YouTube youtubeService = YoutubeHelper.getService();
      String channelId = channel.getId();

      // 1. 获取"Uploads"播放列表ID
      YouTube.Channels.List channelRequest = youtubeService.channels().list("contentDetails");
      channelRequest.setId(channelId).setKey(API_KEY);
      ChannelListResponse channelResponse = channelRequest.execute();

      String uploadsPlaylistId = channelResponse.getItems().get(0)
          .getContentDetails().getRelatedPlaylists().getUploads();

      System.out.println("找到'Uploads'播放列表ID: " + uploadsPlaylistId);

      // 2. 清除此频道之前的视频记录（如果有）
      int deletedCount = this.deleteByChannelId(channelId);
      if (deletedCount > 0) {
        System.out.println("已删除频道旧视频记录: " + deletedCount + "条");
      }

      // 3. 遍历播放列表，获取所有视频
      List<PlaylistItem> allVideos = new ArrayList<>();
      String nextPageToken = "";

      do {
        YouTube.PlaylistItems.List playlistRequest = youtubeService.playlistItems()
            .list("snippet,contentDetails")
            .setPlaylistId(uploadsPlaylistId)
            .setMaxResults(50L) // 每次最多获取50个
            .setPageToken(nextPageToken)
            .setKey(API_KEY);

        PlaylistItemListResponse playlistResponse = playlistRequest.execute();
        allVideos.addAll(playlistResponse.getItems());

        nextPageToken = playlistResponse.getNextPageToken();
      } while (nextPageToken != null);

      System.out.println("获取到频道视频总数: " + allVideos.size());

      for (PlaylistItem item : allVideos) {
        PlaylistItemSnippet snippet = item.getSnippet();
        String videoId = snippet.getResourceId().getVideoId();

        Program program = new Program();
        program.setId(videoId);
        program.setChannelId(channelId);
        program.setPosition(snippet.getPosition().intValue());
        program.setTitle(snippet.getTitle());
        program.setDescription(snippet.getDescription());
        program.setPublishedAt(YoutubeHelper.convertToLocalDateTime(snippet.getPublishedAt()));

        // 获取最高质量的封面
        if (snippet.getThumbnails().getMaxres() != null) {
          program.setCoverUrl(snippet.getThumbnails().getMaxres().getUrl());
        } else if (snippet.getThumbnails().getHigh() != null) {
          program.setCoverUrl(snippet.getThumbnails().getHigh().getUrl());
        } else if (snippet.getThumbnails().getMedium() != null) {
          program.setCoverUrl(snippet.getThumbnails().getMedium().getUrl());
        } else {
          program.setCoverUrl(snippet.getThumbnails().getDefault().getUrl());
        }

        programMapper.insert(program);
      }

    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道视频时发生错误: " + e.getMessage());
    }
  }

  List<Program> findByChannelId(String channelId) {
    LambdaQueryWrapper<Program> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Program::getChannelId, channelId);
    return programMapper.selectList(queryWrapper);
  }

  int deleteByChannelId(String channelId) {
    LambdaQueryWrapper<Program> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Program::getChannelId, channelId);
    return programMapper.delete(queryWrapper);
  }
}
