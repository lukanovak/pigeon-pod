package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.ChannelSource;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.ChannelPack;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.util.YoutubeHelper;

@Log4j2
@Service
public class ChannelService {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final ChannelMapper channelMapper;
  private final EpisodeService episodeService;
  private final ApplicationEventPublisher eventPublisher;
  private final YoutubeHelper youtubeHelper;
  private final AccountService accountService;

  public ChannelService(ChannelMapper channelMapper, EpisodeService episodeService,
      ApplicationEventPublisher eventPublisher, YoutubeHelper youtubeHelper, AccountService accountService) {
    this.channelMapper = channelMapper;
    this.episodeService = episodeService;
    this.eventPublisher = eventPublisher;
    this.youtubeHelper = youtubeHelper;
    this.accountService = accountService;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 appBaseUrl 值
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  public ChannelPack fetchChannel(Channel channel) {
    String channelSource = channel.getChannelSource();
    if (ObjectUtils.isEmpty(channelSource)) {
      throw new BusinessException("channelSource cannot be empty!");
    }

    String channelId = channel.getId();
    if (StringUtils.hasText(channelId)) {
      Channel existChannel = channelMapper.selectById(channelId);
      if (existChannel != null) {
        throw new BusinessException("Channel already exists with name: " + channel.getName());
      }
    }

    String channelUrl = channel.getChannelUrl();
    if (ObjectUtils.isEmpty(channelUrl)) {
      throw new BusinessException("channelUrl cannot be empty!");
    }

    String handler = youtubeHelper.getHandleFromUrl(channelUrl);

    com.google.api.services.youtube.model.Channel ytChannel = youtubeHelper.fetchYoutubeChannelByUrl(channelUrl);
    String ytChannelId = ytChannel.getId();

    Channel fetchedChannel = Channel.builder()
        .id(ytChannelId)
        .handler(handler)
        .name(ytChannel.getSnippet().getTitle())
        .avatarUrl(ytChannel.getSnippet().getThumbnails().getHigh().getUrl())
        .description(ytChannel.getSnippet().getDescription())
        .channelUrl(channelUrl)
        .subscribedAt(LocalDateTime.now())
        .channelSource(channelSource)
        .build();

    // 获取最近3个视频确认是目标频道
    List<Episode> episodes = youtubeHelper.fetchYoutubeChannelVideos(
        ytChannelId, null, 3, null, null, null );
    return ChannelPack.builder().channel(fetchedChannel).episodes(episodes).build();
  }

  public List<Episode> previewChannel(Channel channel) {
    String channelId = channel.getId();
    return youtubeHelper.fetchYoutubeChannelVideos(channelId, null, 3,
        channel.getContainKeywords(), channel.getExcludeKeywords(), channel.getMinimumDuration());
  }

  @Transactional
  public Channel saveChannel(Channel channel) {
    Integer initialEpisodes = channel.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      initialEpisodes = 3;
    }

    String channelId = channel.getId();

    List<Episode> episodes = youtubeHelper.fetchYoutubeChannelVideos(
        channelId, null,
        initialEpisodes, channel.getContainKeywords(),
        channel.getExcludeKeywords(), channel.getMinimumDuration());

    Episode latestEpisode = episodes.get(0);
    for (Episode episode : episodes) {
      // 找到publishDate最新的那个视频
      if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
        latestEpisode = episode;
      }
    }

    // 更新频道的 lastSyncVideoId 和 lastSyncTimestamp
    channel.setLastSyncVideoId(latestEpisode.getId());
    channel.setLastSyncTimestamp(LocalDateTime.now());
    channelMapper.insert(channel);
    episodeService.saveEpisodes(episodes);

    // 发布事件通知有新视频下载
    List<String> savedEpisodeIds = episodes.stream()
        .map(Episode::getId)
        .collect(Collectors.toList());
    EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, savedEpisodeIds);
    eventPublisher.publishEvent(event);
    log.info("发布 EpisodesCreatedEvent 事件，包含 {} 个 episode ID。", savedEpisodeIds.size());

    return channel;
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
            c.getLastSyncTimestamp().isBefore(checkTime))
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
  public void refreshChannel(Channel channel) {
    log.info("正在同步频道: {}", channel.getName());

    // 1. 获取增量视频
    List<Episode> newEpisodes = youtubeHelper.fetchYoutubeChannelVideos(
        channel.getId(), channel.getLastSyncVideoId(), 5,
        channel.getContainKeywords(), channel.getExcludeKeywords(), channel.getMinimumDuration());

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
    Episode latestEpisode = newEpisodes.get(0);
    channel.setLastSyncVideoId(latestEpisode.getId());
    channel.setLastSyncTimestamp(LocalDateTime.now());
    channelMapper.updateById(channel);

    // 4. 复用已有的事件发布机制，触发异步下载
    List<String> newEpisodeIds = newEpisodes.stream().map(Episode::getId)
        .collect(Collectors.toList());
    EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, newEpisodeIds);
    eventPublisher.publishEvent(event);

    log.info("为频道 {} 的新节目发布了下载事件。", channel.getName());
  }

  public String getChannelRssFeedUrl(String channelHandler) {
    String apiKey = accountService.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException("Get API Key failed. Please check your account settings.");
    }
    return appBaseUrl + "/api/rss/" + channelHandler + ".xml?apikey=" + apiKey;
  }

  public Channel updateChannelConfig(String channelId,Channel configuration) {
    Channel existingChannel = channelMapper.selectById(channelId);
    if (existingChannel == null) {
      throw new BusinessException("Channel not found with id: " + channelId);
    }

    // 只更新允许修改的字段
    existingChannel.setContainKeywords(configuration.getContainKeywords());
    existingChannel.setExcludeKeywords(configuration.getExcludeKeywords());
    existingChannel.setMinimumDuration(configuration.getMinimumDuration());
    existingChannel.setMaximumEpisodes(configuration.getMaximumEpisodes());
    existingChannel.setInitialEpisodes(configuration.getInitialEpisodes());

    int result = channelMapper.updateById(existingChannel);
    if (result > 0) {
      log.info("频道 {} 配置更新成功", existingChannel.getName());
      return existingChannel;
    } else {
      log.error("频道 {} 配置更新失败", existingChannel.getName());
      throw new BusinessException("更新频道配置失败");
    }
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
      int count = episodeService.deleteEpisodesByChannelId(channelId);
      log.info("删除了 {} 条episode记录", count);
    } catch (Exception e) {
      log.error("删除episode记录时出错", e);
      throw new BusinessException("删除episode记录失败: " + e.getMessage());
    }
  }


}
