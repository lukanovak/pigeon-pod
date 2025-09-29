package top.asimov.pigeon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import top.asimov.pigeon.constant.PlaylistSource;
import top.asimov.pigeon.constant.Youtube;
import top.asimov.pigeon.event.DownloadTaskEvent;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadAction;
import top.asimov.pigeon.event.DownloadTaskEvent.DownloadTargetType;
import top.asimov.pigeon.event.EpisodesCreatedEvent;
import top.asimov.pigeon.exception.BusinessException;
import top.asimov.pigeon.mapper.PlaylistEpisodeMapper;
import top.asimov.pigeon.mapper.PlaylistMapper;
import top.asimov.pigeon.model.Episode;
import top.asimov.pigeon.model.Playlist;
import top.asimov.pigeon.model.PlaylistEpisode;
import top.asimov.pigeon.model.PlaylistPack;
import top.asimov.pigeon.util.YoutubeHelper;
import top.asimov.pigeon.util.YoutubeVideoHelper;

@Log4j2
@Service
public class PlaylistService {

  private static final int DEFAULT_FETCH_NUM = 3;
  private static final int MAX_FETCH_NUM = 5;
  private static final int ASYNC_FETCH_NUM = 10;

  @Value("${pigeon.base-url}")
  private String appBaseUrl;

  private final PlaylistMapper playlistMapper;
  private final PlaylistEpisodeMapper playlistEpisodeMapper;
  private final EpisodeService episodeService;
  private final ApplicationEventPublisher eventPublisher;
  private final YoutubeHelper youtubeHelper;
  private final YoutubeVideoHelper youtubeVideoHelper;
  private final AccountService accountService;
  private final MessageSource messageSource;

