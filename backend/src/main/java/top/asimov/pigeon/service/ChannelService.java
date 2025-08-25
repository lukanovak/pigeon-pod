package top.asimov.pigeon.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.ChannelSource;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.util.YoutubeHelper;

@Service
public class ChannelService {

  private final ChannelMapper channelMapper;
  private final AccountService accountService;
  private final EpisodeService episodeService;

  public ChannelService(ChannelMapper channelMapper, AccountService accountService,
      EpisodeService episodeService) {
    this.channelMapper = channelMapper;
    this.accountService = accountService;
    this.episodeService = episodeService;
  }

  @Transactional
  public Channel fetchAndSaveChannel(Channel channel) {
    String channelSource = channel.getChannelSource();
    if (ObjectUtils.isEmpty(channelSource)) {
      throw new BusinessException("channelSource cannot be empty!");
    }
    if (channelSource.equals(ChannelSource.YOUTUBE.name())) {
      Channel fetchedChannel = fetchAndSaveChannel(channel.getChannelUrl());
      fetchInitialEpisodes(fetchedChannel);
      saveChannel(fetchedChannel);
      return fetchedChannel;
    }
    throw new BusinessException("Unsupported channel source: " + channelSource);
  }

  public List<Channel> listAllChannels(Channel channel) {
    LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
    String name = channel.getName();
    if (!ObjectUtils.isEmpty(name)) {
      queryWrapper.like(Channel::getName, name);
    }
    String description = channel.getDescription();
    if (!ObjectUtils.isEmpty(description)) {
      queryWrapper.like(Channel::getDescription, description);
    }
    return channelMapper.selectList(queryWrapper);
  }

  public Channel channelDetail(String id) {
    Channel channel = channelMapper.selectById(id);
    if (channel == null) {
      throw new BusinessException("Channel not found with id: " + id);
    }
    String handler = channel.getHandler();
    String channelSource = channel.getChannelSource();
    if (ChannelSource.YOUTUBE.name().equals(channelSource)) {
      channel.setChannelUrl("https://www.youtube.com/@" + handler);
    }
    return channel;
  }


  Channel fetchAndSaveChannel(String channelUrl) {
    try {
      String loginId = (String) StpUtil.getLoginId();
      String youtubeApiKey = accountService.getYoutubeApiKey(loginId);
      if (ObjectUtils.isEmpty(youtubeApiKey)) {
        throw new BusinessException(
            "YouTube API key is not set, please set it in the user setting.");
      }

      // 从URL提取handle
      String handle = YoutubeHelper.getHandleFromUrl(channelUrl);
      if (handle == null) {
        throw new BusinessException("无效的YouTube频道URL");
      }

      // 检查数据库中是否已存在此频道
      Channel existingChannel = this.findByHandler(handle);
      if (existingChannel != null) {
        throw new BusinessException("频道已存在");
      }

      YouTube youtubeService = YoutubeHelper.getService();

      // 使用handle搜索以获取Channel ID
      String channelId = YoutubeHelper.getChannelIdByHandle(youtubeService, youtubeApiKey, handle);
      if (channelId == null) {
        throw new BusinessException("无法通过handle找到频道");
      }

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

      com.google.api.services.youtube.model.Channel ytChannel = channels.get(0);
      return Channel.builder()
          .id(ytChannel.getId())
          .handler(handle)
          .name(ytChannel.getSnippet().getTitle())
          .avatarUrl(ytChannel.getSnippet().getThumbnails().getHigh().getUrl())
          .description(ytChannel.getSnippet().getDescription())
          .registeredAt(YoutubeHelper.convertToLocalDateTime(ytChannel.getSnippet().getPublishedAt()))
          .videoCount(ytChannel.getStatistics().getVideoCount().intValue())
          .subscriberCount(ytChannel.getStatistics().getSubscriberCount().intValue())
          .viewCount(ytChannel.getStatistics().getViewCount().intValue())
          .channelUrl(channelUrl)
          .subscribedAt(LocalDateTime.now())
          .channelSource(ChannelSource.YOUTUBE.name())
          .build();
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道信息失败" + e.getMessage());
    }
  }

  void fetchInitialEpisodes(Channel channel) {
    List<Episode> episodes = episodeService.fetchChannelVideos(channel.getId(), 5L);
    List<Episode> episodeList = episodeService.saveEpisodes(episodes);
    episodeList.stream().filter(episode -> episode.getPosition() == 0)
        .forEach(episode -> {
      channel.setLastSyncVideoId(episode.getId());
      channel.setLastSyncTimestamp(LocalDateTime.now());
    });
    episodeList.forEach(episode -> episodeService.downloadEpisodeAudio(episode.getId()));
  }

  void saveChannel(Channel channel) {
    String channelId = channel.getId();
    Channel existChannel = channelMapper.selectById(channelId);
    if (existChannel != null) {
      throw new BusinessException("Channel already exists with name: " + channel.getName());
    }
    channelMapper.insert(channel);
  }

  Channel findByHandler(String handler) {
    LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Channel::getHandler, handler);
    return channelMapper.selectOne(queryWrapper);
  }
}
