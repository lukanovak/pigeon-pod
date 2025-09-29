package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import top.asimov.pigeon.constant.FeedSource;
import top.asimov.pigeon.constant.Youtube;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.event.DownloadTaskEvent;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadAction;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.ChannelMapper;
import top.asimov.pigeon.model.Channel;
import top.asimov.pigeon.model.ChannelPack;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.util.YoutubeHelper;
import java.util.HashMap;
import top.asimov.pigeon.util.YoutubeVideoHelper;

@Log4j2
@Service
public class ChannelService {

  private static final int DEFAULT_FETCH_NUM = 3;
  private static final int MAX_FETCH_NUM = 5;
  private static final int ASYNC_FETCH_NUM = 10;

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final ChannelMapper channelMapper;
  private final EpisodeService episodeService;
  private final ApplicationEventPublisher eventPublisher;
  private final YoutubeHelper youtubeHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;

  public ChannelService(ChannelMapper channelMapper, EpisodeService episodeService,
      ApplicationEventPublisher eventPublisher, YoutubeHelper youtubeHelper,
      YoutubeVideoHelper youtubeVideoHelper, AccountService accountService,
      MessageSource messageSource) {
    this.channelMapper = channelMapper;
    this.episodeService = episodeService;
    this.eventPublisher = eventPublisher;
    this.youtubeHelper = youtubeHelper;
    this.youtubeVideoHelper = youtubeVideoHelper;
    this.accountService = accountService;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    // 在依赖注入完成后，处理 appBaseUrl 值
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  /**
   * 获取所有频道列表，包含最后上传时间
   *
   * @return 频道列表
   */
  public List<Channel> selectChannelList() {
    return channelMapper.selectChannelsByLastUploadedAt();
  }

  /**
   * 获取频道详情
   *
   * @param id 频道ID
   * @return 频道对象
   */
  public Channel channelDetail(String id) {
    Channel channel = channelMapper.selectById(id);
    if (channel == null) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{id},
              LocaleContextHolder.getLocale()));
    }
    channel.setOriginalUrl(Youtube.CHANNEL_URL + channel.getId());
    return channel;
  }

  /**
   * 根据频道ID或handler查找频道
   *
   * @param channelIdentification 频道ID或handler
   * @return 频道对象，如果未找到则返回null
   */
  public Channel findChannelByIdentification(String channelIdentification) {
    // 先按ID查询
    Channel channel = channelMapper.selectById(channelIdentification);
    if (channel != null) {
      return channel;
    }
    // 再按handler查询
    LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(Channel::getHandler, channelIdentification);
    return channelMapper.selectOne(queryWrapper);
  }

  /**
   * 获取频道的RSS订阅链接
   *
   * @param channelId 频道ID
   * @return RSS订阅链接
   */
  public String getChannelRssFeedUrl(String channelId) {
    Channel channel = channelMapper.selectById(channelId);
    if (ObjectUtils.isEmpty(channel)) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{channelId},
              LocaleContextHolder.getLocale()));
    }
    String apiKey = accountService.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException(
          messageSource.getMessage("channel.api.key.failed", null,
              LocaleContextHolder.getLocale()));
    }
    String handler = channel.getHandler();
    if (StringUtils.hasText(handler)) {
      return appBaseUrl + "/api/rss/" + handler + ".xml?apikey=" + apiKey;
    }
    return appBaseUrl + "/api/rss/" + channelId + ".xml?apikey=" + apiKey;
  }

  /**
   * 更新频道的配置项
   *
   * @param channelId     频道ID
   * @param configuration 包含更新配置的Channel对象
   * @return 更新后的频道对象
   */
  @Transactional
  public HashMap<String, Object> updateChannelConfig(String channelId, Channel configuration) {
    boolean downloadHistory = false; // 是否下载历史视频
    int downloadNumber = 0; // 下载视频数量
    Channel existingChannel = channelMapper.selectById(channelId);
    if (existingChannel == null) {
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{channelId},
              LocaleContextHolder.getLocale()));
    }

    Integer oldInitialEpisodes =
        ObjectUtils.isEmpty(existingChannel.getInitialEpisodes()) ? DEFAULT_FETCH_NUM
            : existingChannel.getInitialEpisodes();
    Integer newInitialEpisodes = configuration.getInitialEpisodes();

    // 只更新允许修改的字段
    existingChannel.setContainKeywords(configuration.getContainKeywords());
    existingChannel.setExcludeKeywords(configuration.getExcludeKeywords());
    existingChannel.setMinimumDuration(configuration.getMinimumDuration());
    existingChannel.setMaximumEpisodes(configuration.getMaximumEpisodes());
    existingChannel.setInitialEpisodes(newInitialEpisodes);

    int result = channelMapper.updateById(existingChannel);
    if (result > 0) {
      // 如果新的历史下载视频数量大于旧的历史下载视频数量，则发布异步下载事件
      if (newInitialEpisodes != null && newInitialEpisodes > oldInitialEpisodes) {
        downloadHistory = true; // 下载历史视频
        downloadNumber = newInitialEpisodes - oldInitialEpisodes; // 下载视频数量
        // 发布异步下载历史视频事件
        DownloadTaskEvent event = new DownloadTaskEvent(
            this,
            DownloadTargetType.CHANNEL,
            DownloadAction.HISTORY,
            channelId,
            downloadNumber,
            existingChannel.getContainKeywords(),
            existingChannel.getExcludeKeywords(),
            existingChannel.getMinimumDuration());
        eventPublisher.publishEvent(event);

        log.info("已发布频道历史节目下载事件，频道: {}, 下载视频数量: {}", existingChannel.getTitle(),
            downloadNumber);
      }

      log.info("频道 {} 配置更新成功", existingChannel.getTitle());
      HashMap<String, Object> res = new HashMap<>();
      res.put("downloadHistory", downloadHistory);
      res.put("downloadNumber", downloadNumber);
      return res;
    } else {
      log.error("频道 {} 配置更新失败", existingChannel.getTitle());
      throw new BusinessException(
          messageSource.getMessage("channel.config.update.failed", null,
              LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 根据用户输入的链接，获取频道信息和预览视频列表
   *
   * @param channelUrl 包含用户输入的频道URL或ID等
   * @return 包含频道详情和预览视频列表的ChannelPack对象
   */
  public ChannelPack fetchChannel(String channelUrl) {
    if (ObjectUtils.isEmpty(channelUrl)) {
      throw new BusinessException(
          messageSource.getMessage("channel.source.empty", null, LocaleContextHolder.getLocale()));
    }

    // 获取频道信息
    com.google.api.services.youtube.model.Channel ytChannel;

    try {
      ytChannel = youtubeHelper.fetchYoutubeChannel(channelUrl);
    } catch (Exception e) {
      log.error("获取频道信息失败，输入: {}, 错误: {}", channelUrl, e.getMessage());
      throw new BusinessException(messageSource.getMessage("youtube.fetch.channel.failed",
          new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }

    String ytChannelId = ytChannel.getId();
    Channel fetchedChannel = Channel.builder()
        .id(ytChannelId)
        .title(ytChannel.getSnippet().getTitle())
        .coverUrl(ytChannel.getSnippet().getThumbnails().getHigh().getUrl())
        .description(ytChannel.getSnippet().getDescription())
        .subscribedAt(LocalDateTime.now())
        .source(FeedSource.YOUTUBE_CHANNEL.name()) // 目前只支持YouTube
        .originalUrl(channelUrl)
        .build();

    // 获取最近3个视频确认是目标频道
    List<Episode> episodes = youtubeVideoHelper.fetchYoutubeChannelVideos(ytChannelId,
        DEFAULT_FETCH_NUM);
    return ChannelPack.builder().channel(fetchedChannel).episodes(episodes).build();
  }

  /**
   * 预览频道的最新视频
   *
   * @param channel 包含频道ID和筛选条件的Channel对象
   * @return 预览的视频列表
   */
  public List<Episode> previewChannel(Channel channel) {
    String channelId = channel.getId();
    int fetchNum = DEFAULT_FETCH_NUM;
    String containKeywords = channel.getContainKeywords();
    String excludeKeywords = channel.getExcludeKeywords();
    if (StringUtils.hasText(containKeywords) || StringUtils.hasText(excludeKeywords)) {
      fetchNum = MAX_FETCH_NUM; // 有关键词时多拉取一些，方便确认过滤规则效果
    }
    return youtubeVideoHelper.fetchYoutubeChannelVideos(channelId, fetchNum,
        containKeywords, excludeKeywords, channel.getMinimumDuration());
  }

  /**
   * 保存频道并初始化下载最新的视频
   * 当initialEpisodes较大时（> ASYNC_FETCH_NUM），使用异步处理模式
   *
   * @param channel 要保存的频道信息
   * @return 包含频道信息和处理状态的Map对象
   */
  @Transactional
  public Map<String, Object> saveChannel(Channel channel) {
    Integer initialEpisodes = channel.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      initialEpisodes = DEFAULT_FETCH_NUM;
      channel.setInitialEpisodes(initialEpisodes);
    }

    boolean isAsyncMode = initialEpisodes > ASYNC_FETCH_NUM; // 超过10个视频时使用异步模式

    if (isAsyncMode) {
      return saveChannelAsync(channel);
    } else {
      return saveChannelSync(channel);
    }
  }

  /**
   * 异步模式保存频道：先保存频道基本信息，然后异步处理视频获取
   *
   * @param channel 要保存的频道信息
   * @return 包含频道信息和异步处理状态的Map对象
   */
  private Map<String, Object> saveChannelAsync(Channel channel) {
    String channelId = channel.getId();
    Integer initialEpisodes = channel.getInitialEpisodes();
    String containKeywords = channel.getContainKeywords();
    String excludeKeywords = channel.getExcludeKeywords();
    Integer minimumDuration = channel.getMinimumDuration();

    log.info("频道 {} 设置的初始视频数量较多({}), 启用异步处理模式", channel.getTitle(), initialEpisodes);

    // 先保存频道基本信息
    channelMapper.insert(channel);

    // 发布异步下载事件
    DownloadTaskEvent event = new DownloadTaskEvent(
        this,
        DownloadTargetType.CHANNEL,
        DownloadAction.INIT,
        channelId,
        initialEpisodes,
        containKeywords,
        excludeKeywords,
        minimumDuration);
    eventPublisher.publishEvent(event);

    log.info("已发布频道异步下载事件，频道: {}, 初始视频数量: {}", channel.getTitle(), initialEpisodes);

    // 返回异步处理状态
    Map<String, Object> result = new HashMap<>();
    result.put("channel", channel);
    result.put("isAsync", true);
    result.put("message", messageSource.getMessage("channel.async.processing",
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale()));
    return result;
  }

  /**
   * 同步模式保存频道：直接处理少量视频
   *
   * @param channel 要保存的频道信息
   * @return 包含频道信息和同步处理状态的Map对象
   */
  private Map<String, Object> saveChannelSync(Channel channel) {
    String channelId = channel.getId();
    Integer initialEpisodes = channel.getInitialEpisodes();
    String containKeywords = channel.getContainKeywords();
    String excludeKeywords = channel.getExcludeKeywords();
    Integer minimumDuration = channel.getMinimumDuration();

    log.info("频道 {} 设置的初始视频数量较少({}), 使用同步处理模式", channel.getTitle(), initialEpisodes);

    List<Episode> episodes = youtubeVideoHelper.fetchYoutubeChannelVideos(channelId,
        initialEpisodes,
        containKeywords, excludeKeywords, minimumDuration);

    if (!episodes.isEmpty()) {
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
    } else {
      // 没有找到视频，只保存频道信息
      channelMapper.insert(channel);
    }

    // 返回同步处理结果
    Map<String, Object> result = new HashMap<>();
    result.put("channel", channel);
    result.put("isAsync", false);
    result.put("message", messageSource.getMessage("channel.sync.completed",
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale()));
    return result;
  }

  /**
   * 查找所有需要同步的频道
   *
   * @param checkTime 检查时间点，所有 lastSyncTimestamp 早于该时间点的频道都需要同步
   * @return 需要同步的频道列表
   */
  public List<Channel> findDueForSync(LocalDateTime checkTime) {
    List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<>());
    return channels.stream()
        .filter(c -> c.getLastSyncTimestamp() == null ||
            c.getLastSyncTimestamp().isBefore(checkTime))
        .collect(Collectors.toList());
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
      throw new BusinessException(
          messageSource.getMessage("channel.not.found", new Object[]{channelId},
              LocaleContextHolder.getLocale()));
    }

    // 2. 查询该频道下所有的episodes
    List<Episode> episodes = episodeService.findByChannelId(channelId);
    log.info("频道 {} 下有 {} 个episodes需要删除", channel.getTitle(), episodes.size());

    // 3. 删除所有episodes对应的音频文件
    deleteAudioFiles(episodes);

    // 4. 从数据库中删除所有episodes记录
    deleteEpisodeRecords(channelId);

    // 5. 删除频道记录
    int result = channelMapper.deleteById(channelId);
    if (result > 0) {
      log.info("频道 {} 删除成功", channel.getTitle());
    } else {
      log.error("频道 {} 删除失败", channel.getTitle());
      throw new BusinessException(
          messageSource.getMessage("channel.delete.failed", null, LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 同步频道，检查是否有新视频并处理
   *
   * @param channel 要同步的频道对象
   */
  @Transactional
  public void refreshChannel(Channel channel) {
    log.info("正在同步频道: {}", channel.getTitle());

    // 1. 获取增量视频，默认一个小时检查一次，默认一个小时最多检查 5 个视频
    List<Episode> newEpisodes = youtubeVideoHelper.fetchYoutubeChannelVideos(
        channel.getId(), MAX_FETCH_NUM, channel.getLastSyncVideoId(),
        channel.getContainKeywords(), channel.getExcludeKeywords(), channel.getMinimumDuration());

    if (newEpisodes.isEmpty()) {
      log.info("频道 {} 没有新内容。", channel.getTitle());
      // 即使没有新内容，也更新同步时间戳，避免频繁检查
      channel.setLastSyncTimestamp(LocalDateTime.now());
      channelMapper.updateById(channel);
      return;
    }

    log.info("频道 {} 发现 {} 个新节目。", channel.getTitle(), newEpisodes.size());

    // 2. 保存新节目的元数据
    episodeService.saveEpisodes(newEpisodes);

    // 3. 更新频道的检查点 (lastSyncedVideoId 和 lastSyncTimestamp)
    Episode latestEpisode = newEpisodes.get(0);
    for (Episode episode : newEpisodes) {
      if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
        latestEpisode = episode;
      }
    }
    channel.setLastSyncVideoId(latestEpisode.getId());
    channel.setLastSyncTimestamp(LocalDateTime.now());
    channelMapper.updateById(channel);

    // 4. 复用已有的事件发布机制，触发异步下载
    List<String> newEpisodeIds = newEpisodes.stream().map(Episode::getId)
        .collect(Collectors.toList());
    EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, newEpisodeIds);
    eventPublisher.publishEvent(event);

    log.info("为频道 {} 的新节目发布了下载事件。", channel.getTitle());
  }

  /**
   * 获取并保存初始化的视频
   *
   * @param channelId       频道ID
   * @param initialEpisodes 要获取的初始视频数量
   * @param containKeywords 包含关键词
   * @param excludeKeywords 排除关键词
   * @param minimumDuration 最小时长
   */
  @Transactional
  public void processChannelInitializationAsync(String channelId, Integer initialEpisodes,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    log.info("开始异步处理频道初始化，频道ID: {}, 初始视频数量: {}", channelId, initialEpisodes);

    try {
      // 获取频道的初始视频
      List<Episode> episodes = youtubeVideoHelper.fetchYoutubeChannelVideos(
          channelId, initialEpisodes, containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("频道 {} 没有找到任何视频。", channelId);
        return;
      }

      // 找到最新的视频
      Episode latestEpisode = episodes.get(0);
      for (Episode episode : episodes) {
        if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
          latestEpisode = episode;
        }
      }

      // 更新频道的同步信息
      Channel channel = channelMapper.selectById(channelId);
      if (channel != null) {
        channel.setLastSyncVideoId(latestEpisode.getId());
        channel.setLastSyncTimestamp(LocalDateTime.now());
        channelMapper.updateById(channel);
      }

      // 保存视频信息
      episodeService.saveEpisodes(episodes);

      // 发布事件通知下载
      List<String> savedEpisodeIds = episodes.stream()
          .map(Episode::getId)
          .collect(Collectors.toList());
      EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, savedEpisodeIds);
      eventPublisher.publishEvent(event);

      log.info("频道 {} 异步初始化完成，保存了 {} 个视频", channelId, episodes.size());

    } catch (Exception e) {
      log.error("频道 {} 异步初始化失败: {}", channelId, e.getMessage(), e);
    }
  }

  @Transactional
  public void processChannelDownloadHistoryAsync(String channelId, Integer episodesToDownload,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    Episode earliestEpisode = episodeService.findEarliestEpisode(channelId);
    LocalDateTime earliestTime = earliestEpisode.getPublishedAt().minusSeconds(1);
    log.info("频道 {} 开始重新下载历史节目，准备下载 {} 个视频", channelId, episodesToDownload);
    try {
      // 获取频道指定时间之前指定数量的历史视频
      List<Episode> episodes = youtubeVideoHelper.fetchYoutubeChannelVideosBeforeDate(channelId,
          episodesToDownload, earliestTime,
          containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("频道 {} 没有找到任何历史视频。", channelId);
        return;
      }

      // 保存视频信息
      episodeService.saveEpisodes(episodes);

      // 发布事件通知下载
      List<String> savedEpisodeIds = episodes.stream()
          .map(Episode::getId)
          .collect(Collectors.toList());
      EpisodesCreatedEvent event = new EpisodesCreatedEvent(this, savedEpisodeIds);
      eventPublisher.publishEvent(event);

      log.info("频道 {} 开始重新下载历史节目，准备下载 {} 个视频", channelId, episodes.size());
    } catch (Exception e) {
      log.error("频道 {} 重新下载历史节目失败: {}", channelId, e.getMessage(), e);
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
      throw new BusinessException(messageSource.getMessage("episode.delete.records.failed",
          new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }
  }

  /**
   * 删除episodes对应的音频文件，并在删除完所有文件后清理空的频道文件夹
   */
  private void deleteAudioFiles(List<Episode> episodes) {
    java.util.Set<String> channelDirectories = new java.util.HashSet<>();

    // 删除所有音频文件，同时收集频道目录路径
    for (Episode episode : episodes) {
      String audioFilePath = episode.getAudioFilePath();
      if (!ObjectUtils.isEmpty(audioFilePath)) {
        try {
          java.io.File audioFile = new java.io.File(audioFilePath);
          if (audioFile.exists()) {
            boolean deleted = audioFile.delete();
            if (deleted) {
              log.info("音频文件删除成功: {}", audioFilePath);
              // 收集父目录路径（频道文件夹）
              java.io.File parentDir = audioFile.getParentFile();
              if (parentDir != null) {
                channelDirectories.add(parentDir.getAbsolutePath());
              }
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

    // 检查并删除空的频道文件夹
    for (String channelDirPath : channelDirectories) {
      try {
        java.io.File channelDir = new java.io.File(channelDirPath);
        if (channelDir.exists() && channelDir.isDirectory()) {
          // 检查目录是否为空
          java.io.File[] files = channelDir.listFiles();
          if (files != null && files.length == 0) {
            boolean deleted = channelDir.delete();
            if (deleted) {
              log.info("空的频道文件夹删除成功: {}", channelDirPath);
            } else {
              log.warn("空的频道文件夹删除失败: {}", channelDirPath);
            }
          } else {
            log.info("频道文件夹不为空，保留: {} (包含 {} 个文件/子目录)",
                channelDirPath, files != null ? files.length : 0);
          }
        }
      } catch (Exception e) {
        log.error("检查或删除频道文件夹时出错: {}", channelDirPath, e);
      }
    }
  }

}