  public PlaylistService(PlaylistMapper playlistMapper, PlaylistEpisodeMapper playlistEpisodeMapper,
      EpisodeService episodeService, ApplicationEventPublisher eventPublisher,
      YoutubeHelper youtubeHelper, YoutubeVideoHelper youtubeVideoHelper,
      AccountService accountService, MessageSource messageSource) {
    this.playlistMapper = playlistMapper;
    this.playlistEpisodeMapper = playlistEpisodeMapper;
    this.episodeService = episodeService;
    this.eventPublisher = eventPublisher;
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
    playlist.setPlaylistUrl(Youtube.PLAYLIST_URL + playlist.getId());
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
  public HashMap<String, Object> updatePlaylistConfig(String playlistId, Playlist configuration) {
    boolean downloadHistory = false;
    int downloadNumber = 0;
    Playlist existingPlaylist = playlistMapper.selectById(playlistId);
    if (existingPlaylist == null) {
      throw new BusinessException(
          messageSource.getMessage("playlist.not.found", new Object[]{playlistId},
              LocaleContextHolder.getLocale()));
    }

    Integer oldInitialEpisodes =
        ObjectUtils.isEmpty(existingPlaylist.getInitialEpisodes()) ? DEFAULT_FETCH_NUM
            : existingPlaylist.getInitialEpisodes();
    Integer newInitialEpisodes = configuration.getInitialEpisodes();

    existingPlaylist.setContainKeywords(configuration.getContainKeywords());
    existingPlaylist.setExcludeKeywords(configuration.getExcludeKeywords());
    existingPlaylist.setMinimumDuration(configuration.getMinimumDuration());
    existingPlaylist.setMaximumEpisodes(configuration.getMaximumEpisodes());
    existingPlaylist.setInitialEpisodes(newInitialEpisodes);

    int result = playlistMapper.updateById(existingPlaylist);
    if (result > 0) {
      if (newInitialEpisodes != null && newInitialEpisodes > oldInitialEpisodes) {
        downloadHistory = true;
        downloadNumber = newInitialEpisodes - oldInitialEpisodes;
        DownloadTaskEvent event = new DownloadTaskEvent(
            this,
            DownloadTargetType.PLAYLIST,
            DownloadAction.HISTORY,
            playlistId,
            downloadNumber,
            existingPlaylist.getContainKeywords(),
            existingPlaylist.getExcludeKeywords(),
            existingPlaylist.getMinimumDuration());
        eventPublisher.publishEvent(event);

        log.info("已发布播放列表历史节目下载事件，播放列表: {}, 下载视频数量: {}", existingPlaylist.getTitle(),
            downloadNumber);
      }

      log.info("播放列表 {} 配置更新成功", existingPlaylist.getTitle());
      HashMap<String, Object> res = new HashMap<>();
      res.put("downloadHistory", downloadHistory);
      res.put("downloadNumber", downloadNumber);
      return res;
    } else {
      log.error("播放列表 {} 配置更新失败", existingPlaylist.getTitle());
      throw new BusinessException(
          messageSource.getMessage("playlist.config.update.failed", null,
              LocaleContextHolder.getLocale()));
    }
  }

  public PlaylistPack fetchPlaylist(String playlistUrl) {
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
        .playlistSource(PlaylistSource.YOUTUBE.name())
        .build();

    List<Episode> episodes = youtubeVideoHelper.fetchPlaylistVideos(ytPlaylistId,
        DEFAULT_FETCH_NUM);
    return PlaylistPack.builder().playlist(fetchedPlaylist).episodes(episodes).build();
  }

  public List<Episode> previewPlaylist(Playlist playlist) {
    String playlistId = playlist.getId();
    int fetchNum = DEFAULT_FETCH_NUM;
    String containKeywords = playlist.getContainKeywords();
    String excludeKeywords = playlist.getExcludeKeywords();
    if (StringUtils.hasText(containKeywords) || StringUtils.hasText(excludeKeywords)) {
      fetchNum = MAX_FETCH_NUM;
    }
    return youtubeVideoHelper.fetchPlaylistVideos(playlistId, fetchNum,
        containKeywords, excludeKeywords, playlist.getMinimumDuration());
  }

  @Transactional
  public Map<String, Object> savePlaylist(Playlist playlist) {
    Integer initialEpisodes = playlist.getInitialEpisodes();
    if (initialEpisodes == null || initialEpisodes <= 0) {
      initialEpisodes = DEFAULT_FETCH_NUM;
      playlist.setInitialEpisodes(initialEpisodes);
    }

    boolean isAsyncMode = initialEpisodes > ASYNC_FETCH_NUM;

    if (isAsyncMode) {
      return savePlaylistAsync(playlist);
    } else {
      return savePlaylistSync(playlist);
    }
  }

  private Map<String, Object> savePlaylistAsync(Playlist playlist) {
    String playlistId = playlist.getId();
    Integer initialEpisodes = playlist.getInitialEpisodes();
    String containKeywords = playlist.getContainKeywords();
    String excludeKeywords = playlist.getExcludeKeywords();
    Integer minimumDuration = playlist.getMinimumDuration();

    log.info("播放列表 {} 设置的初始视频数量较多({}), 启用异步处理模式", playlist.getTitle(),
        initialEpisodes);

    playlistMapper.insert(playlist);

    DownloadTaskEvent event = new DownloadTaskEvent(
        this,
        DownloadTargetType.PLAYLIST,
        DownloadAction.INIT,
        playlistId,
        initialEpisodes,
        containKeywords,
        excludeKeywords,
        minimumDuration);
    eventPublisher.publishEvent(event);

    log.info("已发布播放列表异步下载事件，播放列表: {}, 初始视频数量: {}", playlist.getTitle(),
        initialEpisodes);

    Map<String, Object> result = new HashMap<>();
    result.put("playlist", playlist);
    result.put("isAsync", true);
    result.put("message", messageSource.getMessage("playlist.async.processing",
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale()));
    return result;
  }

  private Map<String, Object> savePlaylistSync(Playlist playlist) {
    String playlistId = playlist.getId();
    Integer initialEpisodes = playlist.getInitialEpisodes();
    String containKeywords = playlist.getContainKeywords();
    String excludeKeywords = playlist.getExcludeKeywords();
    Integer minimumDuration = playlist.getMinimumDuration();

    log.info("播放列表 {} 设置的初始视频数量较少({}), 使用同步处理模式", playlist.getTitle(),
        initialEpisodes);

    List<Episode> episodes = youtubeVideoHelper.fetchPlaylistVideos(playlistId,
        initialEpisodes,
        containKeywords, excludeKeywords, minimumDuration);

    if (!episodes.isEmpty()) {
      Episode latestEpisode = episodes.get(0);
      for (Episode episode : episodes) {
        if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
          latestEpisode = episode;
        }
      }

      playlist.setLastSyncVideoId(latestEpisode.getId());
      playlist.setLastSyncTimestamp(LocalDateTime.now());
      playlistMapper.insert(playlist);

      List<Episode> episodesForInsert = new ArrayList<>(episodes);
      episodeService.saveEpisodes(episodesForInsert);
      upsertPlaylistEpisodes(playlistId, episodes);

      List<String> savedEpisodeIds = episodesForInsert.stream()
          .map(Episode::getId)
          .collect(Collectors.toList());
      if (!savedEpisodeIds.isEmpty()) {
        eventPublisher.publishEvent(new EpisodesCreatedEvent(this, savedEpisodeIds));
        log.info("发布 EpisodesCreatedEvent 事件，包含 {} 个 episode ID。", savedEpisodeIds.size());
      }
    } else {
      playlistMapper.insert(playlist);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("playlist", playlist);
    result.put("isAsync", false);
    result.put("message", messageSource.getMessage("playlist.sync.completed",
        new Object[]{initialEpisodes}, LocaleContextHolder.getLocale()));
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

    List<Episode> newEpisodes = youtubeVideoHelper.fetchPlaylistVideos(
        playlist.getId(), MAX_FETCH_NUM, playlist.getLastSyncVideoId(),
        playlist.getContainKeywords(), playlist.getExcludeKeywords(), playlist.getMinimumDuration());

    if (newEpisodes.isEmpty()) {
      log.info("播放列表 {} 没有新内容。", playlist.getTitle());
      playlist.setLastSyncTimestamp(LocalDateTime.now());
      playlistMapper.updateById(playlist);
      return;
    }

    log.info("播放列表 {} 发现 {} 个新节目。", playlist.getTitle(), newEpisodes.size());

    List<Episode> episodesForInsert = new ArrayList<>(newEpisodes);
    episodeService.saveEpisodes(episodesForInsert);
    upsertPlaylistEpisodes(playlist.getId(), newEpisodes);

    Episode latestEpisode = newEpisodes.get(0);
    for (Episode episode : newEpisodes) {
      if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
        latestEpisode = episode;
      }
    }
    playlist.setLastSyncVideoId(latestEpisode.getId());
    playlist.setLastSyncTimestamp(LocalDateTime.now());
    playlistMapper.updateById(playlist);

    List<String> newEpisodeIds = episodesForInsert.stream().map(Episode::getId)
        .collect(Collectors.toList());
    if (!newEpisodeIds.isEmpty()) {
      eventPublisher.publishEvent(new EpisodesCreatedEvent(this, newEpisodeIds));
    }

    log.info("为播放列表 {} 的新节目发布了下载事件。", playlist.getTitle());
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

      Episode latestEpisode = episodes.get(0);
      for (Episode episode : episodes) {
        if (latestEpisode.getPublishedAt().isBefore(episode.getPublishedAt())) {
          latestEpisode = episode;
        }
      }

      Playlist playlist = playlistMapper.selectById(playlistId);
      if (playlist != null) {
        playlist.setLastSyncVideoId(latestEpisode.getId());
        playlist.setLastSyncTimestamp(LocalDateTime.now());
        playlistMapper.updateById(playlist);
      }

      List<Episode> episodesForInsert = new ArrayList<>(episodes);
      episodeService.saveEpisodes(episodesForInsert);
      upsertPlaylistEpisodes(playlistId, episodes);

      List<String> savedEpisodeIds = episodesForInsert.stream()
          .map(Episode::getId)
          .collect(Collectors.toList());
      if (!savedEpisodeIds.isEmpty()) {
        eventPublisher.publishEvent(new EpisodesCreatedEvent(this, savedEpisodeIds));
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

      List<Episode> episodesForInsert = new ArrayList<>(episodes);
      episodeService.saveEpisodes(episodesForInsert);
      upsertPlaylistEpisodes(playlistId, episodes);

      List<String> savedEpisodeIds = episodesForInsert.stream()
          .map(Episode::getId)
          .collect(Collectors.toList());
      if (!savedEpisodeIds.isEmpty()) {
        eventPublisher.publishEvent(new EpisodesCreatedEvent(this, savedEpisodeIds));
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
}
