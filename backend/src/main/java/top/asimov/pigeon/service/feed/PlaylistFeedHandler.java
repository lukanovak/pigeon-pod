package top.asimov.pigeon.service.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.constant.FeedType;
import top.asimov.pigeon.model.Playlist;
import top.asimov.pigeon.service.PlaylistService;

@Component
public class PlaylistFeedHandler extends AbstractFeedHandler<Playlist> {

  private final PlaylistService playlistService;

  public PlaylistFeedHandler(PlaylistService playlistService, ObjectMapper objectMapper,
      MessageSource messageSource) {
    super(objectMapper, messageSource);
    this.playlistService = playlistService;
  }

  @Override
  public FeedType getType() {
    return FeedType.PLAYLIST;
  }

  @Override
  public List<Playlist> list() {
    return playlistService.selectPlaylistList();
  }

  @Override
  public Playlist detail(String id) {
    return playlistService.playlistDetail(id);
  }

  @Override
  public String getSubscribeUrl(String id) {
    return playlistService.getPlaylistRssFeedUrl(id);
  }

  @Override
  public Object updateConfig(String id, Map<String, Object> payload) {
    return playlistService.updatePlaylistConfig(id, convert(payload, Playlist.class));
  }

  @Override
  public Object fetch(Map<String, ?> payload) {
    return playlistService.fetchPlaylist(resolveSourceUrl(payload, "playlistUrl"));
  }

  @Override
  public Object preview(Map<String, Object> payload) {
    return playlistService.previewPlaylist(convert(payload, Playlist.class));
  }

  @Override
  public Object add(Map<String, Object> payload) {
    return playlistService.savePlaylist(convert(payload, Playlist.class));
  }

  @Override
  public void delete(String id) {
    playlistService.deletePlaylist(id);
  }
}
