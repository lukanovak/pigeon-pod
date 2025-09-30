package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.FeedConfigUpdateResult;
import top.asimov.pigeon.model.FeedPack;
import top.asimov.pigeon.model.FeedSaveResult;
import top.asimov.pigeon.model.Playlist;
import top.asimov.pigeon.model.PlaylistEpisode;
import top.asimov.pigeon.util.FeedEpisodeUtils;
import top.asimov.pigeon.util.YoutubeHelper;
import top.asimov.pigeon.util.YoutubeVideoHelper;

@Log4j2
@Service
public class PlaylistService extends AbstractFeedService<Playlist> {

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final YoutubeHelper youtubeHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;

  public PlaylistService(PlaylistMapper playlistMapper, PlaylistEpisodeMapper playlistEpisodeMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubeVideoHelper youtubeVideoHelper,
      AccountService accountService, MessageSource messageSource) {
    super(episodeService, eventPublisher, messageSource);
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.youtubeHelper = youtubeHelper;
    this.youtubeVideoHelper = youtubeVideoHelper;
    this.accountService = accountService;
    this.messageSource = messageSource;
  }

  @PostConstruct
  private void init() {
    if (appBaseUrl != null && appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
      log.info("已移除 appBaseUrl 末尾的斜杠，处理后的值为: {}", appBaseUrl);
    }
  }

  public List<Playlist> selectPlaylistList() {
    return playlistMapper.selectPlaylistsByLastPublishedAt();
  }

