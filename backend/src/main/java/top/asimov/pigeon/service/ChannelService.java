package top.asimov.pigeon.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.constant.ChannelSource;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.User;
import top.asimov.pigeon.util.YoutubeHelper;

@Log4j2
@Service
public class ChannelService {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final ChannelMapper channelMapper;
  private final AccountService accountService;
  private final EpisodeService episodeService;
  private final UserMapper userMapper;
  private final ApplicationEventPublisher eventPublisher;

  public ChannelService(ChannelMapper channelMapper, AccountService accountService,
      EpisodeService episodeService, UserMapper userMapper, ApplicationEventPublisher eventPublisher) {
    this.channelMapper = channelMapper;
    this.accountService = accountService;
    this.episodeService = episodeService;
    this.userMapper = userMapper;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Channel fetchAndSaveChannel(Channel channel) {
    String channelSource = channel.getChannelSource();
    if (ObjectUtils.isEmpty(channelSource)) {
      throw new BusinessException("channelSource cannot be empty!");
    }
    if (channelSource.equals(ChannelSource.YOUTUBE.name())) {
      Channel fetchedChannel = fetchAndSaveChannel(channel.getChannelUrl());
      saveChannel(fetchedChannel);
      initialEpisodes(fetchedChannel);
      return fetchedChannel;
    }
    throw new BusinessException("Unsupported channel source: " + channelSource);
  }

  public List<Channel> selectChannelList() {
    return channelMapper.selectChannelWithLastUploadedAt();
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

  public List<Channel> findDueForSync(LocalDateTime checkTime) {
    List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<>());
    return channels.stream()
        .filter(c -> c.getLastSyncTimestamp() == null ||
            c.getLastSyncTimestamp().plusHours(c.getUpdateFrequency()).isBefore(checkTime))
        .collect(Collectors.toList());
  }

  public Channel findByHandler(String handler) {
    LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Channel::getHandler, handler);
    return channelMapper.selectOne(queryWrapper);
  }

  /**
   * 删除频道及其所有关联资源
   *
   * @param channelId 要删除的频道ID
   */
  @Transactional
  public void deleteChannel(String channelId) {
    log.info("开始删除频道: {}", channelId);

    // 1. 获取频道信息，确认存在
    Channel channel = channelMapper.selectById(channelId);
    if (channel == null) {
      throw new BusinessException("频道不存在，ID: " + channelId);
    }

    // 2. 查询该频道下所有的episodes
    List<Episode> episodes = episodeService.findByChannelId(channelId);
    log.info("频道 {} 下有 {} 个episodes需要删除", channel.getName(), episodes.size());

    // 3. 删除所有episodes对应的音频文件
    deleteAudioFiles(episodes);

    // 4. 从数据库中删除所有episodes记录
    deleteEpisodeRecords(channelId);

    // 5. 删除频道记录
    int result = channelMapper.deleteById(channelId);
    if (result > 0) {
      log.info("频道 {} 删除成功", channel.getName());
    } else {
      log.error("频道 {} 删除失败", channel.getName());
      throw new BusinessException("删除频道失败");
    }
  }

  @Transactional
  public void syncChannel(Channel channel) {
    log.info("正在同步频道: {}", channel.getName());

    // 1. 调用新方法获取增量视频
    List<Episode> newEpisodes = episodeService.fetchChannelVideos(channel, 5L);

    if (newEpisodes.isEmpty()) {
      log.info("频道 {} 没有新内容。", channel.getName());
      // 即使没有新内容，也更新同步时间戳，避免频繁检查
      channel.setLastSyncTimestamp(LocalDateTime.now());
      channelMapper.updateById(channel);
      return;
    }

    log.info("频道 {} 发现 {} 个新节目。", channel.getName(), newEpisodes.size());

    // 2. 保存新节目的元数据
    episodeService.saveEpisodes(newEpisodes);

    // 3. 更新频道的检查点 (lastSyncedVideoId 和 lastSyncTimestamp)
    // newEpisodes 列表中的最后一个元素就是最新的视频（因为我们反转了）
    Episode latestEpisode = newEpisodes.get(0);
    channel.setLastSyncVideoId(latestEpisode.getId());
    channel.setLastSyncTimestamp(LocalDateTime.now());
    channelMapper.updateById(channel);

    // 4. **复用**我们已有的事件发布机制，触发异步下载
    List<String> newEpisodeIds = newEpisodes.stream().map(Episode::getId)
        .collect(Collectors.toList());
    EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, newEpisodeIds);
    eventPublisher.publishEvent(event);