  public Playlist playlistDetail(String id) {
    Playlist playlist = playlistMapper.selectById(id);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{id},
              LocaleContextHolder.getLocale()));
    }
    playlist.setOriginalUrl(Youtube.PLAYLIST_URL + playlist.getId());
    return playlist;
  }

  public String getPlaylistRssFeedUrl(String playlistId) {
    Playlist playlist = playlistMapper.selectById(playlistId);
    if (ObjectUtils.isEmpty(playlist)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }
    String apiKey = accountService.getApiKey();
    if (ObjectUtils.isEmpty(apiKey)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.api.key.failed", null,
              LocaleContextHolder.getLocale()));
    }
    return appBaseUrl + "/api/rss/playlist/" + playlistId + ".xml?apikey=" + apiKey;
  }

  @Transactional
  public FeedConfigUpdateResult updatePlaylistConfig(String playlistId, Playlist configuration) {
    FeedConfigUpdateResult result = updateFeedConfig(playlistId, configuration);
    log.info("播放列表 {} 配置更新成功", playlistId);
    return result;
  }

  public FeedPack<Playlist> fetchPlaylist(String playlistUrl) {
    if (ObjectUtils.isEmpty(playlistUrl)) {
      throw new BusinessException(
          messageSource.getMessage("playlist.source.empty", null,
              LocaleContextHolder.getLocale()));
    }

    com.google.api.services.youtube.model.Playlist ytPlaylist;

    try {
      ytPlaylist = youtubeHelper.fetchYoutubePlaylist(playlistUrl);
    } catch (Exception e) {
      log.error("获取播放列表信息失败，输入: {}, 错误: {}", playlistUrl, e.getMessage());
      throw new BusinessException(messageSource.getMessage("youtube.fetch.playlist.failed",
          new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
    }

    String ytPlaylistId = ytPlaylist.getId();
    Playlist fetchedPlaylist = Playlist.builder()
        .id(ytPlaylistId)
        .title(ytPlaylist.getSnippet().getTitle())
        .ownerId(ytPlaylist.getSnippet().getChannelId())
        .coverUrl(ytPlaylist.getSnippet().getThumbnails().getHigh().getUrl())
        .description(ytPlaylist.getSnippet().getDescription())
        .subscribedAt(LocalDateTime.now())
        .source(FeedSource.YOUTUBE.name())
        .originalUrl(playlistUrl)
        .build();

    List<Episode> episodes = youtubeVideoHelper.fetchPlaylistVideos(ytPlaylistId,
        DEFAULT_FETCH_NUM);
    return FeedPack.<Playlist>builder().feed(fetchedPlaylist).episodes(episodes).build();
  }

  public FeedPack<Playlist> previewPlaylist(Playlist playlist) {
    return previewFeed(playlist);
  }

  @Transactional
  public FeedSaveResult<Playlist> savePlaylist(Playlist playlist) {
    FeedSaveResult<Playlist> result = saveFeed(playlist);
    if (result.isAsync()) {
      log.info("播放列表 {} 设置的初始视频数量较多({}), 启用异步处理模式", playlist.getTitle(),
          playlist.getInitialEpisodes());
    } else {
      log.info("播放列表 {} 设置的初始视频数量较少({}), 使用同步处理模式", playlist.getTitle(),
          playlist.getInitialEpisodes());
    }
    return result;
  }

  public List<Playlist> findDueForSync(LocalDateTime checkTime) {
    List<Playlist> playlists = playlistMapper.selectList(new LambdaQueryWrapper<>());
    return playlists.stream()
        .filter(p -> p.getLastSyncTimestamp() == null ||
            p.getLastSyncTimestamp().isBefore(checkTime))
        .collect(Collectors.toList());
  }

  @Transactional
  public void deletePlaylist(String playlistId) {
    log.info("开始删除播放列表: {}", playlistId);

    Playlist playlist = playlistMapper.selectById(playlistId);
    if (playlist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }

    playlistEpisodeMapper.deleteByPlaylistId(playlistId);

    int result = playlistMapper.deleteById(playlistId);
    if (result > 0) {
      log.info("播放列表 {} 删除成功", playlist.getTitle());
    } else {
      log.error("播放列表 {} 删除失败", playlist.getTitle());
      throw new BusinessException(
          messageSource.getMessage("playlist.delete.failed", null,
              LocaleContextHolder.getLocale()));
    }
  }

  @Transactional
  public void refreshPlaylist(Playlist playlist) {
    log.info("正在同步播放列表: {}", playlist.getTitle());
    refreshFeed(playlist);
  }

  @Transactional
  public void processPlaylistInitializationAsync(String playlistId, Integer initialEpisodes,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    log.info("开始异步处理播放列表初始化，播放列表ID: {}, 初始视频数量: {}", playlistId, initialEpisodes);

    try {
      List<Episode> episodes = youtubeVideoHelper.fetchPlaylistVideos(
          playlistId, initialEpisodes, containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("播放列表 {} 没有找到任何视频。", playlistId);
        return;
      }

      Playlist playlist = playlistMapper.selectById(playlistId);
      FeedEpisodeUtils.findLatestEpisode(episodes).ifPresent(latest -> {
        if (playlist != null) {
          playlist.setLastSyncVideoId(latest.getId());
          playlist.setLastSyncTimestamp(LocalDateTime.now());
          playlistMapper.updateById(playlist);
        }
      });

      if (playlist != null) {
        persistEpisodesAndPublish(playlist, episodes);
      } else {
        episodeService().saveEpisodes(prepareEpisodesForPersistence(episodes));
        FeedEpisodeUtils.publishEpisodesCreated(eventPublisher(), this, episodes);
        upsertPlaylistEpisodes(playlistId, episodes);
      }

      log.info("播放列表 {} 异步初始化完成，保存了 {} 个视频", playlistId, episodes.size());

    } catch (Exception e) {
      log.error("播放列表 {} 异步初始化失败: {}", playlistId, e.getMessage(), e);
    }
  }

  @Transactional
  public void processPlaylistDownloadHistoryAsync(String playlistId, Integer episodesToDownload,
      String containKeywords, String excludeKeywords, Integer minimumDuration) {
    PlaylistEpisode earliestEpisode = playlistEpisodeMapper.selectEarliestByPlaylistId(playlistId);
    if (earliestEpisode == null || earliestEpisode.getPublishedAt() == null) {
      log.warn("播放列表 {} 尚无历史节目数据，跳过历史下载。", playlistId);
      return;
    }
    LocalDateTime earliestTime = earliestEpisode.getPublishedAt().minusSeconds(1);
    log.info("播放列表 {} 开始重新下载历史节目，准备下载 {} 个视频", playlistId, episodesToDownload);
    try {
      List<Episode> episodes = youtubeVideoHelper.fetchPlaylistVideosBeforeDate(playlistId,
          episodesToDownload, earliestTime,
          containKeywords, excludeKeywords, minimumDuration);

      if (episodes.isEmpty()) {
        log.info("播放列表 {} 没有找到任何历史视频。", playlistId);
        return;
      }

      Playlist playlist = playlistMapper.selectById(playlistId);
      if (playlist != null) {
        persistEpisodesAndPublish(playlist, episodes);
      } else {
        episodeService().saveEpisodes(prepareEpisodesForPersistence(episodes));
        FeedEpisodeUtils.publishEpisodesCreated(eventPublisher(), this, episodes);
        upsertPlaylistEpisodes(playlistId, episodes);
      }

      log.info("播放列表 {} 历史节目处理完成，新增 {} 个视频", playlistId, episodes.size());
    } catch (Exception e) {
      log.error("播放列表 {} 重新下载历史节目失败: {}", playlistId, e.getMessage(), e);
    }
  }

  private void upsertPlaylistEpisodes(String playlistId, List<Episode> episodes) {
    for (Episode episode : episodes) {
      int count = playlistEpisodeMapper.countByPlaylistAndEpisode(playlistId, episode.getId());
      int affected;
      if (count > 0) {
        affected = playlistEpisodeMapper.updateMapping(playlistId, episode.getId(),
            episode.getPublishedAt());
      } else {
        affected = playlistEpisodeMapper.insertMapping(playlistId, episode.getId(),
            episode.getPublishedAt());
      }
      if (affected <= 0) {
        log.warn("更新播放列表 {} 与节目 {} 的关联失败", playlistId, episode.getId());
      }
    }
  }

  @Override
  protected Optional<Playlist> findFeedById(String feedId) {
    return Optional.ofNullable(playlistMapper.selectById(feedId));
  }

  @Override
  protected int updateFeed(Playlist feed) {
    return playlistMapper.updateById(feed);
  }

  @Override
  protected void insertFeed(Playlist feed) {
    playlistMapper.insert(feed);
  }

  @Override
  protected DownloadTargetType downloadTargetType() {
    return DownloadTargetType.PLAYLIST;
  }

  @Override
  protected List<Episode> fetchEpisodes(Playlist feed, int fetchNum) {
    return youtubeVideoHelper.fetchPlaylistVideos(feed.getId(), fetchNum,
        feed.getContainKeywords(), feed.getExcludeKeywords(), feed.getMinimumDuration());
  }

  @Override
  protected List<Episode> fetchIncrementalEpisodes(Playlist feed) {
    return youtubeVideoHelper.fetchPlaylistVideos(feed.getId(), MAX_FETCH_NUM,
        feed.getLastSyncVideoId(), feed.getContainKeywords(), feed.getExcludeKeywords(),
        feed.getMinimumDuration());
  }

  @Override
  protected List<Episode> prepareEpisodesForPersistence(List<Episode> episodes) {
    return new ArrayList<>(episodes);
  }

  @Override
  protected void afterEpisodesPersisted(Playlist feed, List<Episode> episodes) {
    if (feed != null) {
      upsertPlaylistEpisodes(feed.getId(), episodes);
    }
  }

  @Override
  protected org.apache.logging.log4j.Logger logger() {
    return log;
  }
}