    log.info("为频道 {} 的新节目发布了下载事件。", channel.getName());
  }

  public String getChannelRssFeedUrl(String channelHandler) {
    String loginId = (String) StpUtil.getLoginId();
    User user = userMapper.selectById(loginId);
    String apiKey = user.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException("API Key is not set, please generate it in the user setting.");
    }
    return appBaseUrl + "/api/rss/" + channelHandler + ".xml?apikey=" + apiKey;
  }

  private Channel fetchAndSaveChannel(String channelUrl) {
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
          .updateFrequency(8) // 默认每12小时更新一次
          .channelUrl(channelUrl)
          .subscribedAt(LocalDateTime.now())
          .channelSource(ChannelSource.YOUTUBE.name())
          .build();
    } catch (GeneralSecurityException | IOException e) {
      throw new BusinessException("获取频道信息失败" + e.getMessage());
    }
  }

  private void initialEpisodes(Channel channel) {
    // 获取最近几个视频
    List<Episode> episodes = episodeService.fetchChannelVideos(channel, 3L);

    // 保存视频
    List<Episode> savedEpisodes = episodeService.saveEpisodes(episodes);

    // 更新频道的 lastSyncVideoId 和 lastSyncTimestamp
    for (Episode episode : savedEpisodes) {
      if (episode.getPosition() == 0) {
        channel.setLastSyncVideoId(episode.getId());
        channel.setLastSyncTimestamp(LocalDateTime.now());
        channelMapper.updateById(channel);
        break;
      }
    }

    // 发布事件通知有新视频下载
    List<String> savedEpisodeIds = savedEpisodes.stream()
        .map(Episode::getId)
        .collect(Collectors.toList());
    EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, savedEpisodeIds);
    eventPublisher.publishEvent(event);
    log.info("发布 EpisodesCreatedEvent 事件，包含 {} 个 episode ID。", savedEpisodeIds.size());
  }

  private void saveChannel(Channel channel) {
    String channelId = channel.getId();
    Channel existChannel = channelMapper.selectById(channelId);
    if (existChannel != null) {
      throw new BusinessException("Channel already exists with name: " + channel.getName());
    }
    channelMapper.insert(channel);
  }

  /**
   * 删除episodes对应的音频文件
   */
  private void deleteAudioFiles(List<Episode> episodes) {
    for (Episode episode : episodes) {
      String audioFilePath = episode.getAudioFilePath();
      if (!ObjectUtils.isEmpty(audioFilePath)) {
        try {
          java.io.File audioFile = new java.io.File(audioFilePath);
          if (audioFile.exists()) {
            boolean deleted = audioFile.delete();
            if (deleted) {
              log.info("音频文件删除成功: {}", audioFilePath);
            } else {
              log.warn("音频文件删除失败: {}", audioFilePath);
            }
          } else {
            log.warn("音频文件不存在: {}", audioFilePath);
          }
        } catch (Exception e) {
          log.error("删除音频文件时出错: {}", audioFilePath, e);
        }
      }
    }
  }

  /**
   * 从数据库中删除指定频道的所有episode记录
   */
  private void deleteEpisodeRecords(String channelId) {
    try {
      LambdaQueryWrapper<Episode> queryWrapper = new LambdaQueryWrapper<>();
      queryWrapper.eq(Episode::getChannelId, channelId);
      int count = episodeService.removeEpisodes(queryWrapper);
      log.info("删除了 {} 条episode记录", count);
    } catch (Exception e) {
      log.error("删除episode记录时出错", e);
      throw new BusinessException("删除episode记录失败: " + e.getMessage());
    }
  }
}
